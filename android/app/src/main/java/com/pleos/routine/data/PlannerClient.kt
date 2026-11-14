package com.pleos.routine.data

import android.content.Context
import com.pleos.routine.BuildConfig
import com.pleos.routine.core.*
import com.pleos.routine.vehicle.Capability
import kotlinx.serialization.json.*

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
    suspend fun plan(text:String,base:WorkflowDefinition?,selectedNodeId:String?=null,
                     answers:AnsweredQuestions?=null,capabilities:List<Capability> = emptyList()):JsonObject =
        request("/v1/routine-drafts",PlannerConversation.request(text,base,selectedNodeId,answers,capabilities))
}
