package com.pleos.routine

import android.app.Notification
import android.app.NotificationManager
import androidx.lifecycle.Lifecycle
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

@RunWith(AndroidJUnit4::class)
class TimerNotificationTest {
    @Test fun waitDoesNotPublishTimerCompletion():Unit=assertNoCompletion(NodeKind.WAIT,false)
    @Test fun cancelledTimerDoesNotPublishCompletion():Unit=assertNoCompletion(NodeKind.TIMER,true)
    private fun assertNoCompletion(kind:NodeKind,cancel:Boolean):Unit=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val notifications=app.getSystemService(NotificationManager::class.java)
        assumeTrue(!app.runtime.busy.value && withContext(Dispatchers.IO) { app.database.routines().enabled().isEmpty() })
        val label="알림 제외 ${UUID.randomUUID()}"
        val blank=WorkflowExamples.blank("quiet-${UUID.randomUUID()}")
        val graph=GraphEditing.insert(blank,blank.edges.single().id,kind)
        val timer=graph.nodes.single { it.kind==kind }
        val w=graph.copy(nodes=graph.nodes.map { if(it.id==timer.id)it.copy(label=label,durationSeconds=if(cancel)30 else 1) else it })
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            delay(1500)
            withContext(Dispatchers.Main) { app.runtime.requestRun(w) }
            if(cancel) {
                withTimeout(10_000) { while(app.runtime.timers.value.none { it.nodeId==timer.id && !it.completed })delay(20) }
                withContext(Dispatchers.Main) { app.runtime.cancel() }
            }
            withTimeout(10_000) { while(app.runtime.lastResult.value?.workflowId!=w.id || app.runtime.busy.value)delay(50) }
            assertEquals(if(cancel)RunStatus.CANCELLED else RunStatus.COMPLETED,app.runtime.lastResult.value?.status)
            assertTrue(app.runtime.timers.value.isEmpty())
            assertFalse(notifications.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.contains(label)==true })
        } finally { withContext(Dispatchers.Main) { app.runtime.cancel() };scenario.close() }
    }
    @Test fun realTimerCompletesWithNotificationAfterActivityIsHidden():Unit=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        val notifications=app.getSystemService(NotificationManager::class.java)
        assumeTrue(notifications.areNotificationsEnabled())
        assumeTrue(!app.runtime.busy.value && withContext(Dispatchers.IO) { app.database.routines().enabled().isEmpty() })
        val label="완료 확인 ${UUID.randomUUID()}"
        val blank=WorkflowExamples.blank("timer-notification-${UUID.randomUUID()}")
        val graph=GraphEditing.insert(blank,blank.edges.single().id,NodeKind.TIMER)
        val timer=graph.nodes.single { it.kind==NodeKind.TIMER }
        val w=graph.copy(nodes=graph.nodes.map { if(it.id==timer.id)it.copy(label=label,durationSeconds=2) else it })
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            delay(2000)
            withContext(Dispatchers.Main) { app.runtime.requestRun(w) }
            withTimeout(10_000) { while(app.runtime.timers.value.none { it.nodeId==timer.id && !it.completed })delay(20) }
            assertTrue(app.runtime.timers.value.single().remainingMillis in 1..2000)
            scenario.moveToState(Lifecycle.State.CREATED)
            withTimeout(10_000) { while(app.runtime.lastResult.value?.workflowId!=w.id || app.runtime.busy.value)delay(50) }
            assertEquals(RunStatus.COMPLETED,app.runtime.lastResult.value?.status)
            assertTrue(app.runtime.timers.value.single().completed)
            assertEquals(0L,app.runtime.timers.value.single().remainingMillis)
            assertTrue("Completed TIMER must publish a completion notification while the activity is hidden",
                notifications.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.contains("타이머 완료")==true &&
                    it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.contains(label)==true })
        } finally {
            withContext(Dispatchers.Main) { app.runtime.cancel() }
            scenario.close()
            notifications.activeNotifications.filter { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.contains(label)==true }
                .forEach { notifications.cancel(it.tag,it.id) }
        }
    }
}
