package com.eazyverse.testtrack.fixture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatPicture
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatState
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.ui.ChatView
import com.eazyverse.testtrack.ui.Crew
import com.eazyverse.testtrack.ui.CrewApp
import com.eazyverse.testtrack.ui.CrewMember
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.SeatState
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Invented people, messages, pictures and crews for the chat gallery. Nothing here is real. */
internal class GallerySamples(
    context: Context,
) {
    val portrait = pictureFile(context, "portrait", 900, 1600, 205f)
    val landscape = pictureFile(context, "landscape", 1600, 900, 28f)
    val tall = pictureFile(context, "tall", 900, 2600, 150f)

    /** The editor's picture, held in memory as the editor holds it. */
    val editorBitmap: Bitmap = drawPicture(1080, 1440, "Editor", 260f)

    val people: Map<String, ChatPerson> =
        listOf(
            ChatPerson(ME, "Arjun Mehta", appName = "Focus Flow", packageName = "com.example.focusflow", day = 6, mine = true),
            ChatPerson(KENJI, "Kenji Sato", appName = "Steps Daily: Walk Tracker", packageName = "com.example.steps", day = 6),
            ChatPerson(NADIA, "Nadia Karim", appName = "Tiny Tasks", packageName = "com.example.tinytasks", day = 6),
            ChatPerson(SOFIA, "Sofia Alvarez", appName = "Pocket Recipes", packageName = "com.example.recipes", day = 6),
            ChatPerson(DANIEL, "Daniel Okafor", appName = "Lingo Cards", packageName = "com.example.lingo", day = 5),
            ChatPerson(ZARA, "Zara Ahmed", appName = "Pixel Weather", packageName = "com.example.weather", day = 6),
            ChatPerson(LUCAS, "Lucas Moreau", appName = "Habit Garden", packageName = "com.example.habits", day = 6),
            ChatPerson(GRACE, "Grace Liu", appName = "Lift Log", packageName = "com.example.lift", day = 6),
            ChatPerson(EMMA, "Emma Lindqvist", appName = "Sleep Sounds", packageName = "com.example.sleep", day = 7),
            ChatPerson(PRIYA, "Priya Nair", appName = "Plant Pal", packageName = "com.example.plants", day = 6),
        ).associateBy { it.seatId }

    /** A chat page with nothing drawn yet: the open crew, a full day's allowance. */
    fun view(rows: List<ChatRow> = emptyList()) =
        ChatView(
            crewName = CREW,
            rows = rows,
            people = people,
            mySeat = ME,
            messagesLeft = 10,
            picturesLeft = 3,
            members = true,
        )

    fun name(line: ChatLine): String = if (line.mine) "You" else people[line.authorSeatId]?.name ?: "Someone"

    // Lines, one at a time.

    fun msg(
        id: String,
        who: String,
        text: String,
        at: Instant = today(20, 15),
        seq: Long = 0,
        quote: ChatQuote? = null,
        picture: ChatPicture? = null,
        send: SendState = SendState.SENT,
        error: String = "",
        deleted: Boolean = false,
    ) = ChatLine(
        id = id,
        seq = seq,
        rev = seq,
        kind = ChatKind.USER,
        authorSeatId = who,
        mine = who == ME,
        text = text,
        quote = quote,
        picture = picture,
        deleted = deleted,
        at = at,
        send = send,
        error = error,
    )

    fun bot(
        id: String,
        text: String,
        event: String,
        at: Instant = today(0, 1),
        seq: Long = 0,
    ) = ChatLine(id = id, seq = seq, rev = seq, kind = ChatKind.BOT, text = text, event = event, at = at)

    fun team(
        id: String,
        text: String,
        at: Instant = today(9, 30),
        seq: Long = 0,
    ) = ChatLine(id = id, seq = seq, rev = seq, kind = ChatKind.TEAM, text = text, at = at)

    /** A picture the server holds, read by its address, as a sent one is. */
    fun sent(
        uri: String,
        width: Int,
        height: Int,
    ) = ChatPicture(id = uri, width = width, height = height, expiresAt = Instant.now().plus(Duration.ofHours(21)))

    /** A picture still on this phone, as one being sent or one that failed is. */
    fun local(
        uri: String,
        width: Int,
        height: Int,
    ) = ChatPicture(id = "local-${uri.hashCode()}", width = width, height = height, local = uri)

    val expired = ChatPicture(id = "gone", width = 900, height = 1600, expired = true)

    val longText =
        "Quick report on Pocket Recipes after a full evening with it. The search finds recipes fast, " +
            "but the filter for vegetarian resets every time I go back to the list. Saving a recipe " +
            "works, though the heart only fills after a second tap. The timer kept running with the " +
            "screen off, which is great. One crash when I rotated the phone on the shopping list."

    // The full thread: yesterday's evening, then tonight, with four new since the reader last looked.

    val thread: List<ChatLine> =
        listOf(
            bot("t1", "Night 5 started. Open 9 apps tonight.", "night", yesterday(0, 1), 1),
            msg("t2", KENJI, "Evening all. Opened 6 so far, the rest after dinner.", yesterday(19, 42), 2),
            msg("t3", NADIA, "Same here. ${app(ME)} asks me to sign in on first open, is that expected?", yesterday(19, 50), 3),
            msg(
                "t4",
                ME,
                "Yes, that's the Google sign-in. Tap Not now at the bottom to skip it.",
                yesterday(20, 4),
                4,
                quote = ChatQuote("t3", NADIA, "Same here. Focus Flow asks me to sign in on first open, is that expected?"),
            ),
            msg("t5", ME, "Thanks for checking it so carefully.", yesterday(20, 5), 5),
            bot("t6", "Night 6 started. 9 apps to open tonight.", "night", today(0, 1), 6),
            team(
                "t7",
                "Play is slow to show new testers today. If an app says it isn't available, wait an hour and try again.",
                today(9, 30),
                7,
            ),
            msg(
                "t8",
                SOFIA,
                "${app(KENJI)} closed on me when I tapped Start. Screenshot here.",
                today(19, 12),
                8,
                picture = sent(landscape, 1600, 900),
            ),
            msg("t9", KENJI, "${seat(SOFIA)} thanks, which phone is that?", today(19, 20), 9),
            msg("t10", KENJI, "${seat(ME)} did yours open fine tonight?", today(19, 21), 10),
            msg("t11", DANIEL, "", today(19, 33), 11, picture = sent(portrait, 900, 1600)),
            msg(
                "t12",
                ME,
                "All good on mine, opened it a minute ago.",
                today(19, 40),
                12,
                quote = ChatQuote("t10", KENJI, "@Arjun Mehta did yours open fine tonight?"),
            ),
            msg("t13", ME, "Will check Steps Daily again after the update.", Instant.now(), send = SendState.SENDING),
        )

    /** The thread laid out as the page lays it out: day chips, and the "new" line over Sofia's picture. */
    fun threadRows(): List<ChatRow> = ChatThread.rows(thread, newFromRev = 7)

    // Crews, for the tiles and the floating button.

    private val others = people.values.filterNot { it.mine }

    private fun seats(checking: Int = 0): List<CrewSeat> =
        people.values.mapIndexed { i, p ->
            CrewSeat(
                id = p.seatId,
                appName = p.appName,
                owner = p.name,
                packageName = p.packageName,
                crew = CREW,
                state = if (i >= people.size - checking) SeatState.CHECKING else SeatState.CONFIRMED,
                mine = p.mine,
            )
        }

    private fun members(): List<CrewMember> =
        people.values.mapIndexed { i, p ->
            CrewMember(
                id = p.seatId,
                name = p.name,
                appName = p.appName,
                day = 6,
                doneToday = if (p.seatId == KENJI) 4 else 9,
                owedToday = 9,
                nightDone = p.seatId != KENJI,
                mine = p.mine,
                openedMineToday = i % 3 != 0,
                openedMineDays = 6,
                packageName = p.packageName,
            )
        }

    private fun apps(): List<CrewApp> =
        others.mapIndexed { i, p ->
            CrewApp(id = p.seatId, label = p.appName, packageName = p.packageName, owner = p.name, crew = CREW, done = i < 4, day = 6)
        }

    fun run(
        unread: Int,
        muted: Boolean = false,
    ) = Crew(
        id = "crew-05",
        name = CREW,
        phase = CrewPhase.RUN,
        taken = 15,
        filled = 15,
        seats = seats(),
        members = members(),
        apps = apps(),
        strip = apps(),
        day = 6,
        chatState = ChatState.OPEN,
        chatUnread = unread,
        chatMuted = muted,
    )

    fun lobby(
        unread: Int,
        muted: Boolean = false,
    ) = Crew(
        id = "crew-06",
        name = "Crew 06",
        phase = CrewPhase.LOBBY,
        filling = true,
        taken = 10,
        filled = 9,
        seats = seats(checking = 1).map { it.copy(crew = "Crew 06") },
        chatState = ChatState.OPEN,
        chatUnread = unread,
        chatMuted = muted,
    )

    /** A finished crew whose chat can still be read. */
    fun record() =
        run(0).copy(
            id = "crew-04",
            name = "Crew 04",
            phase = CrewPhase.RECORD,
            finishedDays = 14,
            chatState = ChatState.READ_ONLY,
            detailsUntil = Instant.now().plus(Duration.ofDays(9)).toString(),
        )

    companion object {
        const val CREW = "Crew 05"
        const val ME = "a0000001"
        const val KENJI = "a0000002"
        const val NADIA = "a0000003"
        const val SOFIA = "a0000004"
        const val DANIEL = "a0000005"
        const val ZARA = "a0000006"
        const val LUCAS = "a0000007"
        const val GRACE = "a0000008"
        const val EMMA = "a0000009"
        const val PRIYA = "a000000a"

        fun seat(id: String) = "@[seat:$id]"

        fun app(id: String) = "@[app:$id]"

        private val zone: ZoneId get() = ZoneId.systemDefault()

        fun today(
            hour: Int,
            minute: Int,
        ): Instant =
            LocalDate
                .now(zone)
                .atTime(hour, minute)
                .atZone(zone)
                .toInstant()

        fun yesterday(
            hour: Int,
            minute: Int,
        ): Instant =
            LocalDate
                .now(zone)
                .minusDays(1)
                .atTime(hour, minute)
                .atZone(zone)
                .toInstant()
    }
}

/** A made-up app screen as a PNG in the cache, as a file address the chat's image loading reads. */
private fun pictureFile(
    context: Context,
    name: String,
    width: Int,
    height: Int,
    hue: Float,
): String {
    val dir = File(context.cacheDir, "chat-gallery").apply { mkdirs() }
    val file = File(dir, "$name.png")
    if (!file.exists()) {
        val bitmap = drawPicture(width, height, name.replaceFirstChar(Char::uppercase), hue)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    return Uri.fromFile(file).toString()
}

/** A gradient with stripes and a few cards on it, labelled with its name and size. */
private fun drawPicture(
    width: Int,
    height: Int,
    label: String,
    hue: Float,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val w = width.toFloat()
    val h = height.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val top = Color.HSVToColor(floatArrayOf(hue, 0.5f, 0.95f))
    val bottom = Color.HSVToColor(floatArrayOf((hue + 70f) % 360f, 0.75f, 0.55f))
    paint.shader = LinearGradient(0f, 0f, w, h, top, bottom, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    paint.color = Color.argb(34, 255, 255, 255)
    paint.strokeWidth = w / 30f
    var x = -h
    while (x < w) {
        canvas.drawLine(x, h, x + h, 0f, paint)
        x += w / 9f
    }
    val side = w * 0.08f
    val corner = w * 0.03f
    paint.color = Color.argb(215, 255, 255, 255)
    canvas.drawRoundRect(RectF(side, h * 0.05f, w - side, h * 0.05f + w * 0.12f), corner, corner, paint)
    paint.color = Color.argb(150, 255, 255, 255)
    var y = h * 0.05f + w * 0.18f
    repeat(3) {
        canvas.drawRoundRect(RectF(side, y, w - side, y + w * 0.2f), corner, corner, paint)
        y += w * 0.26f
    }
    paint.color = Color.WHITE
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = w / 11f
    canvas.drawText(label, side, h - side - w / 14f, paint)
    paint.typeface = Typeface.DEFAULT
    paint.textSize = w / 22f
    canvas.drawText("$width x $height", side, h - side, paint)
    return bitmap
}
