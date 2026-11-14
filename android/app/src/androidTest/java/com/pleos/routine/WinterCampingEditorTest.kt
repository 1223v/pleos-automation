package com.pleos.routine

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.pleos.routine.core.*
import com.pleos.routine.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread

class WinterCampingEditorTest {
    @get:Rule val compose=createEmptyComposeRule()
    private fun <T> io(block:suspend()->T):T=runBlocking(Dispatchers.IO) { block() }
    private fun shown(text:String) {
        compose.waitUntil(20000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text:String)=compose.onNodeWithText(text).performScrollTo().performClick()

    @Test fun clarificationSelectedLightReviewAndInactiveSaveThroughLocalHttp() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val app=instrument.targetContext.applicationContext as RoutineApplication
        val dao=app.database.routines()
        check(!app.runtime.busy.value && io { app.pendingRequests.read()==null && dao.enabled().isEmpty() })
        val original="플레오스, 겨울 차박 루틴을 만들고 싶어. 바깥이 5도 이하일 때만 공조를 23도로 맞춰 줘. 매트리스를 놓게 의자를 젖히고 조명은 살짝 어둡게 해 줘."
        val question="어느 좌석의 등받이를 젖힐까요?"
        val reply="앞줄 왼쪽 좌석"
        val adjustment="처음엔 그대로 두고, 10분 뒤에 20%로 낮춰 줘."
        val fixture=app.assets.open("fixtures/winter-camping.json").bufferedReader().use { WorkflowJson.decode(it.readText()) }
        val partial=fixture.copy(nodes=fixture.nodes.map { if(it.id=="seat")
            it.copy(label="차박 등받이",action=it.action!!.copy(area="")) else it })
        val prefs=app.getSharedPreferences("connection",0)
        val endpoint=prefs.getString("endpoint",null);val token=prefs.getString("token",null)
        val initial=WorkflowExamples.blank(UUID.randomUUID().toString())
        val activeDrafts=listOf("working","working-request","working-origin","working-clarification","working-gleo-review")
        val originalDrafts=io { app.database.withTransaction { listOf(dao.working(),dao.workingRequest(),dao.workingOrigin(),
            dao.workingClarification(),dao.workingGleoReview()) } }
        fun actualRuns()=io { app.database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM runs WHERE simulation=0"
        ).use { cursor->check(cursor.moveToFirst());cursor.getLong(0) } }
        val beforeActualRuns=actualRuns()
        var createdId:String?=null
        val responses=mutableListOf<JsonObject>()
        fun response(status:String,explanation:String,workflow:WorkflowDefinition?=null,patch:WorkflowPatch?=null)=buildJsonObject {
            put("status",status);put("explanation",explanation)
            put("questions",if(status=="needs_clarification")JsonArray(listOf(JsonPrimitive(question))) else JsonArray(emptyList()))
            put("workflow",workflow?.let { WorkflowJson.format.encodeToJsonElement(it) } ?: JsonNull)
            put("patch",patch?.let { WorkflowJson.format.encodeToJsonElement(it) } ?: JsonNull)
        }
        val server=ServerSocket(0,3,InetAddress.getByName("127.0.0.1"))
        var workerFailure:Throwable?=null
        val worker=thread(isDaemon=true) {
            try {
                repeat(3) { index-> server.accept().use { socket->
                    socket.soTimeout=15000
                    val stream=socket.getInputStream()
                    val header=StringBuilder()
                    while(!header.endsWith("\r\n\r\n")) { val b=stream.read();check(b>=0 && header.length<8192);header.append(b.toChar()) }
                    check(header.startsWith("POST /v1/routine-drafts "))
                    val length=Regex("(?im)^content-length: *([0-9]+)").find(header)!!.groupValues[1].toInt()
                    val bytes=ByteArray(length);java.io.DataInputStream(stream).readFully(bytes)
                    val request=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                    responses+=request
                    val payload=when(index) {
                        0->response("needs_clarification","공조 23°C와 첫 조명 30%를 제안합니다. 좌석을 선택해 주세요.",partial)
                        1->response("draft","앞줄 왼쪽 등받이를 선택했습니다.",fixture)
                        else->{
                            val base=WorkflowJson.format.decodeFromJsonElement<WorkflowDefinition>(request.getValue("baseWorkflow"))
                            val light=base.nodes.single { it.id=="light30" }
                            val darker=light.copy(id="light20",label="조명 20%",position=NodePosition(1460f,325f),action=light.action!!.copy(value=20.0))
                            val wait=WorkflowNode("wait600",NodeKind.WAIT,"10분 대기",NodePosition(1210f,325f),durationSeconds=600)
                            val tail=base.edges.single { it.source==light.id }
                            fun connect(id:String,from:String,to:String)=PatchOperation(PatchType.CONNECT,edge=WorkflowEdge(id,from,to))
                            val patch=WorkflowPatch(base.revision,listOf(PatchOperation(PatchType.DISCONNECT,edgeId=tail.id),
                                PatchOperation(PatchType.ADD_NODE,node=wait),PatchOperation(PatchType.ADD_NODE,node=darker),
                                connect("new1",light.id,wait.id),connect("new2",wait.id,darker.id),connect("new3",darker.id,tail.target)))
                            response("draft","첫 밝기 30%를 유지하고 10분 뒤에 20%로 낮춥니다.",patch=patch)
                        }
                    }.toString().toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(payload);flush()
                    }
                } }
            } catch(e:Throwable) { workerFailure=e }
        }
        try {
            io { app.database.withTransaction {
                activeDrafts.forEach { dao.draft(DraftRecord(it,when(it) { "working"->WorkflowJson.encode(initial);else->"" })) }
            } }
            app.planner.endpoint="http://127.0.0.1:${server.localPort}"
            app.planner.token="local-test-token-".repeat(3)
            ActivityScenario.launch(MainActivity::class.java).use { scenario->
                shown("초안 저장됨");compose.onNodeWithText("01 생성").performClick()
                compose.onNodeWithText("나의 요청").performTextReplacement(original)
                click("AI로 초안 만들기");shown("초안을 완성할 질문")
                compose.onNodeWithText("초안에 적용").assertDoesNotExist()
                compose.onNodeWithText(question).performScrollTo().performTextReplacement(reply)
                compose.waitUntil(10000) { io { dao.workingClarification()?.definition?.contains(reply)==true } }
                assertEquals(initial,io { WorkflowJson.decode(dao.working()!!.definition) })
                scenario.recreate();shown("초안을 완성할 질문")
                compose.onNodeWithText(reply).assertExists()
                compose.onNodeWithText("답변 보내기").performClick();shown("AI 변경 제안 확인")
                compose.onNodeWithText("초안에 적용").performClick()
                compose.waitUntil(10000) { io { dao.working()?.definition?.let(WorkflowJson::decode)?.title==fixture.title } }
                createdId=io { WorkflowJson.decode(dao.working()!!.definition).id }
                compose.onNodeWithText("03 수정").performClick()
                repeat(4) { click("−") }
                click("조명 30%");click("AI에게 수정 요청")
                compose.onNodeWithText("이 노드에서 바꿀 내용").performTextReplacement(adjustment)
                compose.onNodeWithText("수정안 보기").performClick();shown("AI 변경 제안 확인")
                compose.onNodeWithText("수정 후 · 조명 30% → 10분 대기 → 조명 20% → 완료").assertExists()
                compose.waitForIdle()
                val picture=instrument.uiAutomation.takeScreenshot()
                try {
                    File(app.getExternalFilesDir(null),"winter-selected-light-review.png").outputStream().use {
                        check(picture.compress(Bitmap.CompressFormat.PNG,100,it))
                    }
                } finally { picture.recycle() }
                compose.onNodeWithText("초안에 적용").performClick()
                compose.onNodeWithText("04 테스트").performClick();click("이 상황으로 테스트")
                compose.waitUntil(15000) { compose.onAllNodesWithText("COMPLETED",substring=true).fetchSemanticsNodes().isNotEmpty() }
                click("검토한 루틴 저장");compose.onNodeWithText("계속 편집").performClick()
                assertNull(io { dao.routine(createdId!!) })
                click("검토한 루틴 저장");compose.onNodeWithText("저장").performClick()
                compose.waitUntil(10000) { io { dao.routine(createdId!!)!=null } }
                val saved=io { dao.routine(createdId!!)!! }
                val actual=WorkflowJson.decode(saved.definition)
                assertFalse(saved.enabled);assertEquals(WorkflowJson.semanticHash(actual),saved.testedHash)
                assertEquals(fixture.nodes.filter { it.id!="light30" },actual.nodes.filter { it.id in fixture.nodes.map { n->n.id } && it.id!="light30" })
                assertFalse(app.runtime.busy.value)
                assertEquals(beforeActualRuns,actualRuns())
                compose.onNodeWithText("01 생성").performClick();click("빈 흐름")
                compose.waitUntil(10000) { io { WorkflowJson.decode(dao.working()!!.definition).id!=createdId } }
                compose.onNodeWithText("내 루틴").performClick()
                val open=hasTestTag("open-routine-${createdId!!}")
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(open)
                compose.onNode(open).performClick();shown(fixture.title)
                compose.waitUntil(10000) { io { WorkflowJson.decode(dao.working()!!.definition)==actual } }
            }
            worker.join(3000);workerFailure?.let { throw it }
            assertEquals(3,responses.size)
            assertEquals(original,responses[0].getValue("requestText").jsonPrimitive.content)
            assertEquals(reply,responses[1].getValue("clarification").jsonObject.getValue("answers").jsonArray.single().jsonPrimitive.content)
            assertEquals(original,responses[1].getValue("requestText").jsonPrimitive.content)
            assertEquals(adjustment,responses[2].getValue("requestText").jsonPrimitive.content)
            assertEquals("light30",responses[2].getValue("selectedNodeId").jsonPrimitive.content)
        } finally {
            server.close();worker.join(3000)
            prefs.edit().putString("endpoint",endpoint).putString("token",token).commit()
            io { app.database.withTransaction {
                val db=app.database.openHelper.writableDatabase
                activeDrafts.forEach { db.execSQL("DELETE FROM drafts WHERE id=?",arrayOf(it)) }
                originalDrafts.filterNotNull().forEach { dao.draft(it) }
                createdId?.let {
                    db.execSQL("DELETE FROM runs WHERE routineId=?",arrayOf(it))
                    db.execSQL("DELETE FROM routines WHERE id=?",arrayOf(it))
                }
            } }
        }
    }
}
