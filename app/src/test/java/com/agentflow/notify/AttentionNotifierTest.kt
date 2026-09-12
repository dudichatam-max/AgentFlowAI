package com.agentflow.notify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AttentionNotifierTest {
    @Test
    fun stableIds() {
        val a = AttentionNotifier.key("m1", AttentionNotifier.KIND_DONE, "e1")
        val b = AttentionNotifier.key("m1", AttentionNotifier.KIND_DONE, "e1")
        val c = AttentionNotifier.key("m1", AttentionNotifier.KIND_FAIL, "e1")
        assertThat(a).isEqualTo(b)
        assertThat(AttentionNotifier.notificationId(a)).isEqualTo(AttentionNotifier.notificationId(b))
        assertThat(AttentionNotifier.notificationId(a)).isNotEqualTo(AttentionNotifier.notificationId(c))
    }
}
