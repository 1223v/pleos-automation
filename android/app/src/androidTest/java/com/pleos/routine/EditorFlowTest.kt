package com.pleos.routine

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pleos.routine.core.WorkflowJson
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun typedRequestSurvivesActivityRecreationWithoutAiCall() {
        val app=compose.activity.application as RoutineApplication
        assumeTrue(runBlocking(Dispatchers.IO) { app.pendingRequests.read()==null })
        val original=runBlocking(Dispatchers.IO) { app.database.routines().workingRequest()?.definition.orEmpty() }
        val request="평일 아침 요청 복원 시험 ${System.nanoTime()}"
        compose.waitUntil(15000) { compose.onAllNodesWithText("01 생성").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("01 생성").performClick()
        try {
            compose.onNodeWithText("나의 요청").performTextReplacement(request)
            compose.waitUntil(10000) { runBlocking(Dispatchers.IO) { app.database.routines().workingRequest()?.definition==request } }
            val graph=runBlocking(Dispatchers.IO) { app.database.routines().working()?.definition }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) { compose.onAllNodesWithText("01 생성").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("01 생성").performClick()
            compose.onNodeWithText(request).assertExists()
            assertEquals(graph,runBlocking(Dispatchers.IO) { app.database.routines().working()?.definition })
            assertFalse(app.runtime.busy.value)
        } finally {
            compose.onNodeWithText("나의 요청").performTextReplacement(original)
            compose.waitUntil(10000) { runBlocking(Dispatchers.IO) { app.database.routines().workingRequest()?.definition==original } }
        }
    }
    @Test fun createSimulateSaveAndOpenWithoutProviderOrVehicleWrites() {
        val dao=(compose.activity.application as RoutineApplication).database.routines()
        compose.waitUntil(15000) { compose.onAllNodesWithText("01 생성").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("01 생성").performClick()
        compose.onNodeWithText("빈 흐름").performScrollTo().performClick()
        compose.onNodeWithText("04 테스트").performClick()
        compose.onNodeWithText("이 상황으로 테스트").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("COMPLETED",substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("검토한 루틴 저장").performScrollTo().performClick()
        compose.onNodeWithText("계속 편집").performClick()
        compose.onNodeWithText("검토한 루틴 저장").performScrollTo().performClick()
        compose.onNodeWithText("저장할 이름").performTextReplacement("주차 휴식 편집 시험")
        compose.onNodeWithText("저장",substring=false).performClick()
        compose.waitUntil(10000) { runBlocking(Dispatchers.IO) {
            dao.working()?.definition?.let(WorkflowJson::decode)?.title=="주차 휴식 편집 시험"
        } }
        val id=runBlocking(Dispatchers.IO) { WorkflowJson.decode(dao.working()!!.definition).id }
        compose.onNodeWithText("내 루틴").performClick()
        val open=hasTestTag("open-routine-$id")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(open)
        compose.onNode(open).performClick()
        compose.onNodeWithText("02 확인").assertExists()
        compose.onNodeWithText("주차 휴식 편집 시험").assertExists()
    }
}
