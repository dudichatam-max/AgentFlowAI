package com.agentflow.domain.contract

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContractParserTest {
    @Test
    fun extractsFencedJson() {
        val raw = """
            Here is the result:
            ```json
            {"version":1,"status":"CONTINUE","message":"ok","confidence":0.9,"actions":[]}
            ```
        """.trimIndent()
        val env = ContractParser.parse(raw)
        assertThat(env.version).isEqualTo(1)
        assertThat(env.message).isEqualTo("ok")
    }

    @Test(expected = ContractException::class)
    fun rejectsBadVersion() {
        ContractParser.parse("""{"version":9,"status":"CONTINUE","message":"x"}""")
    }

    @Test(expected = ContractException::class)
    fun rejectsMalformed() {
        ContractParser.parse("not json at all")
    }
}
