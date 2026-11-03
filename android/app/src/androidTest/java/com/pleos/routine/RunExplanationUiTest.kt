package com.pleos.routine

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Requires the explicitly injected local Node fixture and adb reverse18787; never a real AI key. */
class RunExplanationUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun <T> io(block:suspend()->T):T=runBlocking(Dispatchers.IO) { block() }
    @Test fun localHttpExplanationDisplaysEvidenceWithoutChangingUnknownRun() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("a05Fixture")=="true")
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        check(!app.runtime.busy.value && io { app.pendingRequests.read()==null && app.database.routines().enabled().isEmpty() })
        val prefs=app.getSharedPreferences("connection",0)
        val oldEndpoint=prefs.getString("endpoint",null);val oldToken=prefs.getString("token",null)
        val w=WorkflowExamples.blank("explanation-ui-${UUID.randomUUID()}").copy(title="A05 로컬 설명 연결")
        val event=ExecutionEvent(UUID.randomUUID().toString(),"start",ActionStatus.PENDING,"저장된 시작 단계",0,sequence=1)
        val result=RunResult(event.runId,w.id,w.revision,WorkflowJson.semanticHash(w),false,RunStatus.UNKNOWN,listOf(event),0)
        val row=RunRecord(event.runId,w.id,null,"UNKNOWN",false,
            definition=WorkflowJson.encode(w),revision=w.revision,semanticHash=result.semanticHash,startCause="MANUAL")
        try {
            app.planner.endpoint="http://127.0.0.1:18787";app.planner.token="a05-local-fixture-token-000000000000"
            io { app.database.routines().putRun(row);app.database.routines().insertEvent(RunEventRecord(row.id,1,WorkflowJson.format.encodeToString(event))) }
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(15000) { compose.onAllNodesWithText("실행 이력").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("실행 이력").performClick()
                compose.onNodeWithText(w.title).performClick()
                compose.onNodeWithText("저장된 시작 단계",substring=true).performScrollTo().assertExists()
                compose.onNodeWithText("루틴 만들기").performClick()
                val entered=CountDownLatch(4);val release=CountDownLatch(1)
                repeat(4) { app.database.queryExecutor.execute { entered.countDown();release.await(15,TimeUnit.SECONDS) } }
                try {
                    assertTrue(entered.await(5,TimeUnit.SECONDS))
                    compose.onNodeWithText("실행 이력").performClick();compose.onNodeWithText(w.title).performClick()
                    compose.onNodeWithText("AI 실행 설명 요청").performScrollTo().assertIsNotEnabled()
                } finally { release.countDown() }
                compose.waitUntil(10000) { compose.onAllNodesWithText("저장된 시작 단계",substring=true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("AI 실행 설명 요청").performScrollTo().performClick()
                compose.waitUntil(15000) { compose.onAllNodesWithText("AI 설명 · 모의 설명: 실행 이력 근거 확인").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("AI 설명 · 모의 설명: 실행 이력 근거 확인").performScrollTo().assertExists()
                compose.onNodeWithText("근거 단계: 1",substring=true).assertExists()
                assertEquals(row,io { app.database.routines().run(row.id) });assertFalse(app.runtime.busy.value)
            }
        } finally {
            prefs.edit().apply { if(oldEndpoint==null)remove("endpoint") else putString("endpoint",oldEndpoint);if(oldToken==null)remove("token") else putString("token",oldToken) }.commit()
            io { app.database.openHelper.writableDatabase.execSQL("DELETE FROM run_events WHERE runId = ?",arrayOf(row.id));app.database.openHelper.writableDatabase.execSQL("DELETE FROM runs WHERE id = ?",arrayOf(row.id)) }
        }
    }
}
