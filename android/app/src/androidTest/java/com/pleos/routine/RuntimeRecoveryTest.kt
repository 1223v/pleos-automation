package com.pleos.routine

import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Two separate instrumentation processes, with an explicit adb force-stop between them. */
@RunWith(AndroidJUnit4::class)
class RuntimeRecoveryTest {
    private fun app(phase:String):RoutineApplication {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryPhase")==phase)
        assumeTrue(Build.HARDWARE=="ranchu" && Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        return InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
    }
    @Test fun prepareRunningAudioForProcessTermination():Unit=runBlocking {
        val app=app("prepare")
        val prefs=app.getSharedPreferences("runtime_recovery_fixture",0)
        // Retry may remove only the draft created by this fixture, never a user draft.
        withContext(Dispatchers.IO) {
            prefs.getString("pending",null)?.let { app.pendingRequests.clearIf(it) }
        }
        assumeTrue("Do not replace an existing user voice draft",withContext(Dispatchers.IO) { app.pendingRequests.read()==null })
        assumeTrue("Do not interrupt a real run",!app.runtime.busy.value)
        assumeTrue("Do not test process death with active user routines",withContext(Dispatchers.IO) { app.database.routines().enabled().isEmpty() })
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            delay(2500)
            assertFalse(app.platform.snapshot().uxRestricted)
            val blank=WorkflowExamples.blank("recovery-"+UUID.randomUUID()).copy(title="실행 복구 검증 · 비활성")
            val audio=GraphEditing.insert(blank,blank.edges.single().id,NodeKind.ACTION,ActionSpec(ActionType.PLAY_LOCAL_AUDIO,targetId="rest-tone"))
            val node=audio.nodes.single { it.kind==NodeKind.ACTION }
            val graph=GraphEditing.insert(audio,audio.edges.single { it.source==node.id }.id,NodeKind.TIMER)
            val w=graph.copy(nodes=graph.nodes.map { if(it.kind==NodeKind.TIMER)it.copy(durationSeconds=120) else it })
            withContext(Dispatchers.IO) { app.database.routines().put(RoutineRecord(w.id,WorkflowJson.encode(w))) }
            withContext(Dispatchers.Main) { app.runtime.requestRun(w) }
            withTimeout(15000) { while(app.runtime.events.value.none { it.nodeId==node.id && it.status==ActionStatus.VERIFIED })delay(100) }
            assertTrue(app.runtime.busy.value)
            assertTrue(withContext(Dispatchers.Main) { app.runtime.isOwnAudioPlaying })
            val row=withContext(Dispatchers.IO) { app.database.routines().observeRuns().first().first { it.routineId==w.id } }
            assertEquals("RUNNING",row.state)
            assertEquals(WorkflowJson.encode(w),row.definition)
            val prefix=withContext(Dispatchers.IO) { app.database.routines().events(row.id) }
                .map { WorkflowJson.format.decodeFromString<ExecutionEvent>(it.eventJson) }
            assertTrue("Verified audio must already be durable before process death",prefix.any { it.nodeId==node.id && it.status==ActionStatus.VERIFIED })
            val request=PendingRoutineRequest(action="EDIT_ROUTINE",text="복구 시험용 요청",routineId=w.id)
            withContext(Dispatchers.IO) {
                assertTrue(app.pendingRequests.save(request))
                assertTrue(prefs.edit().putString("routine",w.id).putString("run",row.id).putString("hash",WorkflowJson.semanticHash(w))
                    .putString("snapshot",row.definition).putInt("revision",w.revision).putString("events",WorkflowJson.format.encodeToString(prefix))
                    .putString("pending",request.id).putInt("pid",Process.myPid()).commit())
            }
            evidence("READY pid=${Process.myPid()} routine=${w.id} journal=RUNNING audioPlaying=true pendingDraftSaved=true")
            // Host must force-stop only this test package after READY. A normal timeout fails.
            withTimeout(60000) { awaitCancellation() }
        } finally {
            withContext(Dispatchers.Main) { app.runtime.cancel() }
            scenario.close()
        }
    }
    @Test fun newProcessRecoversUnknownWithoutReplaying():Unit=runBlocking {
        val app=app("verify")
        val prefs=app.getSharedPreferences("runtime_recovery_fixture",0)
        val id=requireNotNull(prefs.getString("routine",null))
        val runId=requireNotNull(prefs.getString("run",null))
        assertNotEquals("Must start a genuinely different process",prefs.getInt("pid",-1),Process.myPid())
        withTimeout(10000) {
            while(withContext(Dispatchers.IO) { app.database.routines().observeRuns().first().find { it.id==runId }?.state }!="UNKNOWN")delay(100)
        }
        val stored=withContext(Dispatchers.IO) { app.database.routines().routine(id) }!!
        assertFalse(stored.enabled)
        assertEquals(prefs.getString("hash",null),WorkflowJson.semanticHash(WorkflowJson.decode(stored.definition)))
        assertEquals(prefs.getString("pending",null),withContext(Dispatchers.IO) { app.pendingRequests.read()?.id })
        val recovered=withContext(Dispatchers.IO) { app.database.routines().run(runId) }!!
        assertEquals(prefs.getString("snapshot",null),recovered.definition)
        assertEquals(prefs.getInt("revision",-1),recovered.revision)
        assertEquals(prefs.getString("hash",null),recovered.semanticHash)
        val prefix=WorkflowJson.format.decodeFromString<List<ExecutionEvent>>(prefs.getString("events",null)!!)
        val retained=withContext(Dispatchers.IO) { app.database.routines().events(runId) }
            .map { WorkflowJson.format.decodeFromString<ExecutionEvent>(it.eventJson) }
        assertEquals("The acknowledged/verified prefix must survive process death",prefix,retained.take(prefix.size))
        delay(5000)
        assertFalse(app.runtime.busy.value)
        assertTrue(app.runtime.events.value.isEmpty())
        assertNull(app.runtime.approval.value)
        assertNull(app.runtime.lastResult.value)
        assertFalse(withContext(Dispatchers.Main) { app.runtime.isOwnAudioPlaying })
        evidence("PASS pid=${Process.myPid()} journal=UNKNOWN definitionRestored=true snapshotRestored=true retainedEvents=${retained.size} prefixPreserved=true pendingDraftRestored=true noReplayObservedMs=5000 audioPlaying=false")
        withContext(Dispatchers.IO) { app.pendingRequests.clearIf(prefs.getString("pending",null)!!);prefs.edit().clear().commit() }
    }
    private fun evidence(message:String) {
        Log.i("RoutineRecoveryEvidence",message)
        InstrumentationRegistry.getInstrumentation().sendStatus(2,Bundle().apply { putString("stream",message+"\n") })
    }
}
