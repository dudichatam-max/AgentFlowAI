package com.agentflow.domain.reference

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GitHubUrlParserTest {
    @Test
    fun parsesRepoBranchPath() {
        val loc = GitHubUrlParser.parse("https://github.com/example/example/tree/main/app/src/main")
        assertThat(loc.owner).isEqualTo("example")
        assertThat(loc.repo).isEqualTo("example")
        assertThat(loc.ref).isEqualTo("main")
        assertThat(loc.path).isEqualTo("app/src/main")
    }

    @Test(expected = ReferenceException::class)
    fun rejectsNonGithub() {
        GitHubUrlParser.parse("https://evil.example/repo")
    }
}
