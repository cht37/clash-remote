package com.clashremote.core

import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Test

class ProfileCodecTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun secretIsEncryptedAndEachSaveUsesFreshIv() {
        val codec = ProfileCodec { key }
        val profile = RouterProfile("家庭", "http://192.168.1.1:9090", "private-test-secret")
        val first = codec.encode(profile)
        val second = codec.encode(profile)
        assertFalse(first.contains("private-test-secret"))
        assertNotEquals(first, second)
        assertEquals(profile.validated(), codec.decode(first))
    }
    @Test fun wrongKeyAndTamperedMetadataRequireReconfiguration() {
        val stored = ProfileCodec { key }.encode(RouterProfile(endpoint = "http://192.168.1.1:9090", secret = "secret"))
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(runCatching { ProfileCodec { other }.decode(stored) }.exceptionOrNull() is ClashException)
        assertTrue(runCatching { ProfileCodec { key }.decode(stored.replace("192.168.1.1", "192.168.1.2")) }.exceptionOrNull() is ClashException)
        assertTrue(runCatching { ProfileCodec { key }.decode("broken") }.exceptionOrNull() is ClashException)
    }
}
