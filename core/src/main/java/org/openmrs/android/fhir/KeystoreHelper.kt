package org.openmrs.android.fhir

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object KeystoreHelper {

  private const val KEY_ALIAS = "MyEncryptionKey"
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"

  fun initialize() {
    try {
      val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE)
      keyStore.load(null)

      if (!keyStore.containsAlias(KEY_ALIAS)) {
        generateKey()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun generateKey() {
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val keyGenParameterSpec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
      .setKeySize(256)
      .build()

    keyGenerator.init(keyGenParameterSpec)
    keyGenerator.generateKey()
  }

  fun getKey(): SecretKey {
    val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)
    return keyStore.getKey(KEY_ALIAS, null) as SecretKey
  }
}
