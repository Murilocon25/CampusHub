import { initializeApp } from "firebase-admin/app";
import { DocumentData, Timestamp, getFirestore } from "firebase-admin/firestore";

initializeApp();

const db = getFirestore();
const ACTIVE = new Set(["CONFIRMADA", "LISTA_ESPERA", "AGUARDANDO_CONFIRMACAO", "PRESENTE"]);
const FALLBACK_COVER = "https://campushub.invalid/legacy-cover.png";

async function main() {
  const users = await db.collection("users").get();
  const profiles = new Map(users.docs.map((user) => [user.id, user.data()]));
  const registrationsByEvent = new Map<string, Array<{ uid: string; data: DocumentData }>>();

  for (const user of users.docs) {
    const registrations = await user.ref.collection("registrations").get();
    for (const registration of registrations.docs) {
      const data = registration.data();
      if (data.recordType === "user") continue;
      const rows = registrationsByEvent.get(registration.id) ?? [];
      rows.push({ uid: user.id, data });
      registrationsByEvent.set(registration.id, rows);
    }
  }

  const events = await db.collection("events").get();
  const organizerIds = new Set(events.docs.map((event) => event.get("creatorUid")).filter(
    (uid): uid is string => typeof uid === "string" && uid.length > 0,
  ));
  let batch = db.batch();
  let writes = 0;
  let migratedEvents = 0;
  let migratedRegistrations = 0;

  const flushIfNeeded = async () => {
    if (writes >= 400) {
      await batch.commit();
      batch = db.batch();
      writes = 0;
    }
  };

  for (const user of users.docs) {
    const oldRole = profiles.get(user.id)?.role;
    const role = organizerIds.has(user.id) ? "organizer" : oldRole === "organizer" ? "organizer" : "student";
    if (user.get("role") !== role) {
      batch.set(user.ref, { role }, { merge: true });
      writes++;
      await flushIfNeeded();
    }
  }

  const now = Timestamp.now();
  for (const eventDoc of events.docs) {
    const eventId = eventDoc.id;
    const eventRef = eventDoc.ref;
    const existingRegistrations = await eventRef.collection("registrations").get();
    const canonicalByUid = new Map(existingRegistrations.docs.map((registration) => [registration.id, registration.data()]));
    const legacyRows = registrationsByEvent.get(eventId) ?? [];
    const importedRows: Array<{ uid: string; data: DocumentData }> = [];

    for (const row of legacyRows) {
      if (canonicalByUid.has(row.uid)) continue;
      const oldStatus = typeof row.data.status === "string" ? row.data.status : "CONFIRMADA";
      if (oldStatus === "CANCELADA" || oldStatus === "CANCELLED") continue;
      const status = ACTIVE.has(oldStatus) ? oldStatus : "CONFIRMADA";
      const profile = profiles.get(row.uid) ?? {};
      const registeredAt = row.data.registeredAt instanceof Timestamp ? row.data.registeredAt : now;
      const registration = {
        eventId,
        eventTitle: String(eventDoc.get("title") ?? "Evento"),
        userId: row.uid,
        studentName: String(profile.name ?? "Aluno"),
        studentEmail: String(profile.email ?? ""),
        recordType: "event",
        status,
        registeredAt,
        updatedAt: now,
        confirmationExpiresAt: null,
        checkedInAt: null,
        qrTokenHash: null,
      };
      batch.set(eventRef.collection("registrations").doc(row.uid), registration, { merge: true });
      batch.set(db.collection("users").doc(row.uid).collection("registrations").doc(eventId), {
        eventId,
        eventTitle: registration.eventTitle,
        userId: row.uid,
        recordType: "user",
        status,
        registeredAt,
        updatedAt: now,
        confirmationExpiresAt: null,
        checkedInAt: null,
      }, { merge: true });
      writes += 2;
      migratedRegistrations++;
      importedRows.push({ uid: row.uid, data: registration });
      await flushIfNeeded();
    }

    const allRegistrations = [
      ...existingRegistrations.docs.map((row) => ({ uid: row.id, data: row.data(), ref: row.ref })),
      ...importedRows.map((row) => ({ uid: row.uid, data: row.data, ref: eventRef.collection("registrations").doc(row.uid) })),
    ];
    const sequenced = allRegistrations.filter((row) => Number.isSafeInteger(row.data.queuePosition) && row.data.queuePosition > 0)
      .sort((left, right) => left.data.queuePosition - right.data.queuePosition);
    const unsequenced = allRegistrations.filter((row) => !Number.isSafeInteger(row.data.queuePosition) || row.data.queuePosition <= 0)
      .sort((left, right) => {
        const leftAt = left.data.registeredAt instanceof Timestamp ? left.data.registeredAt.toMillis() : 0;
        const rightAt = right.data.registeredAt instanceof Timestamp ? right.data.registeredAt.toMillis() : 0;
        return leftAt - rightAt || left.uid.localeCompare(right.uid);
      });
    let registrationSequence = sequenced.reduce((highest, row) => Math.max(highest, row.data.queuePosition), 0);
    for (const row of unsequenced) {
      registrationSequence++;
      const registeredAt = row.data.registeredAt instanceof Timestamp ? row.data.registeredAt : now;
      batch.set(row.ref, { queuePosition: registrationSequence, registeredAt }, { merge: true });
      writes++;
      await flushIfNeeded();
    }
    const registrationRows = allRegistrations.map((row) => row.data);
    const confirmedCount = registrationRows.filter((row) => row.status === "CONFIRMADA" || row.status === "PRESENTE").length;
    const waitingCount = registrationRows.filter((row) => row.status === "LISTA_ESPERA").length;
    const reservedCount = registrationRows.filter((row) => row.status === "AGUARDANDO_CONFIRMACAO").length;
    const oldCapacity = eventDoc.get("capacity");
    const capacity = Math.max(
      Number.isSafeInteger(oldCapacity) && oldCapacity > 0 ? oldCapacity : 100,
      confirmedCount + reservedCount,
    );
    if (capacity > 50000) throw new Error(`Event ${eventId} has more than 50,000 confirmed participants.`);
    const oldStatus = eventDoc.get("status");
    const status = ["DRAFT", "PUBLISHED", "ENDED"].includes(oldStatus) ? oldStatus : "PUBLISHED";
    batch.set(eventRef, {
      capacity,
      coverImageUrl: typeof eventDoc.get("coverImageUrl") === "string" && String(eventDoc.get("coverImageUrl")).startsWith("https://")
        ? eventDoc.get("coverImageUrl")
        : FALLBACK_COVER,
      status,
      confirmedCount,
      waitingCount,
      reservedCount,
      registrationSequence,
      createdAt: eventDoc.get("createdAt") instanceof Timestamp ? eventDoc.get("createdAt") : now,
      updatedAt: now,
    }, { merge: true });
    writes++;
    migratedEvents++;
    await flushIfNeeded();
  }

  if (writes > 0) await batch.commit();
  console.log(`Updated ${users.size} profiles, ${migratedEvents} events, and migrated ${migratedRegistrations} legacy registrations.`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
