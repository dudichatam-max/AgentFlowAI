package com.agentflow.domain.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun accountsAndProtectsUserData() {
        val root = tmp.root
        File(root, "references").apply { mkdirs(); File(this, "a.txt").writeText("hello") }
        File(root, "reference-cache").apply { mkdirs(); File(this, "c.bin").writeText("cache") }
        File(root, "tmp").apply { mkdirs(); File(this, "t").writeText("tmp") }
        val mgr = StorageManager(root, budgetBytes = 1024)
        val before = mgr.breakdown()
        assertThat(before.userDataBytes).isAtLeast(5)
        assertThat(before.cacheBytes).isAtLeast(4)
        val freed = mgr.cleanupCacheAndTemp()
        assertThat(freed).isGreaterThan(0)
        assertThat(File(root, "references/a.txt").readText()).isEqualTo("hello")
        assertThat(mgr.breakdown().cacheBytes).isEqualTo(0)
    }
}
