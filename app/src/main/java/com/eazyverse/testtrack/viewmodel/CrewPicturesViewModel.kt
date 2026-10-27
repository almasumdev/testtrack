package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.crewPictures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One night of one member's, with its picture: "drive:<id>", or empty. */
data class MemberNight(
    val day: Int,
    val at: String,
    val usedMs: Long,
    val proof: String,
    val byHand: Boolean,
    val counts: Boolean,
)

/** One member's nights with the reader's own app. */
data class MemberNights(
    val seatId: String,
    val name: String,
    val day: Int,
    val out: Boolean,
    val nights: List<MemberNight>,
)

/** The reader's app in one crew, as every other member opened it. */
data class AppNights(
    val appName: String,
    val runDays: Int,
    val testers: List<MemberNights>,
)

/** One crew's pictures of your own app, every member and every night: the owner's grid. */
class CrewPicturesViewModel : ViewModel() {
    var pictures by mutableStateOf<AppNights?>(null)
        private set
    var failed by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    fun load(crewId: String) {
        loading = true
        viewModelScope.launch {
            try {
                val got = Api.crewPictures(crewId)
                pictures =
                    AppNights(
                        got.appName,
                        got.runDays,
                        got.testers.map { t ->
                            MemberNights(
                                t.seatId,
                                t.name,
                                t.day,
                                t.out,
                                t.nights.map { MemberNight(it.day, it.at, it.usedMs, it.proof, it.byHand, it.counts) },
                            )
                        },
                    )
                failed = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                failed = e.message
            } catch (_: Exception) {
                failed = "We could not reach TestTrack. Check your connection."
            } finally {
                loading = false
            }
        }
    }
}
