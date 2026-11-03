package com.pleos.routine.data

import com.pleos.routine.core.ActionResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** A denial at the final dispatch gate is an action result, not an uncertain HTTP failure. */
internal class ActionDispatchRejected(val result:ActionResult):IllegalStateException(result.message)

/** Context-free HTTP boundary. The factory must return an unconnected request. */
internal class PlannerHttpTransport(
    private val dispatcher:CoroutineDispatcher=Dispatchers.IO,
    private val open:(URL)->HttpURLConnection={ it.openConnection() as HttpURLConnection },
) {
    suspend fun request(endpoint:String,token:String,path:String,body:JsonObject?,debug:Boolean,
        beforeSend:suspend()->Unit={}):JsonObject {
        return withContext(dispatcher) {
            require(endpoint.isNotBlank() && token.length>=32) { "연결 설정에서 개발 서버 주소와 접속 토큰을 등록하세요." }
            require(path in setOf("/v1/status","/v1/routine-drafts","/v1/connector-actions","/v1/run-explanations"))
            val connection=open(URI(ConnectionPolicy.endpoint(endpoint,debug)+path).toURL())
            try {
                connection.connectTimeout=10_000;connection.readTimeout=65_000;connection.instanceFollowRedirects=false
                connection.setRequestProperty("Authorization","Bearer $token")
                connection.setRequestProperty("Content-Type","application/json")
                val payload=body?.toString()?.toByteArray(Charsets.UTF_8)
                if(payload!=null) { connection.requestMethod="POST";connection.doOutput=true }
                // The dispatcher hop and request preparation can outlive an approval or execution.
                // Check after both, immediately before either operation can connect/send.
                currentCoroutineContext().ensureActive()
                beforeSend()
                currentCoroutineContext().ensureActive()
                if(payload!=null)connection.outputStream.use { it.write(payload) }
                val status=connection.responseCode
                val stream=if(status in 200..299)connection.inputStream else connection.errorStream
                val bytes=stream?.use { input ->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(true) { val count=input.read(buffer);if(count<0)break;require(output.size()+count<=262144) { "서버 응답이 너무 큽니다." };output.write(buffer,0,count) }
                    output.toByteArray()
                } ?: byteArrayOf()
                require(bytes.size<=262144) { "서버 응답이 너무 큽니다." }
                val data=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                if(status !in 200..299)error(data["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "서버 요청 실패 ($status)")
                data
            } finally { connection.disconnect() }
        }
    }
}
