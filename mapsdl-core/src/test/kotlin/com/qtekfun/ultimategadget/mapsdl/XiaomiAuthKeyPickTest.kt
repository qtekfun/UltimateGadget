package com.qtekfun.ultimategadget.mapsdl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XiaomiAuthKeyPickTest {
    private val authKey = "00112233445566778899aabbccddeeff"
    private val other = "ffeeddccbbaa99887766554433221100"

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
