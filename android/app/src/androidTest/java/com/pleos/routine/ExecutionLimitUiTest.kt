package com.pleos.routine

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import com.pleos.routine.data.DraftRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExecutionLimitUiTest {
    @get:Rule val compose=createEmptyComposeRule()

    @Test fun excessiveRepeatDraftRemainsEditableButCannotBeSavedOrTested() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val dao=app.database.routines()
        assumeTrue(!app.runtime.busy.value && io { app.pendingRequests.read()==null && dao.enabled().isEmpty() })
        assumeTrue(!app.platform.automotive || !app.platform.state.value.uxRestricted)
        val original=io { app.database.withTransaction { listOf(dao.working(),dao.workingRequest(),dao.workingOrigin()) } }
        val id="visit-limit-ui-${UUID.randomUUID()}"
        val nodes=mutableListOf(WorkflowNode("start",NodeKind.START,"start"),WorkflowNode("end",NodeKind.END,"end"),
            WorkflowNode("read",NodeKind.ACTION,"read",action=ActionSpec(ActionType.CALENDAR_READ)))
        val edges=mutableListOf(WorkflowEdge("entry","start","r0"),WorkflowEdge("read-end","read","j2"))
        repeat(3) { i ->
            nodes+=WorkflowNode("r$i",NodeKind.REPEAT,"repeat",repeatCount=20,joinId="j$i")
            nodes+=WorkflowNode("j$i",NodeKind.JOIN,"join")
            edges+=WorkflowEdge("body$i","r$i",if(i==2)"read" else "r${i+1}","body")
            edges+=WorkflowEdge("next$i","r$i",if(i==0)"end" else "j${i-1}")
        }
        val w=WorkflowDefinition(id=id,title="한도 초과 편집 초안",nodes=nodes,edges=edges)
        assertTrue(WorkflowValidator.validate(w).any { it.message.contains("4,096") })
        var scenario:ActivityScenario<MainActivity>?=null
        try {
            io { dao.draft(DraftRecord("working",WorkflowJson.encode(w))) }
            scenario=ActivityScenario.launch(Intent(app,MainActivity::class.java))
            compose.waitUntil(15_000) { compose.onAllNodesWithText("02 확인").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("02 확인").performClick()
            compose.onNodeWithText("검토한 루틴 저장").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("04 테스트").performClick()
            compose.onNodeWithText("이 상황으로 테스트").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("이름 변경").performScrollTo().performClick()
            compose.onNodeWithText("이름").performTextReplacement("한도 조정 중인 초안")
            compose.onNodeWithText("이름 저장").performClick()
            compose.waitUntil(10_000) { io { dao.working()?.definition?.let(WorkflowJson::decode)?.title=="한도 조정 중인 초안" } }
            assertNull(io { dao.routine(id) })
            assertFalse(app.runtime.busy.value)
        } finally {
            scenario?.close()
            io { app.database.withTransaction {
                listOf("working","working-request","working-origin").forEach { key ->
                    app.database.openHelper.writableDatabase.execSQL("DELETE FROM drafts WHERE id = ?",arrayOf(key))
                }
                original.filterNotNull().forEach { dao.draft(it) }
            } }
        }
    }
    private fun <T> io(block:suspend()->T):T=runBlocking(Dispatchers.IO) { block() }
}
