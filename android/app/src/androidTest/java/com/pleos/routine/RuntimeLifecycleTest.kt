package com.pleos.routine

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RuntimeLifecycleTest {
    @Test fun ownAudioAndTimerSurviveHiddenActivityAndCancelCleanly():Unit=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            delay(2500)
            assertFalse("Requires actual unrestricted AVD",app.platform.snapshot().uxRestricted)
            val id="lifecycle-"+UUID.randomUUID().toString()
            val blank=WorkflowExamples.blank(id)
            val audio=GraphEditing.insert(blank,blank.edges.single().id,NodeKind.ACTION,ActionSpec(ActionType.PLAY_LOCAL_AUDIO,targetId="rest-tone"))
            val a=audio.nodes.single { it.kind==NodeKind.ACTION }
            val w=GraphEditing.insert(audio,audio.edges.single { it.source==a.id }.id,NodeKind.TIMER).let { graph -> graph.copy(nodes=graph.nodes.map { if(it.kind==NodeKind.TIMER)it.copy(durationSeconds=120) else it }) }
            withContext(Dispatchers.Main) { app.runtime.requestRun(w) }
            withTimeout(15000) { while(app.runtime.events.value.none { it.nodeId==a.id && it.status==ActionStatus.VERIFIED })delay(100) }
            // HOME does not hide the app in Pleos's split-screen shell. ActivityScenario
            // covers the app with its EmptyActivity and verifies the stopped lifecycle.
            scenario.moveToState(Lifecycle.State.CREATED)
            delay(1500)
            assertFalse(app.runtime.visible)
            assertTrue("Foreground routine must survive activity hiding",app.runtime.busy.value)
            withContext(Dispatchers.Main) { app.runtime.cancel() }
            withTimeout(15000) { while(app.runtime.busy.value)delay(100) }
            assertEquals(RunStatus.CANCELLED,app.runtime.lastResult.value?.status)
            val row=withContext(Dispatchers.IO) { app.database.routines().observeRuns().first().firstOrNull { it.routineId==id }?.state }
            assertEquals("CANCELLED",row)
            Log.i("RoutineEvidence","LIFECYCLE actual local player VERIFIED; activity hidden 1500ms; timer cancelled; journal=$row")
        } finally {
            withContext(Dispatchers.Main) { app.runtime.cancel() }
            scenario.close()
        }
    }
    @Test fun gleoSchemaIsPackagedUnderRequiredName() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val schema=app.assets.open("schema.json").bufferedReader().use { it.readText() }
        assertTrue(schema.contains("CREATE_ROUTINE"))
        assertEquals(app.assets.open("gleo-app-schema.json").bufferedReader().use { it.readText() },schema)
    }
}
