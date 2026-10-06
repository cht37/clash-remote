package com.clashremote.app.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.clashremote.core.*
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ProfileStore(context: Context) : ProfilePersistence {
    private val preferences = context.getSharedPreferences("router", Context.MODE_PRIVATE)
    private val codec = ProfileCodec { key() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("clash_remote_secret_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("clash_remote_secret_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    override fun load(): RouterProfile? = preferences.getString("profile", null)?.let { codec.decode(it) }
    override fun save(profile: RouterProfile) {
        val encoded = codec.encode(profile)
        if (!preferences.edit().putString("profile", encoded).commit()) throw ClashException("配置保存失败，请重试")
    }
    override fun clear() {
        if (!preferences.edit().clear().commit()) throw ClashException("清除本地配置失败，请重试")
    }
}
