package com.pleos.routine.data

import com.pleos.routine.core.*
import com.pleos.routine.execution.SeatRunAuthorization
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerHttpTransportTest {
    @Test fun explanationRouteSendsAuthenticatedReadOnlyEvidence()=runTest {
        val c=Connection()
        val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { c }
        val evidence=buildJsonObject { put("requestId","explain-123") }
        transport.request("https://example.test",token,"/v1/run-explanations",evidence,false)
        assertEquals(evidence.toString(),c.sent.toString(Charsets.UTF_8.name()))
        assertEquals("Bearer $token",c.getRequestProperty("Authorization"));assertEquals(1,c.posts)
        assertFalse(c.instanceFollowRedirects)
    }
    private val body=buildJsonObject { put("request","local-test") }
    private val token="local-fixture-token-".repeat(2)
    private class Connection:HttpURLConnection(URL("https://example.test/v1/connector-actions")) {
        var posts=0
        var reads=0
        var closes=0
        var status=200
        var response="{}".toByteArray()
        var atSend:()->Unit={}
        var atRead:()->Unit={}
        val sent=ByteArrayOutputStream()
        override fun connect() { connected=true }
        override fun disconnect() { closes++ }
        override fun usingProxy()=false
        override fun getOutputStream():ByteArrayOutputStream { posts++;atSend();return sent }
        override fun getResponseCode():Int { reads++;atRead();return status }
        override fun getInputStream()=response.inputStream()
        override fun getErrorStream()=response.inputStream()
    }
    private fun parked()=ExecutionContext(0,0,InputSource.REAL,
        StateValue("P",0,true,InputSource.REAL),StateValue(0.0,0,true,InputSource.REAL),uxRestricted=false)
    private fun graph()=WorkflowDefinition(id="http-approval",title="HTTP and seat plan",nodes=listOf(
        WorkflowNode("start",NodeKind.START,"start"),
        WorkflowNode("ha",NodeKind.ACTION,"light",action=ActionSpec(ActionType.HA_CALL,targetId="light.test",text="light.turn_on")),
        WorkflowNode("seat",NodeKind.ACTION,"seat",action=ActionSpec(ActionType.MOVE_SEAT_LIMITED,area="ROW_1_LEFT",axis=SeatAxis.FORE_AFT,direction=MoveDirection.BACKWARD)),
        WorkflowNode("end",NodeKind.END,"end")),edges=listOf(
        WorkflowEdge("1","start","ha"),WorkflowEdge("2","ha","seat"),WorkflowEdge("3","seat","end")))

    @Test fun approvedRequestIsSentOnceWithExistingHttpRestrictions()=runTest {
        val c=Connection();var opened=0;var gates=0
        val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { opened++;c }
        assertEquals(JsonObject(emptyMap()),transport.request("https://example.test",token,"/v1/connector-actions",body,false) { gates++ })
        assertEquals(1,opened);assertEquals(1,gates);assertEquals(1,c.posts);assertEquals(1,c.closes)
        assertEquals(body.toString(),c.sent.toString(Charsets.UTF_8.name()))
        assertFalse(c.instanceFollowRedirects);assertEquals(10_000,c.connectTimeout);assertEquals(65_000,c.readTimeout)
    }
    @Test fun connectionChangeDuringHttpPreparationRejectsBeforePost()=runTest {
        val w=graph();var generation=1L;val c=Connection()
        val session=SeatRunAuthorization.Session(w,"run",{generation},{parked()},{"calibrated seat plan"},confirm={ _,_->true })
        assertNull(session.authorize(parked()))
        val request=ActionRequest("run","ha",w.nodes.single { it.id=="ha" }.action!!,parked())
        val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { generation++;c }
        val result=runCatching {
            transport.request("https://example.test",token,"/v1/connector-actions",body,false) {
                session.beforeDispatch(request)?.let { throw ActionDispatchRejected(it) }
            }
        }
        assertEquals("A stale approval must not reach the HTTP output stream",0,c.posts)
        assertEquals(ActionStatus.BLOCKED,(result.exceptionOrNull() as? ActionDispatchRejected)?.result?.status)
        assertEquals(1,c.closes)
    }
    @Test fun cancellationDuringHttpPreparationDoesNotSend()=runTest {
        val c=Connection();lateinit var execution:Job
        val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { execution.cancel();c }
        execution=launch { transport.request("https://example.test",token,"/v1/connector-actions",body,false) }
        execution.join()
        assertTrue(execution.isCancelled)
        assertEquals("Cancellation after IO entry must still prevent POST",0,c.posts)
        assertEquals(1,c.closes)
    }
    @Test fun firstSideEffectUsesStateAfterHttpPreparation()=runTest {
        val w=graph();var state=parked();val c=Connection()
        val session=SeatRunAuthorization.Session(w,"run",{1L},{state},{"calibrated seat plan"},confirm={ _,_->true })
        assertNull(session.authorize(parked()))
        val request=ActionRequest("run","ha",w.nodes.single { it.id=="ha" }.action!!,parked())
        val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { state=state.copy(gear=StateValue("D",0,true,InputSource.REAL));c }
        val result=runCatching {
            transport.request("https://example.test",token,"/v1/connector-actions",body,false) {
                session.beforeDispatch(request)?.let { throw ActionDispatchRejected(it) }
            }
        }
        assertEquals(0,c.posts)
        assertEquals(ActionStatus.BLOCKED,(result.exceptionOrNull() as? ActionDispatchRejected)?.result?.status)
        assertEquals(1,c.closes)
    }
    @Test fun cancellationInsideLastGateCannotSendEvenWhenGateReturnsNormally()=runTest {
        val c=Connection();val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { c }
        val execution=launch {
            transport.request("https://example.test",token,"/v1/connector-actions",body,false) { currentCoroutineContext().cancel() }
        }
        execution.join()
        assertTrue(execution.isCancelled);assertEquals(0,c.posts)
    }
    @Test fun cancellationAfterTransmissionNeverRetriesTheRequest()=runTest {
        val c=Connection();var opened=0;lateinit var execution:Job
        c.atSend={ execution.cancel() }
        val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { opened++;c }
        execution=launch { transport.request("https://example.test",token,"/v1/connector-actions",body,false) }
        execution.join()
        assertTrue(execution.isCancelled);assertEquals(1,c.posts);assertEquals(1,opened);assertEquals(1,c.closes)
    }
    @Test fun uncertainResponseFailureDoesNotReplayAWrite()=runTest {
        val c=Connection();var opened=0;c.atRead={ throw IOException("response lost") }
        val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { opened++;c }
        assertTrue(runCatching { transport.request("https://example.test",token,"/v1/connector-actions",body,false) }.exceptionOrNull() is IOException)
        assertEquals(1,opened);assertEquals(1,c.posts);assertEquals(1,c.closes)
    }
    @Test fun oversizedResponseAndRedirectAreRejectedWithoutRetry()=runTest {
        for(redirect in listOf(false,true)) {
            val c=Connection();var opened=0
            if(redirect) { c.status=302;c.response="{}".toByteArray() } else c.response=ByteArray(262145) { 32 }
            val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { opened++;c }
            assertTrue(runCatching { transport.request("https://example.test",token,"/v1/status",null,false) }.isFailure)
            assertFalse(c.instanceFollowRedirects);assertEquals(1,opened);assertEquals(1,c.reads);assertEquals(1,c.closes)
        }
    }
    @Test fun invalidCredentialsPathOrOriginNeverCreatesAConnection()=runTest {
        var opened=0;val transport=PlannerHttpTransport(StandardTestDispatcher(testScheduler)) { opened++;Connection() }
        for(input in listOf(Triple("http://example.test",token,"/v1/status"),Triple("https://example.test","short","/v1/status"),Triple("https://example.test",token,"/unapproved"))) {
            assertTrue(runCatching { transport.request(input.first,input.second,input.third,null,false) }.isFailure)
        }
        assertEquals(0,opened)
    }
}
