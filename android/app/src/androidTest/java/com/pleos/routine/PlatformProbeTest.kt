package com.pleos.routine

import android.util.Log
import android.os.Build
import android.os.Bundle
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
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

@RunWith(AndroidJUnit4::class)
class PlatformProbeTest {
    @Test fun readOnlyHvacAvailability():Unit=runBlocking {
        assumeTrue(Build.HARDWARE=="ranchu" && Build.FINGERPRINT.startsWith("Pleos/crp_avd/"))
        val sdk=Vehicle(InstrumentationRegistry.getInstrumentation().targetContext).also { it.initialize() }
        fun record(message:String) {
            InstrumentationRegistry.getInstrumentation().sendStatus(2,Bundle().apply { putString("stream",message+"\n") })
        }
        try {
            delay(3000)
            val hvac=sdk.getHVAC()
            val areas=read<Map<SeatArea,AreaBounds<Float>>> { ok,fail ->
                hvac.checkHvacTemperatureCapabilities({ c,m,config ->
                    record("HVAC_CAP access=${c.accessType} config=$config")
                    ok(m)
                },fail)
            }
            assertTrue("SDK must expose at least one temperature area",areas.isNotEmpty())
            var available=0
            for((area,bounds) in areas) {
                val result=runCatching { read<Float?> { ok,fail -> hvac.getHvacTemperature(area,TemperatureUnit.CELSIUS,ok,fail) } }
                val value=result.getOrNull()
                record("HVAC_READ area=${area.values.joinToString { it.name }} bounds=${bounds.minValue}..${bounds.maxValue} value=$value error=${result.exceptionOrNull()?.let { it.javaClass.simpleName+":"+it.message?.replace('\n',' ')?.take(240) }}")
                if(value!=null)available++
            }
            record("HVAC_AVAILABILITY readable=$available supported=${areas.size} writes=0")
            assertEquals("Supported areas are not currently readable; inspect HVAC_READ errors",areas.size,available)
        } finally { sdk.release() }
    }
    private suspend fun <T> read(block:((T)->Unit,(Exception)->Unit)->Unit):T=withTimeout(4000) {
        suspendCancellableCoroutine { c -> block({ if(c.isActive)c.resume(it) },{ if(c.isActive)c.resumeWithException(it) }) }
    }
    @Test fun readOnlyCapabilityAndStateProbe()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RoutineApplication
        delay(4000)
        app.platform.refreshCapabilities()
        val x=app.platform.snapshot()
        Log.i("RoutineEvidence","REAL source=${x.source} gear=${x.gear.value} speedMps=${x.speedMps.value} outsideC=${x.outsideTempC.value} uxRestricted=${x.uxRestricted}")
        app.platform.capabilities.value.forEach { Log.i("RoutineEvidence","CAP ${it.type} ${it.access} ${it.areas} ${it.ranges} ${it.message}") }
        assertEquals(InputSource.REAL,x.source)
        assertFalse("Unknown occupancy must not be treated as actual driver identity",x.seatOccupied.valid)
    }
}
