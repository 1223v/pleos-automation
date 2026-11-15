package com.pleos.routine

import android.app.Application
import androidx.room.Room
import com.pleos.routine.data.*
import com.pleos.routine.execution.RoutineRuntime
import com.pleos.routine.vehicle.VehiclePlatform
import com.pleos.routine.voice.PleosSpeechPort
import com.pleos.routine.voice.VoiceInput
import kotlinx.coroutines.flow.MutableSharedFlow

class RoutineApplication:Application() {
    val database by lazy { Room.databaseBuilder(this,RoutineDatabase::class.java,"routines.db").addMigrations(RoutineDatabase.MIGRATION_1_2,RoutineDatabase.MIGRATION_2_3).build() }
    val planner by lazy { PlannerClient(this) }
    val pendingRequests by lazy { PendingRequestStore(this) }
    val gleoReviews by lazy { GleoReviews(pendingRequests::read,pendingRequests::save,pendingRequests::clearIf,::replyToGleo) }
    val platform by lazy { VehiclePlatform(this) }
    val runtime by lazy { RoutineRuntime(this) }
    /** Heard text per input field. Collected only by the open screen; never sent to the AI or the vehicle by itself. */
    val voiceResults=MutableSharedFlow<Pair<String,String>>(extraBufferCapacity=8)
    val voice by lazy { VoiceInput(PleosSpeechPort(this)) { target,text -> voiceResults.tryEmit(target to text) } }
    override fun onCreate() { super.onCreate(); platform.start(); runtime.recover() }
}
