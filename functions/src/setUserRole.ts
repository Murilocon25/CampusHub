import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

initializeApp();

const db = getFirestore();

async function main() {
  const [uid, role] = process.argv.slice(2);
  if (!uid || !/^[A-Za-z0-9_-]{1,128}$/.test(uid) || !["student", "organizer"].includes(role ?? "")) {
    throw new Error("Usage: npm run role:set -- <uid> <student|organizer>");
  }

  const profile = db.collection("users").doc(uid);
  const existing = await profile.get();
  if (!existing.exists) throw new Error(`No user profile exists for UID ${uid}.`);

  await profile.update({ role });
  console.log(`Set user ${uid} role to ${role}.`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
