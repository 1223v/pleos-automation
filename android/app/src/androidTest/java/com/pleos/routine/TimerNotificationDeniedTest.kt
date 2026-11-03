package com.pleos.routine

import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Run separately with POST_NOTIFICATIONS denied, then restore the prior permission state. */
@RunWith(AndroidJUnit4::class)
class TimerNotificationDeniedTest {
    @Test fun deniedNotificationsKeepRealCompletionAndInAppStatus():Unit=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        assumeTrue(!app.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        assumeTrue(!app.runtime.busy.value && withContext(Dispatchers.IO) { app.database.routines().enabled().isEmpty() })
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        val blank=WorkflowExamples.blank("notification-denied-${UUID.randomUUID()}")
        val graph=GraphEditing.insert(blank,blank.edges.single().id,NodeKind.TIMER)
        val w=graph.copy(nodes=graph.nodes.map { if(it.kind==NodeKind.TIMER)it.copy(durationSeconds=1) else it })
        try {
            withContext(Dispatchers.Main) { app.runtime.requestRun(w) }
            withTimeout(15_000) { while(app.runtime.lastResult.value?.workflowId!=w.id || app.runtime.busy.value)delay(50) }
            assertEquals(RunStatus.COMPLETED,app.runtime.lastResult.value?.status)
            assertTrue(app.runtime.timers.value.single().completed)
            assertTrue(app.runtime.notice.value.contains("알림 권한"))
        } finally { withContext(Dispatchers.Main) { app.runtime.cancel() };scenario.close() }
    }
}
