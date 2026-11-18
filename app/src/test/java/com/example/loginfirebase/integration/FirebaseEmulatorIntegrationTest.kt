package com.example.loginfirebase.integration

import android.app.Application
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.example.loginfirebase.data.model.CampusEvent
import com.example.loginfirebase.data.model.toEventComment
import com.example.loginfirebase.data.model.toCampusEvent
import com.example.loginfirebase.data.repository.AuthRepository
import com.example.loginfirebase.data.repository.EventRepository
import com.example.loginfirebase.data.repository.UserProfile
import com.example.loginfirebase.data.repository.UserRepository
import com.example.loginfirebase.data.repository.UserRole
import com.example.loginfirebase.ui.events.EventDetailState
import com.example.loginfirebase.ui.events.EventDetailViewModel
import com.example.loginfirebase.ui.events.EventListState
import com.example.loginfirebase.ui.events.EventListViewModel
import com.example.loginfirebase.ui.events.UserEventsViewModel
import com.example.loginfirebase.ui.profile.ProfileSaveState
import com.example.loginfirebase.ui.profile.ProfileViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.functions.FirebaseFunctions
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Date
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Runs against the local Firebase Auth and Firestore emulators. Start them with:
 * Build functions first, then use `firebase emulators:exec --only auth,firestore,functions --project demo-campushub "./gradlew testDebugUnitTest --tests com.example.loginfirebase.integration.FirebaseEmulatorIntegrationTest"`.
 * Each test app uses a demo project ID and explicitly points the Firebase SDKs at localhost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class FirebaseEmulatorIntegrationTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var firebaseApp: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var functions: FirebaseFunctions
    private lateinit var authRepository: AuthRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var userRepository: UserRepository

    @Before
    fun connectToFirebaseEmulators() {
        assumeTrue(
            "Start Firebase Auth and Firestore emulators for project $PROJECT_ID",
            emulatorIsReachable(AUTH_PORT) && emulatorIsReachable(FIRESTORE_PORT) && emulatorIsReachable(FUNCTIONS_PORT)
        )

        val context = RuntimeEnvironment.getApplication()
        val existingApp = FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP_NAME }
        firebaseApp = existingApp ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApiKey("A" + "x".repeat(38))
                .setApplicationId("1:1234567890:android:campushub-integration")
                .setProjectId(PROJECT_ID)
                .build(),
            FIREBASE_APP_NAME
        )

        auth = FirebaseAuth.getInstance(firebaseApp)
        firestore = FirebaseFirestore.getInstance(firebaseApp)
        functions = FirebaseFunctions.getInstance(firebaseApp, "southamerica-east1")
        if (existingApp == null) {
            auth.useEmulator(EMULATOR_HOST, AUTH_PORT)
            firestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            functions.useEmulator(EMULATOR_HOST, FUNCTIONS_PORT)
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
        }

        authRepository = AuthRepository(auth)
        eventRepository = EventRepository(firestore, functions)
        userRepository = UserRepository(firestore, auth)
    }

    @After
    fun signOutAfterTest() {
        if (::auth.isInitialized) auth.signOut()
    }

    @Test
    fun authRepository_registersSignsOutAndSignsInWithTheSameAccount() {
        val email = uniqueEmail()
        val password = "StrongPass123!"

        val registered = await(authRepository.register(email, password))
        val uid = assertNotNullAndGet(registered.user).uid
        assertEquals(email, authRepository.currentUser?.email)

        authRepository.signOut()
        assertEquals(null, authRepository.currentUser)

        val signedIn = await(authRepository.signIn(email, password))
        assertEquals(uid, assertNotNullAndGet(signedIn.user).uid)
        assertEquals(uid, authRepository.currentUser?.uid)
    }

    @Test
    fun authRepository_passwordResetCreatesAnEmulatorOutOfBandCode() {
        val email = uniqueEmail()
        await(authRepository.register(email, "StrongPass123!"))
        await(authRepository.sendPasswordReset(email))

        val connection = URL(
            "http://$EMULATOR_HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/oobCodes"
        ).openConnection() as HttpURLConnection
        connection.connectTimeout = EMULATOR_TIMEOUT_MS
        connection.readTimeout = EMULATOR_TIMEOUT_MS
        val response = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val codes = JSONObject(response).getJSONArray("oobCodes")
        val resetCode = (0 until codes.length())
            .map(codes::getJSONObject)
            .firstOrNull { it.optString("email") == email }

        assertNotNull("Password reset email should create an emulator action code", resetCode)
        assertEquals("PASSWORD_RESET", resetCode?.getString("requestType"))
        assertTrue(resetCode?.optString("oobCode").orEmpty().isNotBlank())
    }

    @Test
    fun userRepositoryCreatesAndLoadsAProfileThroughFirestore() {
        val uid = createSignedInUser().uid

        val createResult = awaitCallback("create empty profile") { callback ->
            userRepository.createEmptyProfile(uid, callback)
        }
        val loadedProfile = awaitCallback("load profile") { callback ->
            userRepository.loadProfile(uid, callback)
        }

        assertTrue(createResult.isSuccess)
        assertEquals(UserProfile(), loadedProfile.getOrThrow())
    }

    @Test
    fun profileViewModelSaveUpdatesFirestoreAndFirebaseAuthDisplayName() {
        val user = createRoleUser(UserRole.STUDENT)
        val viewModel = ProfileViewModel(userRepository)
        val profile = UserProfile("Ana Souza", "Design", "Campus Central")

        val state = awaitState(
            liveData = viewModel.saveState,
            label = "profile save",
            isComplete = { it is ProfileSaveState.Saved || it is ProfileSaveState.Error }
        ) {
            viewModel.save(user.uid, profile)
        }

        assertEquals(ProfileSaveState.Saved, state)
        await(auth.currentUser!!.reload())
        assertEquals(profile.name, auth.currentUser?.displayName)

        val savedDocument = await(firestore.collection("users").document(user.uid).get())
        assertEquals(profile.name, savedDocument.getString("name"))
        assertEquals(profile.course, savedDocument.getString("course"))
        assertEquals(profile.campus, savedDocument.getString("campus"))
    }

    @Test
    fun userCannotAssignTheOrganizerRoleToTheirOwnProfile() {
        val user = createSignedInUser()
        val result = runCatching {
            await(firestore.collection("users").document(user.uid).set(
                mapOf("name" to "", "course" to "", "campus" to "", "role" to "organizer")
            ))
        }

        val rejection = result.exceptionOrNull()
        assertTrue("Users must not grant themselves organizer access", rejection is FirebaseFirestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, (rejection as FirebaseFirestoreException).code)
    }

    @Test
    fun eventRepositoryWritesAnEventAndMapsTheFirestoreDocument() {
        val user = createRoleUser(UserRole.ORGANIZER)
        val expected = newEvent(creatorUid = user.uid)

        val rejectedWrite = runCatching {
            await(eventRepository.createEvent(
                title = expected.title,
                date = expected.date,
                place = expected.place,
                description = expected.description,
                category = expected.category,
                startAt = expected.startAt!!,
                creatorUid = "another-user",
                location = expected.location!!
            ))
        }
        val rejection = rejectedWrite.exceptionOrNull()
        assertTrue(
            "Expected Firestore to reject the mismatched creator UID, got $rejection",
            rejection is FirebaseFirestoreException
        )
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            (rejection as FirebaseFirestoreException).code
        )

        val reference = await(eventRepository.createEvent(
            title = expected.title,
            date = expected.date,
            place = expected.place,
            description = expected.description,
            category = expected.category,
            startAt = expected.startAt!!,
            creatorUid = user.uid,
            location = expected.location!!
        ))
        val storedEvent = await(eventRepository.getEvent(reference.id)).toCampusEvent()

        assertEquals(reference.id, storedEvent.id)
        assertEquals(expected.title, storedEvent.title)
        assertEquals(expected.category, storedEvent.category)
        assertEquals(expected.creatorUid, storedEvent.creatorUid)
        assertEquals(expected.location, storedEvent.location)
        assertNotNull(storedEvent.startAt)
    }

    @Test
    fun eventListViewModelLoadsFirestoreEventsFromTheRepository() {
        val user = createRoleUser(UserRole.ORGANIZER)
        val expected = createEvent(user.uid)
        val viewModel = EventListViewModel(eventRepository)

        val state = awaitState(
            liveData = viewModel.state,
            label = "event list load",
            isComplete = { it !is EventListState.Loading }
        ) {
            viewModel.loadEvents()
        }

        assertTrue("Expected a successful event list, got $state", state is EventListState.Success)
        assertTrue((state as EventListState.Success).events.any { it.id == expected.id })
    }

    @Test
    fun eventDetailViewModelLoadsTheSelectedFirestoreEvent() {
        val user = createRoleUser(UserRole.ORGANIZER)
        val expected = createEvent(user.uid)
        val viewModel = EventDetailViewModel(eventRepository)

        val state = awaitState(
            liveData = viewModel.state,
            label = "event detail load",
            isComplete = { it !is EventDetailState.Loading }
        ) {
            viewModel.load(expected.id)
        }

        assertTrue("Expected a successful event detail, got $state", state is EventDetailState.Success)
        assertEquals(expected.id, (state as EventDetailState.Success).event.id)
        assertEquals(expected.title, state.event.title)
    }

    @Test
    fun eventRepositoryPublishesUpdatesAndDeletesACommentInFirestore() {
        val user = createRoleUser(UserRole.ORGANIZER)
        val event = createEvent(user.uid)
        val commentRef = await(
            eventRepository.publishComment(event.id, user.uid, "Ana Souza", "Looks great")
        )

        val published = await(eventRepository.getComments(event.id))
        assertEquals(1, published.size())
        assertEquals("Looks great", published.documents.single().toEventComment().text)

        await(eventRepository.updateComment(event.id, commentRef.id, "I will attend"))
        val updated = await(eventRepository.getComments(event.id))
        assertEquals("I will attend", updated.documents.single().getString("text"))

        await(eventRepository.deleteComment(event.id, commentRef.id))
        assertTrue(await(eventRepository.getComments(event.id)).isEmpty)
    }

    @Test
    fun eventRepositoryPersistsFavoritesAndRatingsForTheSignedInUser() {
        val organizer = createRoleAccount(UserRole.ORGANIZER)
        val event = createEvent(organizer.uid)
        signIn(createRoleAccount(UserRole.STUDENT))
        val user = assertNotNullAndGet(auth.currentUser)

        await(eventRepository.saveFavorite(user.uid, event.id))
        val favorite = await(eventRepository.getFavorite(user.uid, event.id))
        assertTrue(favorite.exists())
        assertEquals(event.id, favorite.getString("eventId"))

        await(eventRepository.saveRating(user.uid, event.id, 5))
        val rating = await(eventRepository.getRating(user.uid, event.id))
        assertTrue(rating.exists())
        assertEquals(user.uid, rating.getString("userId"))
        assertEquals(5L, rating.getLong("score"))

        await(eventRepository.removeFavorite(user.uid, event.id))
        assertFalse(await(eventRepository.getFavorite(user.uid, event.id)).exists())
    }

    @Test
    fun userEventsViewModelLoadsAndCancelsARegisteredEvent() {
        val organizer = createRoleAccount(UserRole.ORGANIZER)
        val event = createEvent(organizer.uid)
        val user = createRoleAccount(UserRole.STUDENT)
        signIn(user)

        await(eventRepository.register(user.uid, event))
        val registration = await(eventRepository.getRegistration(user.uid, event.id))
        assertTrue(registration.exists())
        assertEquals(event.title, registration.getString("eventTitle"))

        val viewModel = UserEventsViewModel(eventRepository)
        val state = awaitState(
            liveData = viewModel.state,
            label = "registered events load",
            isComplete = { it !is EventListState.Loading }
        ) {
            viewModel.load(user.uid, "registrations")
        }

        assertTrue("Expected the registered event, got $state", state is EventListState.Success)
        assertTrue((state as EventListState.Success).events.any { it.id == event.id })

        await(eventRepository.cancelRegistration(user.uid, event.id))
        val cancelled = await(eventRepository.getRegistration(user.uid, event.id))
        assertTrue(cancelled.exists())
        assertEquals("CANCELADA", cancelled.getString("status"))
    }

    @Test
    fun registrationsEnforceCapacityPromoteWaitlistInOrderAndValidateQrOnlyOnce() {
        val organizer = createRoleAccount(UserRole.ORGANIZER)
        val event = createEvent(organizer.uid, capacity = 1)
        val firstStudent = createRoleAccount(UserRole.STUDENT)
        val firstResult = await(eventRepository.register(firstStudent.uid, event)).data as Map<*, *>
        assertEquals("CONFIRMADA", firstResult["status"])
        val duplicateResult = await(eventRepository.register(firstStudent.uid, event)).data as Map<*, *>
        assertEquals("CONFIRMADA", duplicateResult["status"])

        val secondStudent = createRoleAccount(UserRole.STUDENT)
        val secondResult = await(eventRepository.register(secondStudent.uid, event)).data as Map<*, *>
        assertEquals("LISTA_ESPERA", secondResult["status"])
        val thirdStudent = createRoleAccount(UserRole.STUDENT)
        val thirdResult = await(eventRepository.register(thirdStudent.uid, event)).data as Map<*, *>
        assertEquals("LISTA_ESPERA", thirdResult["status"])

        val fullEvent = await(eventRepository.getEvent(event.id))
        assertEquals(1L, fullEvent.getLong("confirmedCount"))
        assertEquals(2L, fullEvent.getLong("waitingCount"))

        signIn(firstStudent)
        await(eventRepository.cancelRegistration(firstStudent.uid, event.id))
        assertEquals("CANCELADA", await(eventRepository.getRegistration(firstStudent.uid, event.id)).getString("status"))
        signIn(secondStudent)
        assertEquals("AGUARDANDO_CONFIRMACAO", await(eventRepository.getRegistration(secondStudent.uid, event.id)).getString("status"))
        val pendingDeadline = await(eventRepository.getRegistration(secondStudent.uid, event.id)).getTimestamp("confirmationExpiresAt")
        assertNotNull("The promoted student should receive a confirmation deadline", pendingDeadline)
        assertTrue(pendingDeadline!!.toDate().time > System.currentTimeMillis() + TimeUnit.HOURS.toMillis(23))
        signIn(thirdStudent)
        assertEquals("LISTA_ESPERA", await(eventRepository.getRegistration(thirdStudent.uid, event.id)).getString("status"))
        val offeredEvent = await(eventRepository.getEvent(event.id))
        assertEquals(0L, offeredEvent.getLong("confirmedCount"))
        assertEquals(1L, offeredEvent.getLong("reservedCount"))
        assertEquals(1L, offeredEvent.getLong("waitingCount"))

        signIn(secondStudent)
        val confirmation = await(eventRepository.confirmWaitlist(event.id)).data as Map<*, *>
        assertEquals("CONFIRMADA", confirmation["status"])
        val offerDeadline = await(eventRepository.getRegistration(secondStudent.uid, event.id))
            .getTimestamp("confirmationExpiresAt")
        assertEquals(null, offerDeadline)
        val qr = await(eventRepository.getRegistrationQr(event.id)).data as Map<*, *>
        val qrPayload = qr["qrPayload"] as String
        assertTrue(qrPayload.startsWith("CAMPUSHUB|${event.id}|${secondStudent.uid}|"))
        val occupiedEvent = await(eventRepository.getEvent(event.id))
        assertEquals(1L, occupiedEvent.getLong("confirmedCount"))
        assertEquals(0L, occupiedEvent.getLong("reservedCount"))

        signIn(organizer)
        val checkin = await(eventRepository.validateCheckIn(event.id, qrPayload)).data as Map<*, *>
        assertEquals("PRESENTE", checkin["status"])
        assertEquals(
            "PRESENTE",
            await(firestore.collection("events").document(event.id).collection("registrations").document(secondStudent.uid).get())
                .getString("status")
        )
        val duplicateCheckIn = runCatching { await(eventRepository.validateCheckIn(event.id, qrPayload)) }.exceptionOrNull()
        assertNotNull("A QR Code must not allow a second check-in", duplicateCheckIn)

        signIn(thirdStudent)
        val unauthorizedEnd = runCatching { await(eventRepository.endEvent(event.id)) }.exceptionOrNull()
        assertNotNull("Students must not close events", unauthorizedEnd)
        val unauthorizedCreate = runCatching {
            await(eventRepository.createEvent(
                title = "Student event",
                date = event.date,
                place = event.place,
                description = event.description,
                category = event.category,
                startAt = event.startAt!!,
                creatorUid = thirdStudent.uid,
                location = event.location!!
            ))
        }.exceptionOrNull()
        assertNotNull("Students must not create events", unauthorizedCreate)
        val organizerB = createRoleAccount(UserRole.ORGANIZER)
        val otherEvent = createEvent(organizerB.uid)
        signIn(organizer)
        val wrongEventQr = runCatching { await(eventRepository.validateCheckIn(otherEvent.id, qrPayload)) }.exceptionOrNull()
        assertNotNull("A QR Code must not be accepted for a different event", wrongEventQr)
        val unauthorizedEdit = runCatching {
            await(eventRepository.updateEvent(
                eventId = otherEvent.id,
                title = otherEvent.title,
                date = otherEvent.date,
                place = otherEvent.place,
                description = otherEvent.description,
                category = otherEvent.category,
                startAt = otherEvent.startAt!!,
                location = otherEvent.location!!,
                capacity = otherEvent.capacity,
                coverImageUrl = otherEvent.coverImageUrl
            ))
        }.exceptionOrNull()
        assertNotNull("Organizers must not edit another organizer's event", unauthorizedEdit)
    }

    private fun createSignedInUser(): FirebaseUser {
        val result = await(authRepository.register(uniqueEmail(), "StrongPass123!"))
        return assertNotNullAndGet(result.user)
    }

    private fun createRoleUser(role: UserRole): FirebaseUser {
        createRoleAccount(role)
        return assertNotNullAndGet(auth.currentUser)
    }

    private fun createRoleAccount(role: UserRole): TestAccount {
        val email = uniqueEmail()
        val password = "StrongPass123!"
        val result = await(authRepository.register(email, password))
        val uid = assertNotNullAndGet(result.user).uid
        val profile = if (role == UserRole.ORGANIZER) {
            runCatching { writeOrganizerProfileThroughEmulatorAdminApi(uid) }
        } else {
            awaitCallback("create ${role.name.lowercase()} profile") { callback ->
                userRepository.createEmptyProfile(uid, callback)
            }
        }
        assertTrue("Failed to create ${role.name.lowercase()} profile: ${profile.exceptionOrNull()}", profile.isSuccess)
        return TestAccount(email, password, uid)
    }

    private fun writeOrganizerProfileThroughEmulatorAdminApi(uid: String) {
        val endpoint = URL(
            "http://$EMULATOR_HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/(default)/documents/users?documentId=$uid"
        )
        val fields = JSONObject()
            .put("name", JSONObject().put("stringValue", "Test Organizer"))
            .put("course", JSONObject().put("stringValue", ""))
            .put("campus", JSONObject().put("stringValue", ""))
            .put("role", JSONObject().put("stringValue", UserRole.ORGANIZER.firestoreValue))
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = EMULATOR_TIMEOUT_MS
        connection.readTimeout = 10_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer owner")
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use { it.write(JSONObject().put("fields", fields).toString().toByteArray()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw AssertionError("Could not seed organizer profile in emulator: HTTP $status $body")
            }
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }

    private fun signIn(account: TestAccount) {
        authRepository.signOut()
        await(authRepository.signIn(account.email, account.password))
    }

    private data class TestAccount(val email: String, val password: String, val uid: String)

    private fun createEvent(uid: String, capacity: Int = 100): CampusEvent {
        val expected = newEvent(uid)
        val reference = await(eventRepository.createEvent(
            title = expected.title,
            date = expected.date,
            place = expected.place,
            description = expected.description,
            category = expected.category,
            startAt = expected.startAt!!,
            creatorUid = uid,
            location = expected.location!!,
            capacity = capacity,
            coverImageUrl = "https://example.test/campus-event.png"
        ))
        await(eventRepository.publishEvent(reference.id))
        return expected.copy(id = reference.id, capacity = capacity, coverImageUrl = "https://example.test/campus-event.png")
    }

    private fun newEvent(creatorUid: String) = CampusEvent(
        id = "",
        title = "Integration event ${UUID.randomUUID().toString().take(8)}",
        date = "01/12/2026",
        place = "Campus Central",
        description = "Event used by Firebase integration tests",
        category = "Academic",
        startAt = Timestamp(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7))),
        creatorUid = creatorUid,
        location = GeoPoint(-23.5505, -46.6333)
    )

    private fun uniqueEmail() = "test-${UUID.randomUUID()}@example.test"

    private fun <T> await(task: Task<T>): T {
        val latch = CountDownLatch(1)
        var completedTask: Task<T>? = null
        task.addOnCompleteListener(Executor { command -> command.run() }) {
            completedTask = it
            latch.countDown()
        }
        awaitMainLooperCallback("Firebase task", latch)
        val completed = checkNotNull(completedTask)
        if (!completed.isSuccessful) {
            throw completed.exception ?: IllegalStateException("Firebase task failed without an exception")
        }
        return completed.result
    }

    private fun <T> awaitCallback(
        label: String,
        action: ((Result<T>) -> Unit) -> Unit
    ): Result<T> {
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        action {
            result = it
            latch.countDown()
        }
        awaitMainLooperCallback(label, latch)
        return checkNotNull(result)
    }

    private fun <T> awaitState(
        liveData: LiveData<T>,
        label: String,
        isComplete: (T) -> Boolean,
        action: () -> Unit
    ): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        val observer = Observer<T> { value ->
            if (isComplete(value)) {
                result = value
                latch.countDown()
            }
        }
        liveData.observeForever(observer)
        return try {
            action()
            awaitMainLooperCallback(label, latch)
            checkNotNull(result)
        } finally {
            liveData.removeObserver(observer)
        }
    }

    private fun awaitMainLooperCallback(label: String, latch: CountDownLatch) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS)
        while (latch.count > 0 && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            latch.await(25, TimeUnit.MILLISECONDS)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Timed out waiting for $label", 0L, latch.count)
    }

    private fun emulatorIsReachable(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(EMULATOR_HOST, port), EMULATOR_TIMEOUT_MS)
        }
    }.isSuccess

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return checkNotNull(value)
    }

    private companion object {
        const val PROJECT_ID = "demo-campushub"
        const val FIREBASE_APP_NAME = "campushub-firebase-integration-tests"
        const val EMULATOR_HOST = "127.0.0.1"
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
        const val FUNCTIONS_PORT = 5001
        const val EMULATOR_TIMEOUT_MS = 300
        const val TEST_TIMEOUT_SECONDS = 60L
    }
}
