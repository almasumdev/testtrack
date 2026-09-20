package com.eazyverse.testtrack.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiMyApp
import com.eazyverse.testtrack.net.ApiProfile
import com.eazyverse.testtrack.net.BoardRow
import com.eazyverse.testtrack.net.MeIn
import kotlinx.coroutines.launch

/*
 * The three read-only tabs.
 *
 * Each does one thing: fetch, hold what came back, and say whether it is still waiting or has
 * given up. None of them computes anything, which is why there is no arithmetic here to test —
 * the figures are the server's and the screens draw them.
 *
 * Two flags rather than one, and the split is the point. "Offline" used to mean both "we could
 * not reach the server" and "nobody is signed in", which was harmless while every endpoint needed
 * a token and both meant an empty screen. The board now answers without one, so conflating them
 * would tell somebody to check their connection while the server was answering perfectly well.
 *
 * The loading and offline pair is repeated rather than shared, because a base class that only
 * removes six lines each is a class somebody has to read before they can read the six.
 */

class RanksViewModel : ViewModel() {
    var rows by mutableStateOf<List<BoardRow>>(emptyList())
        private set

    /** Null for a reader with no account, which is what the server sends back for a stranger. */
    var mine by mutableStateOf<BoardRow?>(null)
        private set

    /*
     * The weighting the server sends is not held here any more.
     *
     * It was read by a card at the top of the board explaining the arithmetic, which is a
     * reference page pretending to be a leaderboard. Nothing reads it now, and a view model
     * holding a value for nobody is a value that drifts.
     */

    var loading by mutableStateOf(true)
        private set

    var offline by mutableStateOf(false)
        private set

    /**
     * The board, signed in or not.
     *
     * No session check before the call. A stranger gets the same rows and no row of their own,
     * which is the honest answer and the reason to show them a board at all: it is the only
     * evidence on the whole screen that there are people in here doing the work.
     */
    fun load(context: Context) {
        viewModelScope.launch {
            try {
                ApiAuth.ensure(context)
                val answer = Api.board()
                rows = SampleBoard.pad(answer.rows)
                mine = answer.mine
                offline = false
            } catch (_: ApiError) {
                // A 401 here is no longer the ordinary signed-out case, so it is a real fault.
                offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }
}

class AppsViewModel : ViewModel() {
    var apps by mutableStateOf<List<ApiMyApp>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var offline by mutableStateOf(false)
        private set

    /** Nobody is signed in. Not a fault, and not the same thing as being unable to reach us. */
    var signedOut by mutableStateOf(false)
        private set

    fun load(context: Context) {
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    signedOut = true
                    return@launch
                }
                signedOut = false
                apps = Api.myApps().apps
                offline = false
            } catch (e: ApiError) {
                if (e.isAuth) signedOut = true else offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }
}

class MeViewModel : ViewModel() {
    var profile by mutableStateOf<ApiProfile?>(null)
        private set

    var loading by mutableStateOf(true)
        private set

    var offline by mutableStateOf(false)
        private set

    var signedOut by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /**
     * Change the name, and the face if a new one was chosen.
     *
     * The picture goes up first because the second call needs what the first returns. Leaving the
     * picture alone sends back what is already there, since the endpoint takes both fields and a
     * missing one would read as "clear it".
     */
    fun save(
        context: Context,
        name: String,
        photo: Uri?,
        onDone: () -> Unit,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val ref =
                    if (photo == null) {
                        profile?.photoUrl.orEmpty()
                    } else {
                        val resolver = context.contentResolver
                        val bytes =
                            resolver.openInputStream(photo)?.use { it.readBytes() }
                                ?: throw ApiError(400, "unreadable", "That image could not be read.")
                        Api.upload(bytes, resolver.getType(photo) ?: "image/jpeg").id
                    }
                Api.updateMe(MeIn(displayName = name.trim(), photoRef = ref))
                load(context)
                onDone()
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
            } finally {
                busy = false
            }
        }
    }

    fun load(context: Context) {
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    signedOut = true
                    return@launch
                }
                signedOut = false
                profile = Api.profile()
                offline = false
            } catch (e: ApiError) {
                if (e.isAuth) signedOut = true else offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }
}

/**
 * People to look at, while there are almost none.
 *
 * Debug builds only, and it returns the server's answer untouched in a release one. The board has
 * two members on it at the moment, so every screen built for it — the podium, a hundred rows, a
 * line pinned to the bottom because yours is somewhere far below — is a screen nobody can see.
 * That is not a thing to find out after it ships.
 *
 * The real rows are not altered and keep their places and their scores. These slot in underneath
 * the last of them, with scores that only ever go down, so the board still reads as a board. Ids
 * are prefixed so that nothing here can ever be mistaken for somebody.
 */
private object SampleBoard {
    private val names =
        listOf(
            "Tanvir",
            "Nadia",
            "Shafin",
            "Imran",
            "Farzana",
            "Mahin",
            "Sadia",
            "Rakib",
            "Arif",
            "Nusrat",
            "Hasan",
            "Prottoy",
            "Labiba",
            "Zayed",
            "Kabir",
            "Jarin",
            "Omar",
            "Sabbir",
            "Tahmid",
            "Ruhul",
            "Afsana",
            "Bashir",
            "Munira",
            "Dipu",
        )

    /** How long a board has to be before every state of the screen has somewhere to happen. */
    private const val ENOUGH = 40

    fun pad(real: List<BoardRow>): List<BoardRow> {
        if (!BuildConfig.DEBUG || real.size >= ENOUGH) return real

        val floor = real.lastOrNull()?.points ?: 30
        val span = ENOUGH - real.size
        val drop = ((floor - 1) / span).coerceAtLeast(1)

        return real +
            (0 until span).map { step ->
                val place = real.size + step + 1
                val points = (floor - 1 - step * drop).coerceAtLeast(1)
                // Split back into the two things that earned it, so the line under the name is
                // arithmetic somebody could check rather than a number made up beside it.
                val reviews = if (place % 6 == 0 && points > 5) 1 else 0
                BoardRow(
                    id = "sample-$place",
                    name = names[step % names.size],
                    place = place,
                    opens = points - reviews * 5,
                    reviews = reviews,
                    points = points,
                )
            }
    }
}
