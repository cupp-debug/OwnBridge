package app.ownbridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import app.ownbridge.core.HistorySnapshot
import app.ownbridge.core.HistorySnapshotCodec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class HistoryLoadResult(val snapshots: List<HistorySnapshot>, val unreadable: Int)

/** Per-record authenticated encryption; neither subscriptions nor proxy credentials enter preferences. */
internal class HistoryEncryptedStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "history-v1")
    private val key: SecretKey by lazy(::getOrCreateKey)

    fun load(): HistoryLoadResult {
        if (!directory.exists()) return HistoryLoadResult(emptyList(), 0)
        val files = directory.listFiles { file -> file.isFile &&
            (file.name.endsWith(".obh") || file.name.endsWith(".obh.new") ||
                file.name.endsWith(".obh.bak")) }
            ?: return HistoryLoadResult(emptyList(), 1)
        var unreadable = 0
        val snapshots = files.map { file -> file.name.removeSuffix(".new").removeSuffix(".bak") }
            .distinct().mapNotNull { name ->
            runCatching {
                val id = name.removeSuffix(".obh")
                val bytes = AtomicFile(File(directory, name)).openRead().use { input ->
                    input.readBounded(HistorySnapshotCodec.MAX_BYTES + 64)
                }
                val value = HistorySnapshotCodec.decode(decrypt(id, bytes))
                require(value.id == id)
                value.interruptedIfRunning()
            }.getOrElse {
                unreadable++
                null
            }
        }
        return HistoryLoadResult(snapshots, unreadable)
    }

    fun save(snapshot: HistorySnapshot) {
        check(directory.isDirectory || directory.mkdirs())
        val file = AtomicFile(File(directory, "${snapshot.id}.obh"))
        val bytes = encrypt(snapshot.id, HistorySnapshotCodec.encode(snapshot))
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun delete(id: String) {
        require(Regex("[a-f0-9]{32}").matches(id))
        val file = File(directory, "$id.obh")
        AtomicFile(file).delete()
        check(!file.exists() && !File("${file.path}.new").exists() &&
            !File("${file.path}.bak").exists())
    }

    private fun encrypt(id: String, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(id.toByteArray(Charsets.US_ASCII))
        val encrypted = cipher.doFinal(plain)
        return byteArrayOf(FORMAT, cipher.iv.size.toByte()) + cipher.iv + encrypted
    }

    private fun decrypt(id: String, bytes: ByteArray): ByteArray {
        require(bytes.size >= 2 + 12 + 16 && bytes[0] == FORMAT)
        val ivSize = bytes[1].toInt() and 0xff
        require(ivSize == 12 && bytes.size > 2 + ivSize + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(2, 2 + ivSize)))
        cipher.updateAAD(id.toByteArray(Charsets.US_ASCII))
        return cipher.doFinal(bytes, 2 + ivSize, bytes.size - 2 - ivSize)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
        return output.toByteArray().also { require(it.isNotEmpty()) }
    }

    private companion object {
        const val KEY_ALIAS = "ownbridge-history-v1"
        const val FORMAT: Byte = 1
    }
}
