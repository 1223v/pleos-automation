package com.pleos.routine.data

import org.junit.Assert.*
import org.junit.Test

class ConnectionPolicyTest {
    @Test fun releaseOnlyAllowsAnHttpsOrigin() {
        assertEquals("https://example.test:8443",ConnectionPolicy.endpoint("https://example.test:8443/",false))
        for(url in listOf("https:example.test","https:///host","https://host/path","https://host:0","https://host:65536","http://localhost:8787","https://user:secret@host","https://host?token=x","https://host#fragment")) {
            assertTrue(url,runCatching { ConnectionPolicy.endpoint(url,false) }.isFailure)
        }
    }
    @Test fun debugHttpIsLimitedToExplicitLocalHosts() {
        for(host in listOf("localhost","127.0.0.1","10.0.2.2"))assertEquals("http://$host:8787",ConnectionPolicy.endpoint("http://$host:8787",true))
        for(url in listOf("http://localhost.evil.test","http://127.0.0.1@evil.test","http://192.168.0.1","file:///token"))assertTrue(url,runCatching { ConnectionPolicy.endpoint(url,true) }.isFailure)
    }
}
