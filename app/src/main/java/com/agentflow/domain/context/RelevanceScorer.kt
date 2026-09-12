package com.agentflow.domain.context

import com.agentflow.domain.model.Reference

object RelevanceScorer {
    fun score(query: String, reference: Reference, snippet: String? = null): Double {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return 0.0
        val haystack = tokenize(listOfNotNull(reference.name, reference.path, snippet).joinToString(" "))
        if (haystack.isEmpty()) return 0.0
        val overlap = tokens.count { it in haystack }
        val nameBoost = if (tokenize(reference.name).any { it in tokens }) 0.25 else 0.0
        val pathBoost = if ((reference.path ?: "").lowercase().let { p -> tokens.any { it in p } }) 0.15 else 0.0
        return (overlap.toDouble() / tokens.size) + nameBoost + pathBoost
    }

    fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9+]+"))
            .filter { it.length >= 3 }
            .toSet()
}
