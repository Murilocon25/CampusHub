# Maestro E2E login tests

The login flows run against the local Firebase Authentication and Firestore emulators. The `useFirebaseEmulators` Gradle property opts the debug build into the emulators; release builds always keep the normal Firebase endpoints.

## Prerequisites

- Android Studio with an Android Virtual Device running.
- Java 17 or later.
- Firebase CLI (`npm install -g firebase-tools`).
- Maestro CLI. On Linux: `curl -fsSL "https://get.maestro.mobile.dev" | bash`.

## Run the login flows

Start the configured Firebase emulators from the project root:

```sh
firebase emulators:start --only auth,firestore
```

In a second terminal, build and install the debug app with emulator routing enabled, then seed a local test account:

```sh
./gradlew installDebug -PuseFirebaseEmulators=true
node scripts/maestro/seed-login-user.mjs
```

Run all three UI flows. The defaults below match the seeded emulator account; override them with the same environment variables when needed.

```sh
maestro test \
  -e LOGIN_EMAIL="${LOGIN_EMAIL:-maestro@campushub.test}" \
  -e LOGIN_PASSWORD="${LOGIN_PASSWORD:-CampusHubE2E123!}" \
  .maestro
```

The flows cover empty fields, invalid email, a wrong password, and successful login through to the events screen. The test user exists only in the local Auth Emulator.
