package com.pleos.routine.vehicle

import com.pleos.routine.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeatMotionCoordinatorTest {
    private val policy=SeatMotionPolicy(0,100,200,300,50,"AVD-evidence",setOf(MoveDirection.FORWARD,MoveDirection.BACKWARD),forwardIncreases=true)
    private class Port(val clock:()->Long):SeatMotionPort {
        var gen=1L;var journal=false;var safe=true;var value:Int?=50;var writes=mutableListOf<Pair<SeatCommand,Long>>()
        var moveAck=CompletableDeferred<MotionAck>();var clearCount=0;var onPersist:()->Unit={};var onQuery:suspend()->Unit={}
        var acknowledge=true;var stopAck=true;var stable=true
        var delayedStop:Deferred<MotionAck>?=null
        override fun generation()=gen
        override fun pending()=journal
        override suspend fun persist():Boolean { journal=true;onPersist();return true }
        override suspend fun clear():Boolean { clearCount++;journal=false;return true }
        override suspend fun safe()=safe
        override suspend fun position():Int? { onQuery();return value }
        override fun dispatch(command:SeatCommand):Deferred<MotionAck> {
            writes+=command to clock()
            if(command==SeatCommand.STOP) { if(!stable)value=101;return delayedStop ?: CompletableDeferred(MotionAck(stopAck,clock(),gen)) }
            value=if(command==SeatCommand.FORWARD)51 else 49
            if(acknowledge)moveAck.complete(MotionAck(true,clock(),gen))
            return moveAck
        }
    }
    @Test fun boundedBothDirectionsObserveMovementAndStopThenClear()=runTest {
        for(direction in MoveDirection.entries) {
            val p=Port { testScheduler.currentTime };val c=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler))
            val r=c.move(p,policy,direction) {}
            assertEquals(ActionStatus.VERIFIED,r.status);assertEquals(SeatCommand.STOP,p.writes.last().first)
            assertTrue(p.writes.last().second-p.writes.first().second<=300);assertEquals(1,p.clearCount)
        }
    }
    @Test fun finalSafetyCheckAfterJournalPreventsMove()=runTest {
        val p=Port { testScheduler.currentTime };p.onPersist={p.safe=false}
        val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
        assertEquals(ActionStatus.BLOCKED,r.status);assertFalse(p.writes.any { it.first!=SeatCommand.STOP })
    }
    @Test fun endpointAndUncalibratedDirectionBlockBeforeJournal()=runTest {
        for(value in listOf(100,null,101)) {
            val p=Port { testScheduler.currentTime };p.value=value
            val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
            assertEquals(ActionStatus.BLOCKED,r.status);assertFalse(p.journal);assertTrue(p.writes.isEmpty())
        }
        val p=Port { testScheduler.currentTime }
        assertEquals(ActionStatus.BLOCKED,SeatMotionCoordinator({testScheduler.currentTime}).move(p,policy.copy(directions=emptySet()),MoveDirection.FORWARD) {}.status)
    }
    @Test fun missingOrLateMoveAckRetainsRecoveryJournalDespiteStableStop()=runTest {
        for(late in listOf(false,true)) {
            val p=Port { testScheduler.currentTime };p.acknowledge=false
            val task=launch { if(late) { delay(250);p.moveAck.complete(MotionAck(true,testScheduler.currentTime,p.gen)) } }
            val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
            task.join();assertEquals(ActionStatus.UNKNOWN,r.status);assertTrue(p.journal);assertEquals(0,p.clearCount)
            assertTrue(p.writes.any { it.first==SeatCommand.STOP && it.second-p.writes.first().second<=300 })
        }
    }
    @Test fun blockedPositionQueryCannotDelayWatchdogStop()=runTest {
        val p=Port { testScheduler.currentTime };p.onQuery={if(p.writes.isNotEmpty())delay(1000)}
        val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
        assertTrue(p.writes.any { it.first==SeatCommand.STOP && it.second-p.writes.first().second<=300 });assertEquals(ActionStatus.UNKNOWN,r.status)
    }
    @Test fun missingPositionDuringMoveStopsImmediatelyAndIsUnknown()=runTest {
        val p=Port { testScheduler.currentTime };p.onQuery={if(p.writes.isNotEmpty())p.value=null}
        val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
        assertEquals(ActionStatus.UNKNOWN,r.status);assertTrue(p.journal);assertTrue(p.writes.last().second<200)
    }
    @Test fun reconnectInvalidatesAckAndDoesNotClearJournal()=runTest {
        val p=Port { testScheduler.currentTime };p.onQuery={if(p.writes.isNotEmpty())p.gen=2}
        val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
        assertEquals(ActionStatus.UNKNOWN,r.status);assertTrue(p.journal)
    }
    @Test fun cancellationSendsStopEvenWhenParkingLost()=runTest {
        val p=Port { testScheduler.currentTime };val c=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler))
        val job=launch { c.move(p,policy,MoveDirection.FORWARD) {} }
        runCurrent();p.safe=false;job.cancelAndJoin()
        assertTrue(p.writes.any { it.first==SeatCommand.STOP })
    }
    @Test fun oldPendingMoveNeverReplays()=runTest {
        val p=Port { testScheduler.currentTime };p.journal=true
        assertEquals(ActionStatus.BLOCKED,SeatMotionCoordinator({testScheduler.currentTime}).move(p,policy,MoveDirection.FORWARD) {}.status)
        assertTrue(p.writes.isEmpty());assertTrue(p.journal)
    }
    @Test fun reachingDirectionalBoundaryStopsBeforeNextSample()=runTest {
        for(direction in MoveDirection.entries) {
            val p=Port { testScheduler.currentTime }
            p.value=if(direction==MoveDirection.FORWARD)99 else 1
            p.onQuery={if(p.writes.isNotEmpty())p.value=if(direction==MoveDirection.FORWARD)100 else 0}
            val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,direction) {}
            assertEquals(ActionStatus.VERIFIED,r.status)
            assertEquals(50L,p.writes.last().second-p.writes.first().second)
        }
    }
    @Test fun movementAgainstCalibratedDirectionStopsAndIsUnknown()=runTest {
        val p=Port { testScheduler.currentTime };p.onQuery={if(p.writes.isNotEmpty())p.value=49}
        val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
        assertEquals(ActionStatus.UNKNOWN,r.status);assertEquals(50L,p.writes.last().second-p.writes.first().second)
    }
    @Test fun lateStopAckNeverClearsJournalDespiteStablePosition()=runTest {
        val p=Port { testScheduler.currentTime };val reply=CompletableDeferred<MotionAck>();p.delayedStop=reply
        launch { delay(1000);reply.complete(MotionAck(true,testScheduler.currentTime,p.gen)) }
        val r=SeatMotionCoordinator({testScheduler.currentTime},StandardTestDispatcher(testScheduler)).move(p,policy,MoveDirection.FORWARD) {}
        assertEquals(ActionStatus.UNKNOWN,r.status);assertTrue(p.journal);assertEquals(0,p.clearCount)
    }
}
