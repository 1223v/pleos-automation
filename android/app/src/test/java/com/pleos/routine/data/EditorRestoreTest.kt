package com.pleos.routine.data

import com.pleos.routine.core.*
import org.junit.Assert.*
import org.junit.Test

class EditorRestoreTest {
    @Test fun sameCreateRequestPreservesLatestTextAndGraph() {
        val request=PendingRoutineRequest(id="voice-1",action="CREATE_ROUTINE",text="original")
        val latest=WorkingEditor(WorkflowExamples.blank("edited"),"latest typed request",request.id)
        val restored=EditorRestore.restore(latest,request,null)
        assertEquals(latest,restored.working);assertFalse(restored.applied)
    }
    @Test fun sameEditRequestDoesNotReloadOriginalSavedRoutine() {
        val request=PendingRoutineRequest(id="voice-1",action="EDIT_ROUTINE",text="original",routineId="r")
        val original=WorkflowExamples.blank("r")
        val latest=WorkingEditor(original.copy(title="Edited",revision=2),"new",request.id)
        assertEquals(latest,EditorRestore.restore(latest,request,original).working)
    }
    @Test fun newEditRequestUsesTargetAndNewRequestExactlyOnce() {
        val request=PendingRoutineRequest(id="voice-2",action="EDIT_ROUTINE",text="new",routineId="r")
        val original=WorkflowExamples.blank("r")
        val result=EditorRestore.restore(WorkingEditor(null,"old","voice-1"),request,original)
        assertTrue(result.applied);assertEquals(WorkingEditor(original,"new","voice-2"),result.working)
        assertFalse(EditorRestore.restore(result.working,request,original).applied)
    }
    @Test fun missingTargetRetainsGraphAndRequestsReview() {
        val current=WorkflowExamples.blank("current")
        val request=PendingRoutineRequest(id="v",action="EDIT_ROUTINE",text="edit",routineId="deleted")
        val result=EditorRestore.restore(WorkingEditor(current),request,null)
        assertEquals(current,result.working.workflow);assertTrue(result.missingTarget)
    }
    @Test fun controlAndInvalidRequestsNeverBecomeRestorableDrafts() {
        val current=WorkingEditor(WorkflowExamples.blank("current"),"typed","v")
        assertEquals(current,EditorRestore.restore(current,PendingRoutineRequest(action="CONTROL_ROUTINE",text="run"),null).working)
        assertEquals(current,EditorRestore.restore(current,null,null).working)
    }
    @Test fun oldIntentCannotOverwriteNewerRetainedDraft() {
        val old=PendingRoutineRequest(id="A",action="EDIT_ROUTINE",text="old",routineId="old")
        val latest=PendingRoutineRequest(id="B",action="CREATE_ROUTINE",text="new")
        val working=WorkingEditor(WorkflowExamples.blank("latest"),"latest typed",latest.id)
        val incoming=EditorRestore.incoming(working,old,latest,freshIntent=false)
        assertEquals(latest,incoming);assertEquals(working,EditorRestore.restore(working,incoming,null).working)
    }
    @Test fun clearedRequestDoesNotComeBackFromRecreatedIntent() {
        val old=PendingRoutineRequest(id="A",action="CREATE_ROUTINE",text="old")
        assertNull(EditorRestore.incoming(WorkingEditor(null,"latest","B"),old,null,freshIntent=false))
    }
    @Test fun freshUnsavedRequestCanStillBeReviewedWithoutLosingStoredRequest() {
        val stored=PendingRoutineRequest(id="A",action="CREATE_ROUTINE",text="saved")
        val fresh=PendingRoutineRequest(id="B",action="CREATE_ROUTINE",text="new failed save")
        assertEquals(fresh,EditorRestore.incoming(WorkingEditor(null),fresh,stored,true,true))
        assertEquals(fresh,EditorRestore.incoming(WorkingEditor(null),fresh,null,true))
    }
}
