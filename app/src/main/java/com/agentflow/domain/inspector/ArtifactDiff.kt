package com.agentflow.domain.inspector

data class ArtifactVersionDiff(
    val added: List<String>,
    val removed: List<String>,
    val changed: List<String>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty() && changed.isEmpty()

    fun render(maxLines: Int = 80): String = buildString {
        (removed.take(maxLines / 3)).forEach { appendLine("- $it") }
        (added.take(maxLines / 3)).forEach { appendLine("+ $it") }
        (changed.take(maxLines / 3)).forEach { appendLine("~ $it") }
    }.trimEnd()
}

object ArtifactDiffer {
    fun diff(previous: String, current: String): ArtifactVersionDiff {
        val old = previous.lines()
        val new = current.lines()
        val oldSet = old.toSet()
        val newSet = new.toSet()
        return ArtifactVersionDiff(
            added = (newSet - oldSet).toList(),
            removed = (oldSet - newSet).toList(),
            changed = emptyList(),
        )
    }
}
