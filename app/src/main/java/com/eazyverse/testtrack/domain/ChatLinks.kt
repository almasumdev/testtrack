package com.eazyverse.testtrack.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Whether a chat message holds a link, by the same steps and rules as the server's
 * app/services/chat_links.py. The server is the judge; this only stops a message the server would
 * refuse before it is sent, and says why while it is typed.
 *
 * The text is folded first, so a link written to slip past (spaced out, full width, "dot com") reads
 * as the plain link it is, then five rules look for a scheme, "www", a domain, a dot before a common
 * link ending, or an IP address. Character classes are spelled out with \p{L} and \p{N} rather than
 * \w, which Android and the desk JVM read differently.
 *
 * Change this together with the server, and with the shared cases in chat_link_cases.json.
 */
object ChatLinks {
    /** True when [text] would be refused as a link. [allow] is the crew's own package names. */
    fun blocked(
        text: String,
        allow: Set<String> = emptySet(),
    ): Boolean {
        if (text.isBlank()) return false
        val folded = normalise(text, allow)
        if (SCHEME.containsMatchIn(folded) || SLASHES.containsMatchIn(folded)) return true
        if (WWW.containsMatchIn(folded)) return true
        if (hasDomain(folded)) return true
        if (SPACED_TLD.containsMatchIn(folded)) return true
        return IPV4.containsMatchIn(folded)
    }

    /** The seven steps, in order, that turn a disguised link back into a plain one. */
    internal fun normalise(
        text: String,
        allow: Set<String> = emptySet(),
    ): String {
        var s = text
        // 1. The crew's own apps may be named by package.
        for (pkg in allow.filter { it.isNotBlank() }.sortedByDescending { it.length }) {
            s = Regex("(?i)(?<![$WORD.-])" + Regex.escape(pkg) + "(?![$WORD-]|\\.[$WORD])").replace(s, " ")
        }
        // 2. Full-width and other compatibility forms to plain ones, then one case.
        s = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        // 3. Characters that draw nothing: zero width, soft hyphen, BOM.
        s = s.filterCodePoints { Character.getType(it) != Character.FORMAT.toInt() }
        // 4. Dots that only look like dots.
        s = DOT_LOOKALIKES.replace(s, ".")
        // 5. Dots in brackets or spelled out.
        s = BRACKET_DOT.replace(s, ".")
        s = SPOKEN_DOT.replace(s, ".")
        // 6. Single characters spaced apart, joined back into the word they spell.
        s = SPACED_OUT.replace(s) { it.value.replace(" ", "") }
        // 7. A space before a dot closes up; a dot followed by a space is a sentence and stays.
        s = SPACE_BEFORE_DOT.replace(s) { it.groupValues[1] + "." }
        return s
    }

    private inline fun String.filterCodePoints(keep: (Int) -> Boolean): String {
        val out = StringBuilder(length)
        var i = 0
        while (i < length) {
            val cp = codePointAt(i)
            if (keep(cp)) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** What \w means in Unicode: letters, marks, numbers and the underscore. */
    private const val WORD = "\\p{L}\\p{M}\\p{N}_"

    /** A letter, mark or number: \w without the underscore. */
    private const val ALNUM = "[\\p{L}\\p{M}\\p{N}]"

    private val DOT_LOOKALIKES = Regex("[\u3002\uFF61\uFF0E\uFE52\u2024]")

    // Each starts where a run of spaces starts, so a long run is read once, not once per space.
    private val BRACKET_DOT = Regex("(?<!\\s)\\s*[\\[({<]\\s*(?:dot|d0t|\\.)\\s*[\\])}>]\\s*")
    private val SPOKEN_DOT = Regex("(?<=$ALNUM)\\s+d[o0]t\\s+(?=$ALNUM)")
    private val SPACED_OUT = Regex("(?<!\\S)$ALNUM(?: $ALNUM){2,}(?!\\S)")
    private val SPACE_BEFORE_DOT = Regex("($ALNUM)\\s+\\.\\s*(?=$ALNUM)")

    /** R1, a scheme: the named ones with or without slashes, and anything followed by "://". */
    private val SCHEME =
        Regex(
            "(?<![$WORD])(https?|hxxps?|h\\*\\*ps?|ftp|mailto|intent|market|tg|whatsapp|javascript|sms|tel)\\s*:",
        )
    private val SLASHES = Regex("(?<![$WORD])[a-z][a-z0-9+.-]{1,15}\\s*:\\s*/\\s*/")

    /** R2. */
    private val WWW = Regex("(?<![$WORD])www(?![$WORD])")

    /**
     * R3, a dotted word in which a label after the first is a real top-level domain (from
     * [ChatTlds]) and the label before it a valid one. The word is split at its dots rather than
     * matched by one pattern, as the server does, so nothing is read twice. It may start right after
     * an "@", so an email address counts.
     */
    private fun hasDomain(text: String): Boolean =
        DOTTED.findAll(text).any { word ->
            val labels = word.value.split('.')
            (1 until labels.size).any { i -> labels[i] in ChatTlds.all && LABEL.matches(labels[i - 1]) }
        }

    private val DOTTED = Regex("(?<![$WORD-])$ALNUM[$WORD-]*+(?:\\.$ALNUM[$WORD-]*+)++")
    private val LABEL = Regex("$ALNUM(?:[$WORD-]{0,61}$ALNUM)?")

    /** R4, "example. com": a dot and spaces before an ending used almost only for links. */
    private val SPACED_TLD =
        Regex("(?<!$ALNUM)$ALNUM{2,}\\.\\s+(com|org|io|xyz|ru|cn|ly|gg|tk|ml|cf|ga|gq|info|biz|pk|bd)(?![$WORD])")

    /** R5, four numbers 0 to 255 joined by dots. */
    private const val OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])"
    private val IPV4 = Regex("(?<![0-9.])$OCTET(?:\\.$OCTET){3}(?![0-9])")
}
