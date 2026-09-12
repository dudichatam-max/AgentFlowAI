package com.agentflow.domain.reference

object SecretDenylist {
    private val exact = setOf(
        ".env",
        "local.properties",
        "google-services.json",
        "keystore.properties",
        ".npmrc",
        ".pypirc",
    )

    private val suffixes = listOf(
        ".pem",
        ".key",
        ".p12",
        ".jks",
        ".keystore",
        ".env",
        ".credentials",
        ".secret",
    )

    private val privateKeyPattern = Regex("(?i)BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY")
    private val assignmentPattern = Regex(
        "(?i)(api[_-]?key|secret|password|token|access[_-]?token|client[_-]?secret)\\s*[=:]\\s*[^\\s#]{8,}",
    )

    fun isDenied(name: String): Boolean {
        val base = name.substringAfterLast('/').substringAfterLast('\\').lowercase()
        if (base in exact) return true
        if (suffixes.any { base.endsWith(it) }) return true
        return base.startsWith("id_rsa") || base.startsWith("id_ed25519")
    }

    fun looksLikeSecretContent(text: String): Boolean =
        privateKeyPattern.containsMatchIn(text) || assignmentPattern.containsMatchIn(text)
}
