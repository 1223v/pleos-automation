package com.pleos.routine

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class JournalMigrationTest {
    @Test fun legacyDataAndInterruptedEventsSurviveMigrationAndRecovery()=runBlocking(Dispatchers.IO) {
      for(version in 1..2) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="migration-${UUID.randomUUID()}.db"
        val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val legacy=WorkflowExamples.blank("legacy")
        val schema=Json.parseToJsonElement(instrumentation.context.assets.open("com.pleos.routine.data.RoutineDatabase/$version.json").bufferedReader().use { it.readText() }).jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            schema.getValue("entities").jsonArray.forEach { value -> val e=value.jsonObject;old.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",e.getValue("tableName").jsonPrimitive.content)) }
            schema.getValue("setupQueries").jsonArray.forEach { old.execSQL(it.jsonPrimitive.content) }
            old.execSQL("INSERT INTO routines VALUES ('legacy','definition',1,'tested',7,123)")
            old.execSQL("INSERT INTO runs (id,routineId,result,state,simulation,createdAt) VALUES ('past','legacy',NULL,'RUNNING',0,456)")
            old.execSQL("INSERT INTO drafts VALUES ('working-request','saved text')")
            if(version==2) {
                old.execSQL("UPDATE runs SET definition=?,revision=?,semanticHash=? WHERE id='past'",arrayOf(WorkflowJson.encode(legacy),legacy.revision,WorkflowJson.semanticHash(legacy)))
                old.execSQL("INSERT INTO run_events VALUES ('past',1,'durable legacy event')")
            }
            old.version=version
        }
        var db=Room.databaseBuilder(context,RoutineDatabase::class.java,name).addMigrations(RoutineDatabase.MIGRATION_1_2,RoutineDatabase.MIGRATION_2_3).build()
        try {
            val dao=db.routines();val old=dao.run("past")!!
            assertEquals(456L,old.createdAt);assertNull(old.startCause)
            if(version==1) { assertNull(old.definition);assertNull(old.revision);assertNull(old.semanticHash) }
            else { assertEquals(legacy,RunHistory.definition(old));assertEquals("durable legacy event",dao.events("past").single().eventJson) }
            assertTrue(dao.routine("legacy")!!.enabled);assertEquals(7,dao.routine("legacy")!!.priority)
            assertEquals("saved text",dao.workingRequest()!!.definition)
            val w=WorkflowExamples.blank("new");val journal=RunJournalRepository(db)
            journal.begin(w,"interrupted",RunStartCause.ACTUAL_TEST)
            val event=ExecutionEvent("interrupted","action",ActionStatus.ACKNOWLEDGED,"response only",20,sequence=1,observedValue="raw")
            journal.append(event)
            val created=dao.run("interrupted")!!.createdAt
            db.close()
            db=Room.databaseBuilder(context,RoutineDatabase::class.java,name).addMigrations(RoutineDatabase.MIGRATION_1_2,RoutineDatabase.MIGRATION_2_3).build()
            RunJournalRepository(db).recover()
            val recovered=db.routines().run("interrupted")!!
            assertEquals("UNKNOWN",recovered.state);assertEquals(created,recovered.createdAt)
            assertEquals("ACTUAL_TEST",recovered.startCause)
            assertEquals(w.revision,recovered.revision);assertEquals(WorkflowJson.encode(w),recovered.definition)
            assertEquals("UNKNOWN",db.routines().run("past")!!.state)
            assertEquals(event,WorkflowJson.format.decodeFromString<ExecutionEvent>(db.routines().events("interrupted").single().eventJson))
            assertTrue(runCatching { RunJournalRepository(db).append(event.copy(sequence=2)) }.isFailure)
        } finally { db.close();context.deleteDatabase(name) }
      }
    }

    @Test fun journalRejectsDuplicateSequenceAndMismatchedTerminal()=runBlocking(Dispatchers.IO) {
        val db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,RoutineDatabase::class.java).build()
        try {
            val w=WorkflowExamples.blank("w");val j=RunJournalRepository(db);j.begin(w,"r")
            val e=ExecutionEvent("r",null,ActionStatus.VERIFIED,"done",1,sequence=1)
            j.append(e)
            assertTrue(runCatching { j.append(e) }.isFailure)
            assertTrue(runCatching { j.append(e.copy(runId="other",sequence=2)) }.isFailure)
            val r=RunResult("r",w.id,w.revision,WorkflowJson.semanticHash(w),false,RunStatus.COMPLETED,listOf(e),1)
            assertTrue(runCatching { j.finish(r.copy(revision=99)) }.isFailure)
            val before=db.routines().run("r")!!;j.finish(r)
            assertEquals(before.createdAt,db.routines().run("r")!!.createdAt)
            j.recover();assertEquals("COMPLETED",db.routines().run("r")!!.state)
            assertTrue(runCatching { j.finish(r.copy(status=RunStatus.UNKNOWN)) }.isFailure)
        } finally { db.close() }
    }
}
