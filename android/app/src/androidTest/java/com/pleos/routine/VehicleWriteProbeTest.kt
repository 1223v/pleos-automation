package com.pleos.routine

import android.os.Build
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pleos.routine.core.*
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicitly selected emulator-only tests. HVAC power preparation is not an app action. */
@RunWith(AndroidJUnit4::class)
class VehicleWriteProbeTest {
    private suspend fun platform():com.pleos.routine.vehicle.VehiclePlatform {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vehicleWrites")=="emulator-only")
        assumeTrue(Build.HARDWARE=="ranchu" && (Build.FINGERPRINT.startsWith("Pleos/crp_avd/") || Build.FINGERPRINT.contains("generic")))
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        delay(3000)
        assertTrue("Actual P, zero speed and UX restriction state required",app.platform.snapshot().parkedAndStationary())
        app.platform.refreshCapabilities()
        return app.platform
    }
    private suspend fun <T> call(block:((T)->Unit,(Exception)->Unit)->Unit):T=withTimeout(4000) {
        suspendCancellableCoroutine { c -> block({if(c.isActive)c.resume(it)},{if(c.isActive)c.resumeWithException(it)}) }
    }
    private suspend fun verify(p:com.pleos.routine.vehicle.VehiclePlatform,a:ActionSpec) {
        val before=runCatching { p.observedValue(a) }.getOrNull()
        val r=withContext(Dispatchers.Main) { p.execute(a) { Log.i("RoutineEvidence","WRITE ${a.type} ${it.status} ${it.message}") } }
        Log.i("RoutineEvidence","WRITE ${a.type} area=${a.area} before=$before requested=${a.value ?: a.text} ${r.status} observed=${r.observedValue} ${r.message}")
        assertEquals("${a.type}: ${r.message}",ActionStatus.VERIFIED,r.status)
    }
    @Test fun observedHvacWithExplicitPowerPreparation()=runBlocking {
        val p=platform()
        val sdk=Vehicle(InstrumentationRegistry.getInstrumentation().targetContext).also { it.initialize() }
        delay(1000)
        val hvac=sdk.getHVAC()
        val areas=call<List<SeatArea>> { ok,fail -> hvac.checkHvacPowerCapabilities({ c,areas -> if(c.accessType==AccessType.READ_WRITE)ok(areas) else fail(IllegalStateException("HVAC power not writable")) },fail) }
        for(area in areas) {
            val before=call<Boolean?> { ok,fail -> hvac.getHvacPowerState(area,ok,fail) }
            Log.i("RoutineEvidence","PREPARE HVAC_POWER area=${area.values.joinToString { it.name }} before=$before target=true")
            assertTrue(p.snapshot().parkedAndStationary())
            call<Unit> { ok,fail -> hvac.setHvacPower(area,true,{ actual,_ -> if(actual==area)ok(Unit) else fail(IllegalStateException("wrong area")) },fail) }
            // A callback only acknowledges the request; wait for a fresh observation.
            val applied=withTimeoutOrNull(3000) { while(call<Boolean?> { ok,fail -> hvac.getHvacPowerState(area,ok,fail) }!=true)delay(150);true }
            Log.i("RoutineEvidence","PREPARE HVAC_POWER observedOn=$applied")
            assertEquals(true,applied)
        }
        delay(500)
        verify(p,ActionSpec(ActionType.SET_HVAC_TARGET,area="ROW_1_LEFT",value=22.0))
        verify(p,ActionSpec(ActionType.SET_HVAC_TARGET,area="ROW_1_LEFT",value=23.0))
    }
    @Test fun observedAmbientBrightness()=runBlocking {
        val p=platform();val area=p.capabilities.value.single { it.type==ActionType.SET_AMBIENT_BRIGHTNESS }.areas.single()
        verify(p,ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS,area=area,value=20.0))
        verify(p,ActionSpec(ActionType.SET_AMBIENT_BRIGHTNESS,area=area,value=30.0))
    }
    @Test fun observedAmbientColor()=runBlocking {
        val p=platform();val area=p.capabilities.value.single { it.type==ActionType.SET_AMBIENT_COLOR }.areas.single()
        verify(p,ActionSpec(ActionType.SET_AMBIENT_COLOR,area=area,text="#FFDB854B"))
        verify(p,ActionSpec(ActionType.SET_AMBIENT_COLOR,area=area,text="#FF4B85DB"))
    }
}
