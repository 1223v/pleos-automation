package com.pleos.routine.data

import org.junit.Assert.*
import org.junit.Test

class GleoReviewsTest {
    private var draft:PendingRoutineRequest?=null
    private val replies=mutableListOf<Triple<String,String,String>>()
    private val reviews=GleoReviews({ draft },{ draft=it;true },{ id ->
        if(draft?.id!=id) false else { draft=null;true }
    },{ action,status,message -> replies+=Triple(action,status,message) })

    @Test fun onlyMatchingReviewedSaveFinishesTheEditOnce() {
        val request=PendingRoutineRequest("id","EDIT_ROUTINE","make it darker","target")
        assertTrue(reviews.retain(request))
        assertFalse(reviews.saved("somebody-else","target"))
        assertFalse(reviews.saved("id","another-routine"))
        assertTrue(reviews.saved("id","target"))
        assertFalse(reviews.saved("id","target"))
        assertNull(draft)
        assertEquals(listOf("success"),replies.map { it.second })
    }

    @Test fun replacingOrDecliningADraftCannotReportSuccess() {
        val old=PendingRoutineRequest("old","CREATE_ROUTINE","request")
        assertTrue(reviews.retain(old))
        assertTrue(reviews.retain(old.copy(id="new")))
        assertFalse(reviews.dismiss("old"))
        assertTrue(reviews.dismiss("new"))
        assertEquals(listOf("fail","fail"),replies.map { it.second })
    }

    @Test fun controlIsBoundToOneLiveReviewAndNeverClaimsExecution() {
        reviews.openControl("nonce","target","start")
        assertFalse(reviews.reviewControl("other","target","start",true,true))
        assertFalse(reviews.reviewControl("nonce","different","start",true,true))
        assertFalse(reviews.reviewControl("nonce","target","stop",true,true))
        assertTrue(reviews.reviewControl("nonce","target","start",true,true))
        assertFalse(reviews.reviewControl("nonce","target","start",true,true))
        assertEquals("success",replies.single().second)
        assertTrue(replies.single().third.contains("review"))
        assertTrue(replies.single().third.contains("No vehicle command"))
    }

    @Test fun declinedAndMissingControlTargetsReturnFail() {
        reviews.openControl("first","target","stop")
        assertTrue(reviews.reviewControl("first","target","stop",false,true))
        reviews.openControl("second","missing","enable")
        assertTrue(reviews.reviewControl("second","missing","enable",true,false))
        assertEquals(listOf("fail","fail"),replies.map { it.second })
    }

    @Test fun replacedControlCannotBeReplayedOrPersisted() {
        reviews.openControl("first","target","start")
        reviews.openControl("second","target","stop")
        assertNull(draft)
        assertFalse(reviews.matchesControl("first","target","start"))
        assertTrue(reviews.matchesControl("second","target","stop"))
        reviews.retain(PendingRoutineRequest("draft","CREATE_ROUTINE","new request"))
        assertFalse(reviews.reviewControl("second","target","stop",true,true))
        assertEquals(listOf("fail","fail"),replies.map { it.second })
    }
}
