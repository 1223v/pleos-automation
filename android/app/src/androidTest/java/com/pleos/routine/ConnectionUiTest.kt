package com.pleos.routine

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pleos.routine.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectionUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun catalogShowsInstalledAppsWithoutLaunchingThem() {
        val app=compose.activity.application as RoutineApplication
        val events=app.runtime.events.value.toList()
        val target=InstalledApps.discover(app).first()
        compose.waitUntil(15000) { compose.onAllNodesWithText("연결 둘러보기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("연결 둘러보기").performClick()
        compose.onNodeWithText("이 기기의 앱").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText(target.packageName).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("설치된 앱 검색").performTextInput(target.packageName)
        compose.onNode(hasText(target.packageName) and !hasSetTextAction()).assertExists()
        assertEquals(events,app.runtime.events.value)
    }
    @Test fun retainedVoiceRequestReopensForReviewWithoutCallingAi():Unit=runBlocking {
        val app=compose.activity.application as RoutineApplication
        val events=app.runtime.events.value.toList()
        assumeTrue("Do not replace a user draft",withContext(Dispatchers.IO) { app.pendingRequests.read()==null })
        val draft=PendingRoutineRequest(action="CREATE_ROUTINE",text="복구된 요청 · 평일 아침 길 안내")
        try {
            withContext(Dispatchers.IO) { assertTrue(app.pendingRequests.save(draft)) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) { compose.onAllNodesWithText("보관된 Gleo 요청",substring=true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(draft.text).assertExists()
            compose.onNodeWithText("AI로 초안 만들기").assertExists()
            assertFalse(app.runtime.busy.value)
            assertEquals(events,app.runtime.events.value)
            compose.onNodeWithText("보관 요청 지우기").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("보관된 Gleo 요청",substring=true).fetchSemanticsNodes().isEmpty() }
            assertNull(withContext(Dispatchers.IO) { app.pendingRequests.read() })
        } finally { withContext(Dispatchers.IO) { app.pendingRequests.clearIf(draft.id) } }
    }
}
