package com.agentflow.domain.reference

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

interface ReferenceStorage {
    fun save(referenceId: String, filename: String, bytes: ByteArray): StoredBlob
    fun save(referenceId: String, filename: String, stream: InputStream, sizeHint: Long = -1): StoredBlob
    fun open(referenceId: String): InputStream?
    fun readText(referenceId: String, maxBytes: Int = ReferenceLimits.MAX_READ_BYTES): String?
    fun delete(referenceId: String)
    fun exists(referenceId: String): Boolean
    fun size(referenceId: String): Long
    fun totalBytes(): Long
    fun cleanupOrphans(knownIds: Set<String>): Int
}

data class StoredBlob(
    val relativePath: String,
    val sizeBytes: Long,
    val contentHash: String,
)

class FileReferenceStorage(
    private val root: File,
    private val cacheRoot: File = File(root.parentFile, "reference-cache"),
) : ReferenceStorage {

    init {
        root.mkdirs()
        cacheRoot.mkdirs()
    }

    override fun save(referenceId: String, filename: String, bytes: ByteArray): StoredBlob =
        save(referenceId, filename, ByteArrayInputStream(bytes), bytes.size.toLong())

    override fun save(referenceId: String, filename: String, stream: InputStream, sizeHint: Long): StoredBlob {
        val safeId = sanitize(referenceId)
        val dir = File(root, safeId).apply { mkdirs() }
        val dest = File(dir, sanitize(filename).ifBlank { "content.bin" })
        dest.outputStream().use { out -> stream.copyTo(out) }
        val hash = dest.inputStream().use { ContentHasher.sha256(it) }
        return StoredBlob("references/$safeId/${dest.name}", dest.length(), hash)
    }

    override fun open(referenceId: String): InputStream? {
        val file = firstFile(referenceId) ?: return null
        return file.inputStream()
    }

    override fun readText(referenceId: String, maxBytes: Int): String? {
        val file = firstFile(referenceId) ?: return null
        val bytes = file.inputStream().use { input ->
            val buf = ByteArray(maxBytes + 1)
            var off = 0
            while (off < buf.size) {
                val n = input.read(buf, off, buf.size - off)
                if (n <= 0) break
                off += n
            }
            buf.copyOf(off)
        }
        val truncated = bytes.size > maxBytes
        val slice = if (truncated) bytes.copyOf(maxBytes) else bytes
        val text = slice.toString(Charsets.UTF_8)
        return if (truncated) text + "\n[CONTENT TRUNCATED FOR CONTEXT BUDGET]\n" else text
    }

    override fun delete(referenceId: String) {
        val dir = File(root, sanitize(referenceId))
        if (dir.exists()) dir.deleteRecursively()
    }

    override fun exists(referenceId: String): Boolean = firstFile(referenceId) != null

    override fun size(referenceId: String): Long = firstFile(referenceId)?.length() ?: 0L

    override fun totalBytes(): Long = totalReferenceBytes() + cacheBytes()

    fun totalReferenceBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    override fun cleanupOrphans(knownIds: Set<String>): Int {
        val safeKnown = knownIds.map { sanitize(it) }.toSet()
        var removed = 0
        root.listFiles()?.forEach { child ->
            if (child.isDirectory && child.name !in safeKnown) {
                child.deleteRecursively()
                removed++
            }
        }
        return removed
    }

    fun cacheFile(key: String, bytes: ByteArray): File {
        val dest = File(cacheRoot, sanitize(key))
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return dest
    }

    fun clearCache() {
        cacheRoot.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun cacheBytes(): Long = cacheRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun firstFile(referenceId: String): File? {
        val dir = File(root, sanitize(referenceId))
        return dir.listFiles()?.firstOrNull { it.isFile }
    }

    companion object {
        fun sanitize(raw: String): String {
            val name = raw.substringAfterLast('/').substringAfterLast('\\')
            if (name.contains("..") || name.startsWith("/")) {
                throw ReferenceException(ReferenceErrorType.INVALID_FILE, "Illegal path")
            }
            return name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
        }
    }
}
