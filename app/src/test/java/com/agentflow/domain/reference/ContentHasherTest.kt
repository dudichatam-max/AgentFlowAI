package com.agentflow.domain.reference

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentHasherTest {
    @Test
    fun sameContentSameHash() {
        assertThat(ContentHasher.sha256("abc")).isEqualTo(ContentHasher.sha256("abc"))
        assertThat(ContentHasher.sha256("abc")).isNotEqualTo(ContentHasher.sha256("abd"))
        assertThat(ContentHasher.sha256("abc")).hasLength(64)
    }
}
