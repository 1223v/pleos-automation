package com.pleos.routine

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RunHistoryUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun <T> io(block:suspend()->T):T=runBlocking(Dispatchers.IO) { block() }
    private fun fixture(test:(RoutineApplication,WorkflowDefinition)->Unit) {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val dao=app.database.routines()
        check(!app.runtime.busy.value && io { app.pendingRequests.read()==null && dao.enabled().isEmpty() })
        val original=io { listOf(dao.working(),dao.workingRequest(),dao.workingOrigin()) }
        val w=WorkflowExamples.blank("history-ui-${UUID.randomUUID()}").copy(title="A05 실행 당시 루틴",revision=7)
        try { test(app,w) } finally {
            io { app.database.withTransaction {
                val db=app.database.openHelper.writableDatabase
                db.execSQL("DELETE FROM run_events WHERE runId IN (SELECT id FROM runs WHERE routineId = ?)",arrayOf(w.id))
                db.execSQL("DELETE FROM runs WHERE routineId = ?",arrayOf(w.id))
                db.execSQL("DELETE FROM routines WHERE id = ?",arrayOf(w.id))
                listOf("working","working-request","working-origin").forEach { db.execSQL("DELETE FROM drafts WHERE id = ?",arrayOf(it)) }
                original.filterNotNull().forEach { dao.draft(it) }
            } }
        }
    }
    @Test fun simulationStoresSnapshotAndHistoryDisplaysVersionAndCause()=fixture { app,w ->
        io { app.database.routines().draft(DraftRecord("working",WorkflowJson.encode(w))) }
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use {
            compose.waitUntil(15000) { compose.onAllNodesWithText("04 테스트").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("04 테스트").performClick()
            compose.onNodeWithText("이 상황으로 테스트").performScrollTo().performClick()
            compose.waitUntil(10000) { io { app.database.routines().observeRuns().first().any { it.routineId==w.id } } }
            val row=io { app.database.routines().observeRuns().first().first { it.routineId==w.id } }
            assertEquals(w,RunHistory.definition(row));assertEquals("SIMULATION",row.startCause)
            compose.onNodeWithText("실행 이력").performClick()
            compose.onNode(hasText(w.title) and hasText("버전 7",substring=true) and hasText("가상 입력 테스트",substring=true)).assertExists()
        }
    }
    @Test fun oldHistoryShowsObservedValueAndOpensItsConditionWithoutOverwritingSavedVersion()=fixture { app,blank ->
        val base=WorkflowExamples.commute().copy(id=blank.id,title=blank.title,revision=7)
        val latest=base.copy(revision=8,title="최신 저장본")
        val e=ExecutionEvent("history-run-${UUID.randomUUID()}","cold",ActionStatus.BLOCKED,"조건값 확인 불가",12,sequence=1,observedValue="12.0°C")
        val result=RunResult(e.runId,base.id,7,WorkflowJson.semanticHash(base),false,RunStatus.BLOCKED,listOf(e),12)
        io {
            val dao=app.database.routines();dao.put(RoutineRecord(latest.id,WorkflowJson.encode(latest)))
            dao.putRun(RunRecord(e.runId,base.id,WorkflowJson.format.encodeToString(result),"BLOCKED",false,
                definition=WorkflowJson.encode(base),revision=7,semanticHash=result.semanticHash,startCause="AUTOMATIC"))
        }
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use {
            compose.waitUntil(15000) { compose.onAllNodesWithText("실행 이력").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("실행 이력").performClick()
            compose.onNodeWithText(base.title).performClick()
            compose.onNodeWithText("자동 실행",substring=true).assertExists()
            compose.onNodeWithText("관찰값: 12.0°C",substring=true).performScrollTo().assertExists()
            compose.onNodeWithText("조건 편집 · 외기 온도 ≤ 5°C").performScrollTo().performClick()
            compose.waitUntil(10000) { io { app.database.routines().working()?.definition?.let(WorkflowJson::decode)?.let { draft ->
                draft.id!=base.id && draft.title=="${base.title} 복사" && draft.nodes==base.nodes
            }==true } }
            val working=io { WorkflowJson.decode(app.database.routines().working()!!.definition) }
            assertNotEquals(base.id,working.id);assertEquals(base.nodes,working.nodes)
            assertEquals(latest,io { WorkflowJson.decode(app.database.routines().routine(base.id)!!.definition) })
            compose.onNodeWithText("외기 온도 ≤ 5°C 수정").assertExists()
        }
    }
    @Test fun manualRunPersistsStartCauseBeforeFinishing()=fixture { app,w ->
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use {
            compose.waitUntil(15000) { compose.onAllNodesWithText("실행 이력").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { app.runtime.requestRun(w) }
            compose.waitUntil(15000) { io { app.database.routines().observeRuns().first().any { it.routineId==w.id && it.state!="RUNNING" } } }
            val row=io { app.database.routines().observeRuns().first().first { it.routineId==w.id } }
            assertEquals("MANUAL",row.startCause);assertEquals(w,RunHistory.definition(row))
        }
    }
    @Test fun delayedHistoryReadCannotOverwriteNewerRequest()=fixture { app,w ->
        val result=RunResult("delayed-${UUID.randomUUID()}",w.id,w.revision,WorkflowJson.semanticHash(w),true,RunStatus.COMPLETED,emptyList(),0)
        io { app.database.routines().putRun(RunHistory.simulation(w,result)) }
        ActivityScenario.launch<MainActivity>(Intent(app,MainActivity::class.java)).use {
            compose.waitUntil(15000) { compose.onAllNodesWithText("실행 이력").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("실행 이력").performClick();compose.onNodeWithText(w.title).performClick()
            compose.onNodeWithText("시작 조건 편집").performScrollTo()
            // Hold the real Room query executor at its IO boundary, not the UI thread.
            val entered=CountDownLatch(4);val release=CountDownLatch(1)
            repeat(4) { app.database.queryExecutor.execute { entered.countDown();release.await(15,TimeUnit.SECONDS) } }
            try {
                assertTrue("Room query executor must be held",entered.await(5,TimeUnit.SECONDS))
                compose.onNodeWithText("시작 조건 편집").performClick()
                if(compose.onAllNodesWithText("취소").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithText("취소").performClick()
                compose.onNodeWithText("루틴 만들기").performClick();compose.onNodeWithText("01 생성").performClick()
                val newer="최신 사용자 요청 ${UUID.randomUUID()}"
                compose.onNodeWithText("나의 요청").performTextReplacement(newer)
                release.countDown()
                io { app.database.routines().routine(w.id) }
                // Allow the 350ms autosave and the formerly delayed UI continuation to settle.
                compose.mainClock.advanceTimeBy(1000);compose.waitForIdle()
                compose.onNodeWithText(newer).assertExists()
                compose.waitUntil(10000) { io { app.database.routines().workingRequest()?.definition==newer } }
                assertEquals(newer,io { app.database.routines().workingRequest()!!.definition })
            } finally { release.countDown() }
        }
    }
}
