package com.pleos.routine.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable data class PendingRoutineRequest(
    val id:String=UUID.randomUUID().toString(),
    val action:String,
    val text:String,
    val routineId:String?=null,
) {
    fun isValid()=id.isNotBlank() && id.length<=80 && action in setOf("CREATE_ROUTINE","EDIT_ROUTINE") && text.length<=4000 && (routineId?.length ?: 0)<=80
}

/** One unapproved voice draft; execution/control requests are deliberately never persisted. */
class PendingRequestStore(context:Context,name:String="pending_gleo_request") {
    private val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
    @Synchronized fun read():PendingRoutineRequest? = prefs.getString("draft",null)?.let { raw ->
        runCatching { Json.decodeFromString<PendingRoutineRequest>(raw) }.getOrNull()?.takeIf { it.isValid() }
    }
    /** Caller uses Dispatchers.IO and checks the durable write result. */
    @Synchronized fun save(request:PendingRoutineRequest):Boolean {
        require(request.isValid()) { "Only a bounded create/edit draft may be saved" }
        return prefs.edit().putString("draft",Json.encodeToString(request)).commit()
    }
    @Synchronized fun clearIf(id:String):Boolean {
        if(read()?.id!=id)return false
        return prefs.edit().remove("draft").commit()
    }
}
