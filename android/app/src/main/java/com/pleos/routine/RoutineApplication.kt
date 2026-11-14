package com.pleos.routine

import android.app.Application
import androidx.room.Room
import com.pleos.routine.data.*
import com.pleos.routine.execution.RoutineRuntime
import com.pleos.routine.vehicle.VehiclePlatform

class RoutineApplication:Application() {
    val database by lazy { Room.databaseBuilder(this,RoutineDatabase::class.java,"routines.db").addMigrations(RoutineDatabase.MIGRATION_1_2,RoutineDatabase.MIGRATION_2_3).build() }
    val planner by lazy { PlannerClient(this) }
    val pendingRequests by lazy { PendingRequestStore(this) }
    val gleoReviews by lazy { GleoReviews(pendingRequests::read,pendingRequests::save,pendingRequests::clearIf,::replyToGleo) }
    val platform by lazy { VehiclePlatform(this) }
    val runtime by lazy { RoutineRuntime(this) }
    override fun onCreate() { super.onCreate(); platform.start(); runtime.recover() }
}
