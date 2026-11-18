const emulatorUrl = (process.env.FIREBASE_AUTH_EMULATOR_URL ?? "http://127.0.0.1:9099")
  .replace(/\/+$/, "");
const email = process.env.LOGIN_EMAIL ?? "maestro@campushub.test";
const password = process.env.LOGIN_PASSWORD ?? "CampusHubE2E123!";
const apiKey = "fake-api-key";

async function post(path) {
  const response = await fetch(
    `${emulatorUrl}/identitytoolkit.googleapis.com/v1/${path}?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, returnSecureToken: true }),
    },
  );
  const body = await response.json();
  return { response, body };
}

const signup = await post("accounts:signUp");
if (signup.response.ok) {
  console.log(`E2E account ready in Auth Emulator: ${email}`);
  process.exit(0);
}

if (signup.body.error?.message === "EMAIL_EXISTS") {
  const signIn = await post("accounts:signInWithPassword");
  if (signIn.response.ok) {
    console.log(`E2E account already exists in Auth Emulator: ${email}`);
    process.exit(0);
  }
  throw new Error(`Existing E2E account credentials do not match: ${JSON.stringify(signIn.body)}`);
}

throw new Error(`Could not seed Auth Emulator user: ${JSON.stringify(signup.body)}`);
