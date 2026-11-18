import { createHash, randomBytes } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import {
  GeoPoint,
  Query,
  Timestamp,
  getFirestore,
} from "firebase-admin/firestore";
import type { DocumentData, QueryDocumentSnapshot } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions } from "firebase-functions/v2";

initializeApp();
setGlobalOptions({ region: "southamerica-east1", maxInstances: 10 });

const db = getFirestore();
const CONFIRMATION_WINDOW_MS = 24 * 60 * 60 * 1000;
const ACTIVE_REGISTRATION_STATES = new Set([
  "CONFIRMADA", "LISTA_ESPERA", "AGUARDANDO_CONFIRMACAO", "PRESENTE",
]);

type RegistrationState =
  | "CONFIRMADA"
  | "LISTA_ESPERA"
  | "AGUARDANDO_CONFIRMACAO"
  | "CANCELADA"
  | "PRESENTE";

function requireUid(authUid: string | undefined): string {
  if (!authUid) throw new HttpsError("unauthenticated", "Entre na sua conta para continuar.");
  return authUid;
}

async function requireRole(uid: string, expected: "student" | "organizer") {
  const profile = await db.collection("users").doc(uid).get();
  if (!profile.exists || profile.get("role") !== expected) {
    throw new HttpsError("permission-denied", "Seu perfil não pode executar esta operação.");
  }
  return profile;
}

function requireEventId(value: unknown): string {
  if (typeof value !== "string" || value.trim().length < 1 || value.length > 128) {
    throw new HttpsError("invalid-argument", "Evento inválido.");
  }
  return value.trim();
}

function makeQrToken() {
  const token = randomBytes(32).toString("base64url");
  return { token, hash: createHash("sha256").update(token).digest("hex") };
}

function registrationRef(eventId: string, uid: string) {
  return db.collection("events").doc(eventId).collection("registrations").doc(uid);
}

function userRegistrationRef(uid: string, eventId: string) {
  return db.collection("users").doc(uid).collection("registrations").doc(eventId);
}

function registrationMirror(
  eventId: string,
  eventTitle: string,
  uid: string,
  data: Record<string, any>,
) {
  return {
    eventId,
    eventTitle,
    userId: uid,
    recordType: "user",
    status: data.status,
    registeredAt: data.registeredAt,
    confirmationExpiresAt: data.confirmationExpiresAt ?? null,
    checkedInAt: data.checkedInAt ?? null,
    updatedAt: data.updatedAt,
  };
}

function activeState(status: unknown): status is RegistrationState {
  return typeof status === "string" && ACTIVE_REGISTRATION_STATES.has(status);
}

function eventCapacity(data: DocumentData): number {
  const capacity = data.capacity;
  if (!Number.isSafeInteger(capacity) || capacity < 1) {
    throw new HttpsError("failed-precondition", "O evento ainda não possui uma capacidade válida.");
  }
  return capacity;
}

function isRegistrationOpen(event: DocumentData, now: Timestamp): boolean {
  return event.status === "PUBLISHED" && event.startAt instanceof Timestamp && event.startAt.toMillis() > now.toMillis();
}

function waitlistOfferDeadline(event: DocumentData, now: Timestamp): Timestamp {
  const dailyDeadline = now.toMillis() + CONFIRMATION_WINDOW_MS;
  const eventStarts = event.startAt instanceof Timestamp ? event.startAt.toMillis() : dailyDeadline;
  return Timestamp.fromMillis(Math.min(dailyDeadline, eventStarts));
}

function nonNegativeCount(value: unknown): number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : 0;
}

function waitlistQuery(eventId: string): Query {
  return db.collection("events").doc(eventId).collection("registrations")
    .where("status", "==", "LISTA_ESPERA")
    .orderBy("queuePosition", "asc")
    .limit(1);
}

export const publishEvent = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  await requireRole(uid, "organizer");
  const eventId = requireEventId(request.data?.eventId);
  const ref = db.collection("events").doc(eventId);
  const now = Timestamp.now();

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) throw new HttpsError("not-found", "Evento não encontrado.");
    const data = snapshot.data()!;
    if (data.creatorUid !== uid) throw new HttpsError("permission-denied", "Este evento pertence a outro organizador.");
    const capacity = eventCapacity(data);
    const title = typeof data.title === "string" ? data.title.trim() : "";
    const description = typeof data.description === "string" ? data.description.trim() : "";
    const category = typeof data.category === "string" ? data.category.trim() : "";
    const place = typeof data.place === "string" ? data.place.trim() : "";
    const startAt = data.startAt;
    const location = data.location;
    const coverImageUrl = typeof data.coverImageUrl === "string" ? data.coverImageUrl.trim() : "";
    let validImageUrl = false;
    try {
      const parsed = new URL(coverImageUrl);
      validImageUrl = parsed.protocol === "https:" && Boolean(parsed.hostname) && !parsed.hostname.endsWith(".invalid");
    } catch {
      validImageUrl = false;
    }
    if (title.length < 3 || title.length > 120 || description.length < 1 || description.length > 2000 ||
        category.length < 1 || category.length > 80 || place.length < 2 || place.length > 200 ||
        !(startAt instanceof Timestamp) || startAt.toMillis() <= now.toMillis() ||
        !(location instanceof GeoPoint) || !validImageUrl) {
      throw new HttpsError("failed-precondition", "Preencha os dados do evento com valores válidos antes de publicar.");
    }
    if (data.status === "ENDED") throw new HttpsError("failed-precondition", "Um evento encerrado não pode ser publicado novamente.");
    if (nonNegativeCount(data.confirmedCount) + nonNegativeCount(data.reservedCount) > capacity) {
      throw new HttpsError("failed-precondition", "A capacidade não pode ficar abaixo das vagas ocupadas.");
    }
    transaction.update(ref, { status: "PUBLISHED", publishedAt: now, updatedAt: now });
  });
  return { status: "PUBLISHED" };
});

export const endEvent = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  await requireRole(uid, "organizer");
  const eventId = requireEventId(request.data?.eventId);
  const ref = db.collection("events").doc(eventId);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) throw new HttpsError("not-found", "Evento não encontrado.");
    if (snapshot.get("creatorUid") !== uid) throw new HttpsError("permission-denied", "Este evento pertence a outro organizador.");
    if (snapshot.get("status") !== "PUBLISHED") throw new HttpsError("failed-precondition", "Somente eventos publicados podem ser encerrados.");
    transaction.update(ref, { status: "ENDED", endedAt: Timestamp.now(), updatedAt: Timestamp.now() });
  });
  return { status: "ENDED" };
});

export const joinEvent = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  const profile = await requireRole(uid, "student");
  const eventId = requireEventId(request.data?.eventId);
  const eventRef = db.collection("events").doc(eventId);
  const regRef = registrationRef(eventId, uid);
  const mirrorRef = userRegistrationRef(uid, eventId);
  const now = Timestamp.now();
  let result: { status: RegistrationState } = { status: "CANCELADA" };

  await db.runTransaction(async (transaction) => {
    const [eventSnap, regSnap, mirrorSnap] = await Promise.all([
      transaction.get(eventRef), transaction.get(regRef), transaction.get(mirrorRef),
    ]);
    if (!eventSnap.exists) throw new HttpsError("not-found", "Evento não encontrado.");
    const event = eventSnap.data()!;
    if (event.status !== "PUBLISHED" || !(event.startAt instanceof Timestamp) || event.startAt.toMillis() <= now.toMillis()) {
      throw new HttpsError("failed-precondition", "As inscrições deste evento estão encerradas.");
    }
    if (regSnap.exists && activeState(regSnap.get("status"))) {
      result = { status: regSnap.get("status") as RegistrationState };
      return;
    }
    const capacity = eventCapacity(event);
    const confirmedCount = nonNegativeCount(event.confirmedCount);
    const reservedCount = nonNegativeCount(event.reservedCount);
    const available = confirmedCount + reservedCount < capacity;
    const status: RegistrationState = available ? "CONFIRMADA" : "LISTA_ESPERA";
    const queuePosition = nonNegativeCount(event.registrationSequence) + 1;
    const qr = available ? makeQrToken() : null;
    const registeredAt = now;
    const registration = {
      eventId,
      eventTitle: event.title,
      userId: uid,
      studentName: String(profile.get("name") || request.auth?.token.name || "Aluno"),
      studentEmail: String(profile.get("email") || request.auth?.token.email || ""),
      recordType: "event",
      status,
      queuePosition,
      registeredAt,
      updatedAt: now,
      confirmationExpiresAt: null,
      checkedInAt: null,
      qrTokenHash: qr?.hash ?? null,
    };
    transaction.set(regRef, registration);
    transaction.set(mirrorRef, registrationMirror(eventId, String(event.title ?? "Evento"), uid, registration));
    transaction.update(eventRef, {
      confirmedCount: confirmedCount + (available ? 1 : 0),
      waitingCount: nonNegativeCount(event.waitingCount) + (available ? 0 : 1),
      registrationSequence: queuePosition,
      updatedAt: now,
    });
    result = { status };
    void mirrorSnap;
  });
  return result;
});

export const cancelRegistration = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  await requireRole(uid, "student");
  const eventId = requireEventId(request.data?.eventId);
  const eventRef = db.collection("events").doc(eventId);
  const regRef = registrationRef(eventId, uid);
  const mirrorRef = userRegistrationRef(uid, eventId);
  const now = Timestamp.now();
  let nextSnapshot: QueryDocumentSnapshot | undefined;

  await db.runTransaction(async (transaction) => {
    const [eventSnap, regSnap, ownMirror] = await Promise.all([
      transaction.get(eventRef), transaction.get(regRef), transaction.get(mirrorRef),
    ]);
    if (!eventSnap.exists || !regSnap.exists) throw new HttpsError("not-found", "Inscrição não encontrada.");
    const event = eventSnap.data()!;
    const current = regSnap.data()!;
    const status = current.status as RegistrationState;
    if (!activeState(status) || status === "PRESENTE") throw new HttpsError("failed-precondition", "Esta inscrição não pode ser cancelada.");
    const mayPromote = isRegistrationOpen(event, now);
    if (mayPromote && (status === "CONFIRMADA" || status === "AGUARDANDO_CONFIRMACAO")) {
      const queued = await transaction.get(waitlistQuery(eventId));
      nextSnapshot = queued.docs[0];
      if (nextSnapshot) await transaction.get(userRegistrationRef(nextSnapshot.id, eventId));
    }
    const confirmed = nonNegativeCount(event.confirmedCount) - (status === "CONFIRMADA" ? 1 : 0);
    let reserved = nonNegativeCount(event.reservedCount) - (status === "AGUARDANDO_CONFIRMACAO" ? 1 : 0);
    let waiting = nonNegativeCount(event.waitingCount) - (status === "LISTA_ESPERA" ? 1 : 0);
    const cancelled = { ...current, status: "CANCELADA", cancellationReason: "student_cancelled", qrTokenHash: null, confirmationExpiresAt: null, updatedAt: now };
    transaction.set(regRef, cancelled);
    transaction.set(mirrorRef, registrationMirror(eventId, String(event.title ?? current.eventTitle ?? "Evento"), uid, cancelled));
    if (mayPromote && (status === "CONFIRMADA" || status === "AGUARDANDO_CONFIRMACAO") && nextSnapshot) {
      const nextRef = nextSnapshot.ref;
      const nextRegistration = { ...nextSnapshot.data(), status: "AGUARDANDO_CONFIRMACAO", confirmationExpiresAt: waitlistOfferDeadline(event, now), updatedAt: now };
      transaction.set(nextRef, nextRegistration);
      transaction.set(userRegistrationRef(nextSnapshot.id, eventId), registrationMirror(eventId, String(event.title ?? "Evento"), nextSnapshot.id, nextRegistration));
      waiting = Math.max(0, waiting - 1);
      reserved += 1;
    }
    transaction.update(eventRef, { confirmedCount: Math.max(0, confirmed), reservedCount: Math.max(0, reserved), waitingCount: Math.max(0, waiting), updatedAt: now });
    void ownMirror;
  });
  return { status: "CANCELADA", promotedUserId: nextSnapshot?.id ?? null };
});

export const confirmWaitlist = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  await requireRole(uid, "student");
  const eventId = requireEventId(request.data?.eventId);
  const eventRef = db.collection("events").doc(eventId);
  const regRef = registrationRef(eventId, uid);
  const mirrorRef = userRegistrationRef(uid, eventId);
  const now = Timestamp.now();
  const qr = makeQrToken();

  await db.runTransaction(async (transaction) => {
    const [eventSnap, regSnap] = await Promise.all([transaction.get(eventRef), transaction.get(regRef)]);
    if (!eventSnap.exists || !regSnap.exists) throw new HttpsError("not-found", "Convite não encontrado.");
    const event = eventSnap.data()!;
    const registration = regSnap.data()!;
    const deadline = registration.confirmationExpiresAt;
    if (!isRegistrationOpen(event, now) || registration.status !== "AGUARDANDO_CONFIRMACAO" || !(deadline instanceof Timestamp) || deadline.toMillis() <= now.toMillis()) {
      throw new HttpsError("deadline-exceeded", "O prazo da vaga expirou. A oportunidade foi passada ao próximo aluno.");
    }
    if (nonNegativeCount(event.confirmedCount) + nonNegativeCount(event.reservedCount) > eventCapacity(event)) {
      throw new HttpsError("failed-precondition", "A vaga não está mais reservada.");
    }
    const confirmed = { ...registration, status: "CONFIRMADA", confirmationExpiresAt: null, qrTokenHash: qr.hash, updatedAt: now };
    transaction.set(regRef, confirmed);
    transaction.set(mirrorRef, registrationMirror(eventId, String(event.title ?? registration.eventTitle ?? "Evento"), uid, confirmed));
    transaction.update(eventRef, { confirmedCount: nonNegativeCount(event.confirmedCount) + 1, reservedCount: Math.max(0, nonNegativeCount(event.reservedCount) - 1), updatedAt: now });
  });
  return { status: "CONFIRMADA", qrPayload: `CAMPUSHUB|${eventId}|${uid}|${qr.token}` };
});

export const getRegistrationQr = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  await requireRole(uid, "student");
  const eventId = requireEventId(request.data?.eventId);
  const eventRef = db.collection("events").doc(eventId);
  const regRef = registrationRef(eventId, uid);
  const qr = makeQrToken();
  const now = Timestamp.now();
  await db.runTransaction(async (transaction) => {
    const [eventSnap, regSnap] = await Promise.all([transaction.get(eventRef), transaction.get(regRef)]);
    if (!eventSnap.exists || !regSnap.exists || !["CONFIRMADA", "PRESENTE"].includes(String(regSnap.get("status")))) {
      throw new HttpsError("failed-precondition", "Somente participantes confirmados ou presentes possuem QR Code.");
    }
    transaction.update(regRef, { qrTokenHash: qr.hash, qrIssuedAt: now, updatedAt: now });
  });
  return { qrPayload: `CAMPUSHUB|${eventId}|${uid}|${qr.token}` };
});

export const validateCheckIn = onCall(async (request) => {
  // Legacy endpoint kept so older clients receive a clear error instead of
  // bypassing the student-request / organizer-approval flow.
  requireUid(request.auth?.uid);
  throw new HttpsError("failed-precondition", "O participante deve ler o QR Code do evento e aguardar a aprovação do organizador.");
});

export const getAttendanceQr = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  const profile = await db.collection("users").doc(uid).get();
  const role = profile.exists ? profile.get("role") : undefined;
  if (!profile.exists || (role !== "organizer" && role !== "student")) {
    throw new HttpsError("permission-denied", "Seu perfil não pode acessar o QR Code deste evento.");
  }
  const eventId = requireEventId(request.data?.eventId);
  const eventRef = db.collection("events").doc(eventId);
  const qrRef = eventRef.collection("internal").doc("attendanceQr");
  const now = Timestamp.now();
  const registrationRefForStudent = role === "student" ? registrationRef(eventId, uid) : null;
  let token = "";
  await db.runTransaction(async (transaction) => {
    const [eventSnap, qrSnap, registrationSnap] = await Promise.all([
      transaction.get(eventRef),
      transaction.get(qrRef),
      registrationRefForStudent ? transaction.get(registrationRefForStudent) : Promise.resolve(null),
    ]);
    if (!eventSnap.exists || !["PUBLISHED", "ENDED"].includes(String(eventSnap.get("status")))) {
      throw new HttpsError("failed-precondition", "O evento não está disponível para gerar o QR Code de presença.");
    }
    const isEventOrganizer = role === "organizer" && eventSnap.get("creatorUid") === uid;
    const isConfirmedParticipant = role === "student" && registrationSnap?.exists
      && ["CONFIRMADA", "PRESENTE"].includes(String(registrationSnap.get("status")));
    if (!isEventOrganizer && !isConfirmedParticipant) {
      throw new HttpsError("permission-denied", "Somente o organizador ou um participante confirmado pode acessar este QR Code.");
    }

    const savedToken = qrSnap.get("token");
    const savedHash = qrSnap.get("tokenHash");
    if (qrSnap.exists && typeof savedToken === "string" && savedToken.length > 0
      && savedHash === createHash("sha256").update(savedToken).digest("hex")) {
      token = savedToken;
      return;
    }

    const qr = makeQrToken();
    token = qr.token;
    transaction.set(qrRef, { token: qr.token, tokenHash: qr.hash, updatedAt: now });
  });
  return { qrPayload: `CAMPUSHUB_EVENT|${eventId}|${token}` };
});

export const requestAttendance = onCall(async (request) => {
  const uid = requireUid(request.auth?.uid);
  await requireRole(uid, "student");
  const eventId = requireEventId(request.data?.eventId);
  const payload = request.data?.qrPayload;
  if (typeof payload !== "string" || payload.length > 512) {
    throw new HttpsError("invalid-argument", "QR Code inválido.");
  }
  const [prefix, qrEventId, token, ...rest] = payload.split("|");
  if (prefix !== "CAMPUSHUB_EVENT" || qrEventId !== eventId || !token || rest.length > 0) {
    throw new HttpsError("invalid-argument", "Este QR Code não pertence a este evento.");
  }

  const tokenHash = createHash("sha256").update(token).digest("hex");
  const eventRef = db.collection("events").doc(eventId);
  const qrRef = eventRef.collection("internal").doc("attendanceQr");
  const regRef = registrationRef(eventId, uid);
  const requestRef = eventRef.collection("attendanceRequests").doc(uid);
  const now = Timestamp.now();
  let resultStatus = "PENDING";

  await db.runTransaction(async (transaction) => {
    const [eventSnap, qrSnap, regSnap, attendanceRequestSnap] = await Promise.all([
      transaction.get(eventRef),
      transaction.get(qrRef),
      transaction.get(regRef),
      transaction.get(requestRef),
    ]);
    if (!eventSnap.exists || !regSnap.exists) throw new HttpsError("not-found", "Inscrição não encontrada.");
    if (!["PUBLISHED", "ENDED"].includes(String(eventSnap.get("status")))) {
      throw new HttpsError("failed-precondition", "O evento não está disponível para check-in.");
    }
    if (!qrSnap.exists || qrSnap.get("tokenHash") !== tokenHash) {
      throw new HttpsError("permission-denied", "QR Code inválido ou expirado.");
    }

    const registration = regSnap.data()!;
    if (registration.status === "PRESENTE") {
      resultStatus = "PRESENTE";
      return;
    }
    if (registration.status !== "CONFIRMADA") {
      throw new HttpsError("failed-precondition", "Somente inscrições confirmadas podem solicitar presença.");
    }
    if (attendanceRequestSnap.exists && attendanceRequestSnap.get("status") === "PENDING") {
      resultStatus = "PENDING";
      return;
    }

    transaction.set(requestRef, {
      eventId,
      userId: uid,
      studentName: String(registration.studentName || "Aluno"),
      studentEmail: String(registration.studentEmail || ""),
      status: "PENDING",
      requestedAt: now,
      updatedAt: now,
    });
  });

  return { status: resultStatus };
});

export const decideAttendanceRequest = onCall(async (request) => {
  const organizerUid = requireUid(request.auth?.uid);
  await requireRole(organizerUid, "organizer");
  const eventId = requireEventId(request.data?.eventId);
  const studentUid = requireUid(request.data?.studentUid);
  const approved = request.data?.approved;
  if (typeof approved !== "boolean") throw new HttpsError("invalid-argument", "Decisão inválida.");

  const eventRef = db.collection("events").doc(eventId);
  const requestRef = eventRef.collection("attendanceRequests").doc(studentUid);
  const regRef = registrationRef(eventId, studentUid);
  const mirrorRef = userRegistrationRef(studentUid, eventId);
  const now = Timestamp.now();
  let resultStatus = approved ? "APPROVED" : "REJECTED";

  await db.runTransaction(async (transaction) => {
    const [eventSnap, attendanceRequestSnap, regSnap] = await Promise.all([
      transaction.get(eventRef),
      transaction.get(requestRef),
      transaction.get(regRef),
    ]);
    if (!eventSnap.exists || eventSnap.get("creatorUid") !== organizerUid) {
      throw new HttpsError("permission-denied", "Você não administra este evento.");
    }
    if (!attendanceRequestSnap.exists || attendanceRequestSnap.get("status") !== "PENDING") {
      throw new HttpsError("failed-precondition", "Esta solicitação já foi respondida ou não está mais disponível.");
    }
    if (!regSnap.exists || regSnap.get("status") !== "CONFIRMADA") {
      resultStatus = "INVALIDATED";
      transaction.update(requestRef, {
        status: resultStatus,
        decidedAt: now,
        decidedBy: organizerUid,
        updatedAt: now,
      });
      return;
    }

    if (!approved) {
      transaction.update(requestRef, {
        status: "REJECTED",
        decidedAt: now,
        decidedBy: organizerUid,
        updatedAt: now,
      });
      return;
    }

    const registration = regSnap.data()!;
    const checkedIn = {
      ...registration,
      status: "PRESENTE",
      checkedInAt: now,
      checkedInBy: organizerUid,
      qrTokenHash: null,
      updatedAt: now,
    };
    transaction.set(regRef, checkedIn);
    transaction.set(mirrorRef, registrationMirror(
      eventId,
      String(eventSnap.get("title") ?? registration.eventTitle ?? "Evento"),
      studentUid,
      checkedIn,
    ));
    transaction.update(requestRef, {
      status: "APPROVED",
      decidedAt: now,
      decidedBy: organizerUid,
      updatedAt: now,
    });
  });

  return { status: resultStatus };
});

export const expireWaitlistOffers = onSchedule({ schedule: "every 1 minutes", timeoutSeconds: 540 }, async () => {
  const now = Timestamp.now();
  const expired = await db.collectionGroup("registrations")
    .where("recordType", "==", "event")
    .where("status", "==", "AGUARDANDO_CONFIRMACAO")
    .where("confirmationExpiresAt", "<=", now)
    .limit(100)
    .get();

  for (const stale of expired.docs) {
    const eventRef = stale.ref.parent.parent;
    if (!eventRef) continue;
    const eventId = eventRef.id;
    const uid = stale.id;
    await db.runTransaction(async (transaction) => {
      const [eventSnap, currentSnap] = await Promise.all([transaction.get(eventRef), transaction.get(stale.ref)]);
      if (!eventSnap.exists || !currentSnap.exists || currentSnap.get("status") !== "AGUARDANDO_CONFIRMACAO") return;
      const deadline = currentSnap.get("confirmationExpiresAt");
      if (!(deadline instanceof Timestamp) || deadline.toMillis() > now.toMillis()) return;
      const event = eventSnap.data()!;
      const queued = isRegistrationOpen(event, now) ? await transaction.get(waitlistQuery(eventId)) : null;
      const next = queued?.docs[0];
      if (next) await transaction.get(userRegistrationRef(next.id, eventId));
      const current = { ...currentSnap.data(), status: "CANCELADA", cancellationReason: "confirmation_expired", confirmationExpiresAt: null, qrTokenHash: null, updatedAt: now };
      transaction.set(stale.ref, current);
      transaction.set(userRegistrationRef(uid, eventId), registrationMirror(eventId, String(event.title ?? currentSnap.get("eventTitle") ?? "Evento"), uid, current));
      let reserved = Math.max(0, nonNegativeCount(event.reservedCount) - 1);
      let waiting = nonNegativeCount(event.waitingCount);
      if (next) {
        const promoted = { ...next.data(), status: "AGUARDANDO_CONFIRMACAO", confirmationExpiresAt: waitlistOfferDeadline(event, now), updatedAt: now };
        transaction.set(next.ref, promoted);
        transaction.set(userRegistrationRef(next.id, eventId), registrationMirror(eventId, String(event.title ?? "Evento"), next.id, promoted));
        reserved += 1;
        waiting = Math.max(0, waiting - 1);
      }
      transaction.update(eventRef, { reservedCount: reserved, waitingCount: waiting, updatedAt: now });
    });
  }
});
