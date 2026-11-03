package com.pleos.routine

import android.os.Build
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.InputSource
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The host drives only the official Console UI. No injected app state or vehicle writes. */
@RunWith(AndroidJUnit4::class)
class DrivingRestrictionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun report(message:String) = InstrumentationRegistry.getInstrumentation().sendStatus(
        2,Bundle().apply { putString("stream",message+"\n") })

    @Test fun actualConsoleDriveClosesEditorAndRetainsDraft() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("drivingConsole")=="manual")
        assumeTrue(Build.HARDWARE=="ranchu" && Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        val app=compose.activity.application as RoutineApplication
        compose.waitUntil(15000) { app.platform.state.value.parkedAndStationary() && compose.onAllNodesWithText("02 확인").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(app.runtime.busy.value)
        compose.onNodeWithText("02 확인").performClick()
        compose.onNodeWithText("이름 변경").performClick()
        compose.onNodeWithText("루틴 이름").assertExists()
        val before=runBlocking(Dispatchers.IO) { app.database.routines().working()?.definition }
        report("CONSOLE_READY parked=true editorOpen=true writes=0")
        compose.waitUntil(120000) {
            val x=app.platform.state.value
            x.source==InputSource.REAL && x.gear.value=="D" && (x.speedMps.value ?: 0.0)>0.5 && x.uxRestricted
        }
        val moving=app.platform.state.value
        compose.onNodeWithText("루틴 이름").assertDoesNotExist()
        compose.onNodeWithText("주행 제한 · 초안 보관").assertExists()
        compose.waitUntil(10000) { compose.onAllNodesWithText("초안 저장됨").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before,runBlocking(Dispatchers.IO) { app.database.routines().working()?.definition })
        assertFalse(app.runtime.busy.value)
        report("CONSOLE_MOVING source=${moving.source} gear=${moving.gear.value} speedMps=${moving.speedMps.value} uxRestricted=${moving.uxRestricted} editorClosed=true draftPreserved=true")
        compose.waitUntil(120000) { app.platform.state.value.parkedAndStationary() }
        compose.onNodeWithText("편집 가능").assertExists()
        compose.onNodeWithText("이름 변경").assertExists()
        report("CONSOLE_PASS parkedAgain=true editingRestored=true noRoutineStarted=true")
    }
}
