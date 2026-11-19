package com.eazyverse.testtrack.domain

/** An "@word" the cursor sits right after: the "@" is at [at], [query] is what follows it up to the cursor. */
data class MentionQuery(
    val at: Int,
    val query: String,
)

/** One row of the suggestion list: a member, or a member's app when [app] is set. */
data class MentionOption(
    val person: ChatPerson,
    val app: Boolean,
) {
    /** The name matched against what is typed. */
    val name: String get() = if (app) person.appName else person.name
}

/** Finding the "@word" being typed, and who it may mean. Pure, so the field and the list agree. */
object MentionFinder {
    /** The longest name typed after "@" that still counts, so a stray "@" far back never opens the list. */
    private const val MAX_QUERY = 30

    /** A name may have a few words ("Amir Haddad", "Deshi Help Malaysia"). */
    private const val MAX_SPACES = 3

    /**
     * The "@word" the cursor is right after, or null. The "@" must start a word (so an address like
     * a@b is left alone), the text after it may hold a few spaces but no line break and must not
     * start with a space, and it must not lie inside a mention already picked ([spans]).
     */
    fun queryAt(
        text: String,
        cursor: Int,
        spans: List<MentionSpan> = emptyList(),
    ): MentionQuery? {
        if (cursor < 1 || cursor > text.length) return null
        var at = cursor - 1
        while (at >= 0 && text[at] != '@') {
            if (text[at].isWhitespace() && text[at] != ' ') return null
            if (cursor - at > MAX_QUERY) return null
            at--
        }
        if (at < 0) return null
        if (at > 0 && !text[at - 1].isWhitespace()) return null
        if (spans.any { at >= it.start && at < it.end }) return null
        val query = text.substring(at + 1, cursor)
        if (query.firstOrNull()?.isWhitespace() == true) return null
        if (query.count { it == ' ' } > MAX_SPACES) return null
        return MentionQuery(at, query)
    }

    /**
     * Who [query] may mean, best first: members before apps, those whose name starts with it, then
     * those with a word starting with it, then the rest that contain it, each group by name. An empty
     * query lists everyone. [people] is filtered to the crew's others here, never yourself.
     */
    fun options(
        people: Collection<ChatPerson>,
        query: String,
    ): List<MentionOption> {
        val q = query.trimStart().lowercase()
        val others = people.filter { it.inCrew && !it.mine }
        val all =
            others.filter { it.name.isNotBlank() }.map { MentionOption(it, app = false) } +
                others.filter { it.appName.isNotBlank() }.map { MentionOption(it, app = true) }
        return all
            .mapNotNull { option -> rank(option.name, q).takeIf { it >= 0 }?.let { it to option } }
            .sortedWith(compareBy({ it.first }, { it.second.app }, { it.second.name.lowercase() }))
            .map { it.second }
    }

    /** 0 when [name] starts with [q], 1 when a word of it does, 2 when it only contains it, -1 when not at all. */
    internal fun rank(
        name: String,
        q: String,
    ): Int {
        if (q.isEmpty()) return 0
        val n = name.lowercase()
        if (n.startsWith(q)) return 0
        var from = n.indexOf(q)
        if (from < 0) return -1
        var best = 2
        while (from >= 0) {
            if (from == 0 || !n[from - 1].isLetterOrDigit()) {
                best = 1
                break
            }
            from = n.indexOf(q, from + 1)
        }
        return best
    }
}
