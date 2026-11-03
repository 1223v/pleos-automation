package com.pleos.routine.data

import com.pleos.routine.core.WorkflowDefinition

data class WorkingEditor(val workflow:WorkflowDefinition?,val text:String="",val originRequestId:String?=null)
data class RestoredEditor(val working:WorkingEditor,val applied:Boolean,val missingTarget:Boolean=false)
object EditorRestore {
    fun incoming(working:WorkingEditor,intent:PendingRoutineRequest?,retained:PendingRoutineRequest?,freshIntent:Boolean,saveFailed:Boolean=false):PendingRoutineRequest? {
        // The private retained store is the current request; an old Activity Intent has no ordering authority.
        if(freshIntent && saveFailed)return intent?.takeIf { it.isValid() }
        return retained?.takeIf { it.isValid() } ?: intent?.takeIf { freshIntent && it.isValid() && it.id!=working.originRequestId }
    }
    fun restore(working:WorkingEditor,incoming:PendingRoutineRequest?,target:WorkflowDefinition?):RestoredEditor {
        if(incoming==null || !incoming.isValid() || incoming.id==working.originRequestId)
            return RestoredEditor(working,false)
        val editing=incoming.action=="EDIT_ROUTINE"
        return RestoredEditor(WorkingEditor(if(editing)target ?: working.workflow else working.workflow,
            incoming.text,incoming.id),true,editing && target==null)
    }
}
