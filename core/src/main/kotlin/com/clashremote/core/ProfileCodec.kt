package com.clashremote.core

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ProfileCodec(private val key: () -> SecretKey) {
    @Serializable private data class StoredProfile(
        val version: Int = 1, val name: String, val endpoint: String, val testUrl: String,
        val iv: String, val ciphertext: String,
    )
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun aad(name: String, endpoint: String, testUrl: String) =
        listOf("1", name, endpoint, testUrl).joinToString("\u0000").toByteArray(Charsets.UTF_8)
    fun encode(profile: RouterProfile): String {
        val validated = profile.validated()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(aad(validated.name, validated.endpoint, validated.testUrl))
            val encrypted = cipher.doFinal(validated.secret.toByteArray(Charsets.UTF_8))
            return json.encodeToString(StoredProfile(name = validated.name, endpoint = validated.endpoint,
                testUrl = validated.testUrl, iv = Base64.getEncoder().encodeToString(cipher.iv),
                ciphertext = Base64.getEncoder().encodeToString(encrypted)))
        } catch (e: Exception) { throw ClashException("无法加密保存密钥，请重试", e) }
    }
    fun decode(stored: String): RouterProfile {
        try {
            val data = json.decodeFromString<StoredProfile>(stored)
            require(data.version == 1)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(data.iv)))
            cipher.updateAAD(aad(data.name, data.endpoint, data.testUrl))
            val secret = String(cipher.doFinal(Base64.getDecoder().decode(data.ciphertext)), Charsets.UTF_8)
            return RouterProfile(data.name, data.endpoint, secret, data.testUrl).validated()
        } catch (e: Exception) { throw ClashException("本地配置无法解密或已损坏，请重新填写并保存", e) }
    }
}
