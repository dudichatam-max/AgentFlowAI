package com.agentflow.data.storage

import java.nio.file.Files

fun main() {
    atomicWriteLeavesOnlyFinalFile()
    reconcileRemovesOrphansButKeepsReferencedFiles()
    rejectsPathTraversalIds()
    println("AtomicArtifactFileStoreTest: PASS")
}

private fun atomicWriteLeavesOnlyFinalFile() {
    val root = Files.createTempDirectory("artifact-store-test").toFile()
    try {
        val store = AtomicArtifactFileStore(root)
        store.writeAtomic("artifact-1", "hello")

        check(store.read("artifact-1") == "hello")
        check(root.listFiles()!!.map { it.name }.toSet() == setOf("artifact-1"))
    } finally {
        root.deleteRecursively()
    }
}

private fun reconcileRemovesOrphansButKeepsReferencedFiles() {
    val root = Files.createTempDirectory("artifact-store-test").toFile()
    try {
        val store = AtomicArtifactFileStore(root)
        store.writeAtomic("kept", "keep")
        store.writeAtomic("orphan", "remove")
        Files.writeString(root.resolve(".artifact-tmp-orphan" ).toPath(), "tmp")

        val removed = store.reconcile(setOf("kept"))

        check(removed == setOf("orphan"))
        check(store.read("kept") == "keep")
        check(!root.resolve("orphan").exists())
        check(!root.resolve(".artifact-tmp-orphan").exists())
    } finally {
        root.deleteRecursively()
    }
}

private fun rejectsPathTraversalIds() {
    val root = Files.createTempDirectory("artifact-store-test").toFile()
    try {
        val store = AtomicArtifactFileStore(root)
        check(runCatching { store.writeAtomic("../escape", "bad") }.isFailure)
        check(runCatching { store.read("nested/escape") }.isFailure)
    } finally {
        root.deleteRecursively()
    }
}
