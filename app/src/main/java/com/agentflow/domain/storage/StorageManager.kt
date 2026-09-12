package com.agentflow.domain.storage

import java.io.File

enum class StorageClass { USER_DATA, CACHE, TEMPORARY, HISTORY }

const val DEFAULT_STORAGE_BUDGET = 500L * 1024L * 1024L

data class StorageBreakdown(
    val userDataBytes: Long,
    val cacheBytes: Long,
    val temporaryBytes: Long,
    val historyBytes: Long,
    val budgetBytes: Long = DEFAULT_STORAGE_BUDGET,
) {
    val totalBytes: Long get() = userDataBytes + cacheBytes + temporaryBytes + historyBytes
    val overBudget: Boolean get() = totalBytes > budgetBytes
}

class StorageManager(
    private val filesDir: File,
    private val budgetBytes: Long = DEFAULT_STORAGE_BUDGET,
) {
    fun breakdown(): StorageBreakdown {
        val refs = File(filesDir, "references")
        val artifacts = File(filesDir, "artifacts")
        val chats = File(filesDir, "chat-attachments")
        val cache = File(filesDir, "reference-cache")
        val tmp = File(filesDir, "tmp")
        return StorageBreakdown(
            userDataBytes = sizeOf(refs) + sizeOf(artifacts) + sizeOf(chats),
            cacheBytes = sizeOf(cache),
            temporaryBytes = sizeOf(tmp),
            historyBytes = 0,
            budgetBytes = budgetBytes,
        )
    }

    fun cleanupCacheAndTemp(): Long {
        var freed = 0L
        listOf("reference-cache", "tmp").forEach { name ->
            val dir = File(filesDir, name)
            if (dir.exists()) {
                freed += sizeOf(dir)
                dir.deleteRecursively()
                dir.mkdirs()
            }
        }
        return freed
    }

    companion object {
        const val DEFAULT_BUDGET = DEFAULT_STORAGE_BUDGET
        fun sizeOf(file: File): Long {
            if (!file.exists()) return 0
            if (file.isFile) return file.length()
            return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
