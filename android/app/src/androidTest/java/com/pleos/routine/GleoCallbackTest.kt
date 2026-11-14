package com.pleos.routine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.WorkflowExamples
import com.pleos.routine.core.WorkflowJson
import com.pleos.routine.data.RoutineRecord
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual exported activity and broadcast format with a local receiver. */
@RunWith(AndroidJUnit4::class)
class GleoCallbackTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun controlReviewProducesAskingAndTerminalBroadcastsWithoutDispatch() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val dao = app.database.routines()
        assumeTrue(io { app.pendingRequests.read() == null && dao.enabled().isEmpty() })
        assumeTrue(!app.runtime.busy.value && (!app.platform.automotive || !app.platform.state.value.uxRestricted))
        val original = io { app.database.withTransaction {
            listOf(dao.working(), dao.workingRequest(), dao.workingOrigin(), dao.workingClarification(), dao.workingGleoReview())
        } }
        val initialEvents = app.runtime.events.value.toList()
        val initialRuns = io { dao.observeRuns().first() }
        val workflow = WorkflowExamples.blank(UUID.randomUUID().toString())
        val target = RoutineRecord(workflow.id, WorkflowJson.encode(workflow))
        val replies = LinkedBlockingQueue<JsonObject>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getStringExtra("packageName") != app.packageName) return
                val response = Json.parseToJsonElement(intent.getStringExtra("response") ?: return).jsonObject
                if (response["functionName"]?.jsonPrimitive?.content == "CONTROL_ROUTINE") replies.offer(response)
            }
        }
        // The reply is an implicit broadcast addressed to Gleo, an external app. Android 14 does not deliver
        // implicit broadcasts to a non-exported runtime receiver, so the stand-in must be exported like Gleo's.
        ContextCompat.registerReceiver(app, receiver, IntentFilter("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"),
            ContextCompat.RECEIVER_EXPORTED)
        var screen: ActivityScenario<MainActivity>? = null
        fun expect(status: String): String {
            val response = requireNotNull(replies.poll(7, TimeUnit.SECONDS)) { "No local Gleo callback for $status" }
            assertEquals(status, response.getValue("status").jsonPrimitive.content)
            return response.getValue("message").jsonPrimitive.content
        }
        try {
            io { dao.put(target) }
            val activity = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java))
            screen = activity
            compose.waitUntil(15_000) { compose.onAllNodesWithText("초안 저장됨").fetchSemanticsNodes().isNotEmpty() }
            fun request(operation: String) {
                activity.onActivity {
                    it.startActivity(Intent(it, GleoActivity::class.java).setAction("CONTROL_ROUTINE")
                        .putExtra("ACTION_PARAM", buildJsonObject {
                            put("routineId", workflow.id)
                            put("operation", operation)
                        }.toString()))
                }
            }
            request("invalid")
            expect("fail")
            request("enable")
            expect("asking")
            compose.waitUntil(15_000) { compose.onAllNodesWithText("요청 확인").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("요청 확인").assertIsEnabled().performClick()
            assertTrue(expect("success").contains("No vehicle command"))
            request("start")
            expect("asking")
            compose.waitUntil(15_000) { compose.onAllNodesWithText("거절").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("거절").performClick()
            expect("fail")
            assertEquals(target, io { dao.routine(workflow.id) })
            assertEquals(initialRuns, io { dao.observeRuns().first() })
            assertEquals(initialEvents, app.runtime.events.value)
            assertFalse(app.runtime.busy.value)
            assertNull(io { app.pendingRequests.read() })
        } finally {
            screen?.close()
            app.unregisterReceiver(receiver)
            io { app.database.withTransaction {
                val sql = app.database.openHelper.writableDatabase
                sql.execSQL("DELETE FROM routines WHERE id=?", arrayOf(workflow.id))
                sql.execSQL("DELETE FROM drafts WHERE id IN ('working','working-request','working-origin','working-clarification','working-gleo-review')")
                original.filterNotNull().forEach { dao.draft(it) }
            } }
        }
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
