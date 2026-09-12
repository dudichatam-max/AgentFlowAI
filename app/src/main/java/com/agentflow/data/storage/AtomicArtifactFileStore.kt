package com.agentflow.data.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** File-backed artifact content store with same-directory atomic replacement and orphan cleanup. */
class AtomicArtifactFileStore(private val root: File) {

    fun writeAtomic(id: String, content: String) {
        validateId(id)
        root.mkdirs()
        val target = root.resolve(id)
        val temp = root.resolve(".artifact-tmp-$id-${System.nanoTime()}")
        try {
            temp.outputStream().use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            Files.deleteIfExists(temp.toPath())
        }
    }

    fun read(id: String): String? {
        validateId(id)
        val file = root.resolve(id)
        return if (file.isFile) {
            file.inputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            null
        }
    }

    fun reconcile(referencedIds: Set<String>): Set<String> {
        root.mkdirs()
        val removed = mutableSetOf<String>()
        root.listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name.startsWith(".artifact-tmp-")) {
                file.delete()
                return@forEach
            }
            if (file.name !in referencedIds && file.delete()) {
                removed += file.name
            }
        }
        return removed
    }

    private fun validateId(id: String) {
        require(id.isNotBlank() && id != "." && id != ".." && !id.contains('/') && !id.contains('\\')) {
            "Invalid artifact id"
        }
    }
}
