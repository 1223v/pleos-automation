package com.pleos.routine.data

import java.net.URI

object ConnectionPolicy {
    fun endpoint(value:String,debug:Boolean):String {
        val u=URI(value)
        require(u.scheme=="https" || debug && u.scheme=="http" && u.host in setOf("127.0.0.1","localhost","10.0.2.2"))
        require(u.userInfo==null && u.query==null && u.fragment==null)
        require(!u.host.isNullOrBlank() && !u.isOpaque && u.rawPath in setOf("","/") && (u.port==-1 || u.port in 1..65535))
        return value.trimEnd('/')
    }
}
