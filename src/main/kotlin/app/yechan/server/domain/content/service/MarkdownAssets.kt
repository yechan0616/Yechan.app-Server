package app.yechan.server.domain.content.service

// Markdown refers to an uploaded image as "![alt](asset:<id>)" so the body survives a storage move or a re-import.
internal object MarkdownAssets {
    private val destination = Regex("""(?<=\]\()\s*asset:([^\s)]*)""")

    fun references(markdown: String): List<String> = destination.findAll(markdown).map { it.groupValues[1] }.toList()

    fun rewrite(
        markdown: String,
        replacement: (String) -> String?,
    ): String =
        destination.replace(markdown) { match ->
            replacement(match.groupValues[1])?.let { "${match.value.substringBefore("asset:")}$it" } ?: match.value
        }
}
