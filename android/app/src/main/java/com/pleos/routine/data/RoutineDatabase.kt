package com.pleos.routine.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="routines")
data class RoutineRecord(@PrimaryKey val id: String, val definition: String, val enabled: Boolean=false, val testedHash: String?=null, val priority: Int=0, val updatedAt: Long=System.currentTimeMillis())
@Entity(tableName="runs")
data class RunRecord(@PrimaryKey val id: String, val routineId: String, val result: String?, val state: String, val simulation: Boolean, val createdAt: Long=System.currentTimeMillis(),val definition:String?=null,val revision:Int?=null,val semanticHash:String?=null,val startCause:String?=null)
@Entity(tableName="run_events",primaryKeys=["runId","sequence"])
data class RunEventRecord(val runId:String,val sequence:Long,val eventJson:String)
@Entity(tableName="drafts")
data class DraftRecord(@PrimaryKey val id: String, val definition: String)

@Dao interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY updatedAt DESC") fun observeRoutines(): Flow<List<RoutineRecord>>
    @Query("SELECT * FROM routines WHERE enabled = 1 ORDER BY priority DESC") suspend fun enabled(): List<RoutineRecord>
    @Query("SELECT * FROM routines WHERE id = :id") suspend fun routine(id:String): RoutineRecord?
    @Upsert suspend fun put(record: RoutineRecord)
    @Query("UPDATE routines SET enabled = :enabled WHERE id = :id") suspend fun enable(id:String,enabled:Boolean)
    @Query("SELECT * FROM runs ORDER BY createdAt DESC LIMIT 100") fun observeRuns(): Flow<List<RunRecord>>
    @Upsert fun putRun(record:RunRecord)
    @Query("SELECT * FROM runs WHERE id = :id") suspend fun run(id:String):RunRecord?
    @Insert suspend fun insertRun(record:RunRecord)
    @Insert suspend fun insertEvent(record:RunEventRecord)
    @Query("SELECT * FROM run_events WHERE runId = :id ORDER BY sequence") suspend fun events(id:String):List<RunEventRecord>
    @Query("SELECT * FROM run_events WHERE runId = :id ORDER BY sequence") fun observeEvents(id:String):Flow<List<RunEventRecord>>
    @Query("SELECT COALESCE(MAX(sequence),0) FROM run_events WHERE runId = :id") suspend fun lastSequence(id:String):Long
    @Query("UPDATE runs SET state = 'UNKNOWN' WHERE id = :id AND state = 'RUNNING'") suspend fun markUnknown(id:String)
    @Query("UPDATE runs SET state = 'UNKNOWN' WHERE state = 'RUNNING'") suspend fun recoverInterrupted()
    @Upsert suspend fun draft(record:DraftRecord)
    @Query("SELECT * FROM drafts WHERE id = 'working'") suspend fun working():DraftRecord?
    @Query("SELECT * FROM drafts WHERE id = 'working-request'") suspend fun workingRequest():DraftRecord?
    @Query("SELECT * FROM drafts WHERE id = 'working-origin'") suspend fun workingOrigin():DraftRecord?
    @Query("SELECT * FROM drafts WHERE id = 'working-clarification'") suspend fun workingClarification():DraftRecord?
    @Query("SELECT * FROM drafts WHERE id = 'working-gleo-review'") suspend fun workingGleoReview():DraftRecord?
}
@Database(entities=[RoutineRecord::class,RunRecord::class,DraftRecord::class,RunEventRecord::class],version=3,exportSchema=true)
abstract class RoutineDatabase:RoomDatabase() {
    abstract fun routines():RoutineDao
    companion object {
        val MIGRATION_2_3=object:Migration(2,3) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE runs ADD COLUMN startCause TEXT")
            }
        }
        val MIGRATION_1_2=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE runs ADD COLUMN definition TEXT")
                db.execSQL("ALTER TABLE runs ADD COLUMN revision INTEGER")
                db.execSQL("ALTER TABLE runs ADD COLUMN semanticHash TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS run_events (runId TEXT NOT NULL, sequence INTEGER NOT NULL, eventJson TEXT NOT NULL, PRIMARY KEY(runId, sequence))")
            }
        }
    }
}
