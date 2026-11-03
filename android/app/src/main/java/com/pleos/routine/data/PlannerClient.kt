package com.pleos.routine.data

import android.content.Context
import com.pleos.routine.BuildConfig
import com.pleos.routine.core.*
import kotlinx.serialization.json.*
import java.util.UUID

class PlannerClient(context:Context) {
    private val prefs=context.getSharedPreferences("connection",Context.MODE_PRIVATE)
    private val transport=PlannerHttpTransport()
    var endpoint:String
        get()=prefs.getString("endpoint",if(BuildConfig.DEBUG) "http://127.0.0.1:8787" else "")!!
        set(value) { prefs.edit().putString("endpoint",ConnectionPolicy.endpoint(value,BuildConfig.DEBUG)).apply() }
    // Local development server bearer only. Provider secrets are never stored in the app.
    var token:String
        get()=prefs.getString("token","")!!
        set(value) { prefs.edit().putString("token",value).apply() }
    suspend fun request(path:String,body:JsonObject?=null,beforeSend:suspend()->Unit={}):JsonObject=
        transport.request(endpoint,token,path,body,BuildConfig.DEBUG,beforeSend)
    suspend fun plan(text:String,base:WorkflowDefinition?):JsonObject=request("/v1/routine-drafts",buildJsonObject {
        put("requestId",UUID.randomUUID().toString());put("mode",if(base==null) "create" else "edit");put("requestText",text);put("locale","ko-KR")
        if(base!=null) { put("baseWorkflow",WorkflowJson.format.encodeToJsonElement(base));put("baseRevision",base.revision) }
    })
}
