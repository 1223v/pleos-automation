package com.pleos.routine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ConnectionDraftTest {
    @Test fun installedAppListContainsOnlyResolvableTargets() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val apps=InstalledApps.discover(context)
        assertTrue("Official AVD is expected to have launchable apps",apps.isNotEmpty())
        assertEquals(apps.size,apps.map { it.packageName }.distinct().size)
        apps.forEach { app ->
            assertNotEquals(context.packageName,app.packageName)
            assertNotNull(context.packageManager.getLaunchIntentForPackage(app.packageName))
        }
        Log.i("RoutineEvidence","INSTALLED_APPS count=${apps.size} allResolvable=true noAppLaunched=true")
    }
    @Test fun pendingDraftSurvivesStoreReopenButControlIsNeverPersisted() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="pending-request-test-"+UUID.randomUUID()
        val store=PendingRequestStore(context,name)
        val first=PendingRoutineRequest(action="EDIT_ROUTINE",text="열선 기준을 8도로 바꿔줘",routineId="commute")
        try {
            assertTrue(store.save(first))
            assertEquals(first,PendingRequestStore(context,name).read())
            val second=PendingRoutineRequest(action="CREATE_ROUTINE",text="새 초안을 만들어줘")
            assertTrue(store.save(second))
            assertFalse(store.clearIf(first.id))
            assertEquals(second,store.read())
            try { store.save(PendingRoutineRequest(action="CONTROL_ROUTINE",text="실행"));fail("Control must not be persisted") } catch(_:IllegalArgumentException) { }
            assertEquals(second,store.read())
            assertTrue(store.clearIf(second.id))
            assertNull(store.read())
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
        Log.i("RoutineEvidence","PENDING_DRAFT reopened=true staleClearRejected=true controlRejected=true")
    }
}
