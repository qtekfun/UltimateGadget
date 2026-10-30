package com.qtekfun.ultimategadget.mapsdl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XiaomiAuthKeyPickTest {
    private val authKey = "a".repeat(32)
    private val other = "b".repeat(32)

    @Test
    fun authKeyWinsOverEarlierSiblingSecrets() {
        val leaves = linkedMapOf("did" to other, "detail.beaconkey" to other, "detail.auth_key" to authKey)
        assertEquals(authKey, XiaomiToken.findAuthKey(leaves))
    }

    @Test
    fun looseNameUsedWhenNoExactAuthKey() {
        val leaves = linkedMapOf("sid" to other, "detail.device_key" to authKey)
        assertEquals(authKey, XiaomiToken.findAuthKey(leaves))
    }

    @Test
    fun nonHexKeyYieldsNull() {
        assertNull(XiaomiToken.findAuthKey(linkedMapOf("detail.auth_key" to "not-hex")))
    }
}
