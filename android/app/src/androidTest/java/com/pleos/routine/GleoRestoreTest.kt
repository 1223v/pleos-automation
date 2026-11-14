package com.pleos.routine

import android.content.Intent
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.WorkflowExamples
import com.pleos.routine.core.WorkflowJson
import com.pleos.routine.data.DraftRecord
import com.pleos.routine.data.PendingRoutineRequest
import com.pleos.routine.data.RoutineRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class GleoRestoreTest {
    // Launch manually so no Activity can autosave over the user's draft before it is backed up.
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun retainedCreateRequestDoesNotReplaceLatestEditsAfterRecreation() {
        assertLatestEditsSurviveRecreation("CREATE_ROUTINE", includeIntentRequest = false)
    }

    @Test fun retainedEditRequestAndSameIntentDoNotReloadSavedOriginalAfterRecreation() {
        assertLatestEditsSurviveRecreation("EDIT_ROUTINE", includeIntentRequest = true)
    }

    @Test fun staleRequestBeforeAutosavePreservesLiveEditor() {
        assertLatestEditsSurviveRecreation("EDIT_ROUTINE", true, "EDIT_ROUTINE")
    }

    @Test fun controlRequestBeforeAutosavePreservesLiveEditor() {
        assertLatestEditsSurviveRecreation("CREATE_ROUTINE", true, "CONTROL_ROUTINE")
    }

    @Test fun newRequestDiscardsResponseFromPreviousPendingProposal() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val dao = app.database.routines()
        assumeTrue(io { app.pendingRequests.read() == null && dao.enabled().isEmpty() })
        assumeTrue(!app.runtime.busy.value && (!app.platform.automotive || !app.platform.state.value.uxRestricted))
        val drafts = io { app.database.withTransaction { listOf(dao.working(), dao.workingRequest(), dao.workingOrigin()) } }
        val prefs = app.getSharedPreferences("connection", 0)
        val oldEndpoint = prefs.getString("endpoint", null)
        val oldToken = prefs.getString("token", null)
        val a = PendingRoutineRequest(action = "CREATE_ROUTINE", text = "첫 번째 요청")
        val b = PendingRoutineRequest(action = "CREATE_ROUTINE", text = "새로운 두 번째 요청")
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        // Controlled local HTTP only: no provider key, external network or real AI call.
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            runCatching { server.accept().use { socket ->
                socket.soTimeout = 15_000
                val input = socket.getInputStream()
                val header = StringBuilder()
                while (!header.endsWith("\r\n\r\n")) { val byte = input.read(); check(byte >= 0); header.append(byte.toChar()) }
                val length = Regex("(?im)^content-length: *([0-9]+)").find(header)?.groupValues?.get(1)?.toInt() ?: 0
                repeat(length) { check(input.read() >= 0) }
                received.countDown()
                check(release.await(15, TimeUnit.SECONDS))
                val body = """{"status":"draft","explanation":"이전 요청 응답","questions":[],"patch":null,"workflow":${WorkflowJson.encode(WorkflowExamples.blank("obsolete-proposal"))}}""".toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body); flush()
                }
            } }
        }
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            io { assertTrue(app.pendingRequests.save(a)) }
            app.planner.endpoint = "http://127.0.0.1:${server.localPort}"
            app.planner.token = "local-test-token-".repeat(3)
            val launched = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
            scenario = launched
            waitForSavedEditor()
            compose.onNodeWithText("AI로 초안 만들기").performClick()
            assertTrue("The local stub must receive request A", received.await(10, TimeUnit.SECONDS))
            io { assertTrue(app.pendingRequests.save(b)) }
            launched.onActivity { activity ->
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity,
                    Intent(app, MainActivity::class.java).putExtra("routineAction", b.action)
                        .putExtra("requestId", b.id).putExtra("requestText", b.text))
            }
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction() and hasText(b.text)).fetchSemanticsNodes().isNotEmpty() }
            release.countDown()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("AI 변경 제안 확인").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("AI로 초안 만들기").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
            }
            compose.onNodeWithText("AI 변경 제안 확인").assertDoesNotExist()
            compose.onNode(hasSetTextAction() and hasText(b.text)).assertExists()
            assertEquals(b, io { app.pendingRequests.read() })
            assertFalse(app.runtime.busy.value)
        } finally {
            release.countDown(); server.close(); worker.join(2_000)
            scenario?.close()
            io {
                prefs.edit().putString("endpoint", oldEndpoint).putString("token", oldToken).commit()
                app.pendingRequests.clearIf(a.id); app.pendingRequests.clearIf(b.id)
                app.database.withTransaction {
                    listOf("working", "working-request", "working-origin").forEach { id ->
                        app.database.openHelper.writableDatabase.execSQL("DELETE FROM drafts WHERE id = ?", arrayOf(id))
                    }
                    drafts.filterNotNull().forEach { dao.draft(it) }
                }
            }
        }
    }

    private fun assertLatestEditsSurviveRecreation(action: String, includeIntentRequest: Boolean, entryBeforeSave: String? = null) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val dao = app.database.routines()
        assumeTrue("Do not replace a user's pending Gleo draft", io { app.pendingRequests.read() == null })
        assumeTrue("Do not run this editor regression during a real routine", !app.runtime.busy.value)
        assumeTrue("Existing active routines must not execute during this editor regression", io { dao.enabled().isEmpty() })
        assumeTrue("The editor must be available while parked", !app.platform.automotive || !app.platform.state.value.uxRestricted)

        val originalDrafts = io {
            app.database.withTransaction { listOf(dao.working(), dao.workingRequest(), dao.workingOrigin()) }
        }
        val originalEvents = app.runtime.events.value.toList()
        val suffix = UUID.randomUUID().toString()
        val working = WorkflowExamples.blank("working-$suffix").copy(title = "보관 중인 작업 흐름 $suffix")
        val target = WorkflowExamples.blank("routine-$suffix").copy(title = "원본 저장 루틴 $suffix")
        val editing = action == "EDIT_ROUTINE"
        val initialGraph = if (editing) target else working
        val pending = PendingRoutineRequest(
            id = "request-$suffix", action = action, text = "Gleo에서 처음 요청한 내용 $suffix",
            routineId = if (editing) target.id else null,
        )
        var latestRequest = "직접 수정한 최신 요청 $suffix"
        val latestTitle = "수정한 작업 흐름 $suffix"
        var scenario: ActivityScenario<MainActivity>? = null
        var insertedTarget = false
        try {
            io {
                app.database.withTransaction {
                    if (editing) {
                        assertNull("The test fixture must have a unique ID", dao.routine(target.id))
                        dao.put(RoutineRecord(target.id, WorkflowJson.encode(target), enabled = false))
                    }
                    dao.draft(DraftRecord("working", WorkflowJson.encode(working)))
                    dao.draft(DraftRecord("working-request", "이전 작업의 요청"))
                    dao.draft(DraftRecord("working-origin", "previous-$suffix"))
                }
                insertedTarget = editing
                assertTrue("The retained request must be durable before launch", app.pendingRequests.save(pending))
            }
            val intent = Intent(app, MainActivity::class.java).apply {
                if (includeIntentRequest) {
                    putExtra("routineAction", pending.action)
                    putExtra("requestText", pending.text)
                    putExtra("routineId", pending.routineId)
                    putExtra("requestId", pending.id)
                }
            }
            val launched = ActivityScenario.launch<MainActivity>(intent)
            scenario = launched
            compose.waitUntil(15_000) {
                val draft = readDraft(app)
                draft.text == pending.text && draft.origin == pending.id &&
                    draft.graph?.let { WorkflowJson.decode(it).id } == initialGraph.id
            }
            waitForSavedEditor()
            compose.onNodeWithText("01 생성").performClick()
            compose.onNode(hasSetTextAction() and hasText(pending.text)).assertExists()
            compose.onNodeWithText("나의 요청").performTextReplacement(latestRequest)
            compose.onNodeWithText("02 확인").performClick()
            compose.onNodeWithText(initialGraph.title).assertExists()
            compose.onNodeWithText("이름 변경").performScrollTo().performClick()
            compose.onNodeWithText("이름").performTextReplacement(latestTitle)
            compose.onNodeWithText("이름 저장").performClick()

            // Removing origin persistence/replay suppression must fail this real UI + Room flow.
            compose.waitUntil(10_000) {
                val draft = readDraft(app)
                val graph = draft.graph?.let(WorkflowJson::decode)
                draft.text == latestRequest && draft.origin == pending.id && graph != null &&
                    graph.id == initialGraph.id && graph.title == latestTitle && graph.revision == 2
            }
            waitForSavedEditor()
            if (entryBeforeSave != null) {
                compose.onNodeWithText("01 생성").performClick()
                val input = compose.onNode(hasSetTextAction() and hasText(latestRequest)).fetchSemanticsNode()
                    .config[SemanticsActions.SetText].action!!
                latestRequest = "아직 저장되지 않은 마지막 입력 $suffix"
                // Both callbacks occur in one UI task, before recomposition or the 350 ms save.
                // Disposing the editor on a new Intent loses this live value and must fail below.
                launched.onActivity { activity ->
                    assertTrue(input(AnnotatedString(latestRequest)))
                    InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, Intent(app, MainActivity::class.java)
                        .putExtra("routineAction", entryBeforeSave).putExtra("requestId", "immediate-$suffix")
                        .putExtra("routineId", target.id).putExtra("requestText", "오래된 요청"))
                }
                compose.onNodeWithText("루틴 만들기").performClick()
                compose.onNodeWithText("01 생성").performClick()
                compose.onNode(hasSetTextAction() and hasText(latestRequest)).assertExists()
                compose.waitUntil(10_000) { readDraft(app).text == latestRequest }
                assertEquals(latestTitle, WorkflowJson.decode(readDraft(app).graph!!).title)
                assertEquals(pending.id, readDraft(app).origin)
            }
            val latest = readDraft(app)
            // A stale entry must reuse this editor and cannot outrank the current private request B.
            val staleId="old-$suffix"
            launched.onActivity { activity -> activity.startActivity(Intent(app,MainActivity::class.java)
                .putExtra("routineAction","EDIT_ROUTINE").putExtra("requestId",staleId)
                .putExtra("routineId",target.id).putExtra("requestText","오래된 A 요청")) }
            compose.waitUntil(10_000) {
                var delivered=false
                launched.onActivity { delivered=it.intent.getStringExtra("requestId")==staleId }
                delivered
            }
            waitForSavedEditor()
            assertEquals("Old Intent A must not overwrite latest working B",latest,readDraft(app))
            repeat(2) {
                launched.recreate()
                // Wait for this new Activity's restore AND autosave, not the old database rows.
                waitForSavedEditor()
                compose.onNodeWithText("02 확인").performClick()
                compose.onNodeWithText(latestTitle).assertExists()
                compose.onNodeWithText("01 생성").performClick()
                compose.onNode(hasSetTextAction() and hasText(latestRequest)).assertExists()
                assertEquals("Recreation must retain the entire latest working draft", latest, readDraft(app))
                assertEquals("Reviewing a request must not silently discard it", pending, io { app.pendingRequests.read() })
            }
            if (editing) {
                val saved = io { dao.routine(target.id) }
                assertNotNull(saved)
                assertEquals("Editing the working copy must leave its saved original intact", WorkflowJson.encode(target), saved!!.definition)
                assertFalse(saved.enabled)
            }
            assertFalse("Draft review must not start a run", app.runtime.busy.value)
            assertEquals("Draft review must not issue execution events", originalEvents, app.runtime.events.value)
            Log.i("RoutineEvidence", "GLEO_RESTORE action=$action recreations=2 latestTextPreserved=true editedGraphPreserved=true noRunStarted=true")
        } finally {
            try {
                scenario?.close()
            } finally {
                // Close first: a live composition must not autosave over the restored user rows.
                io {
                    app.pendingRequests.clearIf(pending.id)
                    app.database.withTransaction {
                        val db = app.database.openHelper.writableDatabase
                        listOf("working", "working-request", "working-origin").forEach { id ->
                            db.execSQL("DELETE FROM drafts WHERE id = ?", arrayOf(id))
                        }
                        originalDrafts.filterNotNull().forEach { dao.draft(it) }
                        if (insertedTarget) db.execSQL("DELETE FROM routines WHERE id = ?", arrayOf(target.id))
                    }
                }
            }
        }
    }

    private fun waitForSavedEditor() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("초안 저장됨").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private data class DraftState(val graph: String?, val text: String?, val origin: String?)

    private fun readDraft(app: RoutineApplication): DraftState = io {
        app.database.withTransaction {
            val dao = app.database.routines()
            DraftState(dao.working()?.definition, dao.workingRequest()?.definition, dao.workingOrigin()?.definition)
        }
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
