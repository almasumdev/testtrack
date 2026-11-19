package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "@word" being typed, and who it may mean. */
class MentionFinderTest {
    private val amir = ChatPerson("s1", "Amir Haddad", appName = "Pixel Pal: Photo Fixer")
    private val kenji = ChatPerson("s2", "Kenji Sato", appName = "Tidy Notes")
    private val sofia = ChatPerson("s3", "Sofia Marin", appName = "Loop Timer")
    private val me = ChatPerson("s4", "Me Myself", appName = "Mine App", mine = true)
    private val gone = ChatPerson("s5", "Amy Left", appName = "Amy App", inCrew = false)
    private val crew = listOf(amir, kenji, sofia, me, gone)

    private fun labels(
        people: List<ChatPerson>,
        query: String,
    ) = MentionFinder.options(people, query).map { (if (it.app) "app:" else "") + it.name }

    private fun names(query: String) = labels(crew, query)

    @Test
    fun `the at word the cursor is right after`() {
        assertEquals(MentionQuery(0, ""), MentionFinder.queryAt("@", 1))
        assertEquals(MentionQuery(4, "ke"), MentionFinder.queryAt("Hey @ke", 7))
        assertEquals(MentionQuery(4, "k"), MentionFinder.queryAt("Hey @ke", 6))
        assertEquals(MentionQuery(4, ""), MentionFinder.queryAt("Hey @ke", 5))
    }

    @Test
    fun `a few words after the at still count, a leading space does not`() {
        assertEquals(MentionQuery(0, "Amir Ha"), MentionFinder.queryAt("@Amir Ha", 8))
        assertNull(MentionFinder.queryAt("@ ", 2))
        assertNull(MentionFinder.queryAt("@one two three four five", 24))
    }

    @Test
    fun `no list away from an at word`() {
        assertNull(MentionFinder.queryAt("", 0))
        assertNull(MentionFinder.queryAt("Hey there", 9))
        assertNull(MentionFinder.queryAt("mail@kenji", 10))
        assertNull(MentionFinder.queryAt("@ke\nhello", 9))
        assertNull(MentionFinder.queryAt("Hey @ke", 3))
        assertNull(MentionFinder.queryAt("Hey @ke", 99))
    }

    @Test
    fun `a mention already picked does not open the list again`() {
        val span = MentionSpan(4, 16, false, "s1")
        assertNull(MentionFinder.queryAt("Hey @Amir Haddad ", 17, listOf(span)))
        assertNull(MentionFinder.queryAt("Hey @Amir Haddad", 16, listOf(span)))
        assertEquals(MentionQuery(17, "k"), MentionFinder.queryAt("Hey @Amir Haddad @k", 19, listOf(span)))
    }

    @Test
    fun `an empty query lists the crew's others, members then apps, never yourself`() {
        assertEquals(
            listOf("Amir Haddad", "Kenji Sato", "Sofia Marin", "app:Loop Timer", "app:Pixel Pal: Photo Fixer", "app:Tidy Notes"),
            names(""),
        )
    }

    @Test
    fun `it filters by what is typed, name starts first, then word starts, ignoring case`() {
        assertEquals(listOf("Kenji Sato"), names("KEN"))
        // "ha" starts a word of Amir Haddad; "sa" starts Sato's word.
        assertEquals(listOf("Amir Haddad"), names("ha"))
        assertEquals(listOf("Kenji Sato"), names("sa"))
    }

    @Test
    fun `a name that starts with it ranks above one that only has a word starting with it`() {
        val a = ChatPerson("a", "Bo Sam")
        val b = ChatPerson("b", "Sam Lee")
        assertEquals(listOf("Sam Lee", "Bo Sam"), MentionFinder.options(listOf(a, b), "sam").map { it.name })
    }

    @Test
    fun `apps match on their app name and come up when asked for`() {
        assertEquals(listOf("app:Tidy Notes"), names("tidy"))
        assertEquals(listOf("app:Pixel Pal: Photo Fixer"), names("photo"))
    }

    @Test
    fun `a trailing space needs a name that goes on with a space`() {
        assertEquals(listOf("Amir Haddad"), names("amir "))
        assertEquals(emptyList<String>(), names("kenji x"))
        assertEquals(emptyList<String>(), names("zzz"))
    }

    @Test
    fun `people with no name or no app give no row for it`() {
        val plain = ChatPerson("p", "Pat", appName = "")
        val nameless = ChatPerson("q", "", appName = "Ghost App")
        assertEquals(listOf("Pat", "app:Ghost App"), labels(listOf(plain, nameless), ""))
    }
}
