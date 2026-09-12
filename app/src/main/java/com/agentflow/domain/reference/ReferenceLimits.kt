package com.agentflow.domain.reference

object ReferenceLimits {
    const val MAX_SINGLE_FILE_BYTES = 512 * 1024
    const val MAX_FOLDER_FILES = 80
    const val MAX_TOTAL_IMPORT_BYTES = 4 * 1024 * 1024
    const val MAX_TEXT_REFERENCE_BYTES = 256 * 1024
    const val MAX_GITHUB_TREE = 120
    const val MAX_READ_BYTES = 96 * 1024
    const val HASH_PREFIX_BYTES = 2 * 1024 * 1024
}

enum class FileClass { TEXT, CODE, DOCUMENT, IMAGE, BINARY, UNKNOWN }

object FileClassifier {
    private val code = setOf(
        "kt", "kts", "java", "xml", "json", "yml", "yaml", "md", "txt",
        "gradle", "sh", "bash", "properties", "toml", "proto", "css", "html", "js", "ts",
    )
    private val image = setOf("png", "jpg", "jpeg", "gif", "webp", "svg")
    private val document = setOf("pdf", "doc", "docx")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun classify(name: String, mime: String? = null): FileClass {
        val ext = extension(name)
        return when {
            ext in code || mime?.startsWith("text/") == true ->
                if (ext in setOf("md", "txt")) FileClass.DOCUMENT else FileClass.CODE
            ext in image || mime?.startsWith("image/") == true -> FileClass.IMAGE
            ext in document -> FileClass.DOCUMENT
            ext.isBlank() -> FileClass.UNKNOWN
            else -> FileClass.BINARY
        }
    }

    fun isTextLike(name: String, mime: String? = null): Boolean =
        classify(name, mime) in setOf(FileClass.TEXT, FileClass.CODE) ||
            extension(name) in setOf("md", "txt")
}
