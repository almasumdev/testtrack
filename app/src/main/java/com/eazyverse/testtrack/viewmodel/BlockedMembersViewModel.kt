package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.blocks
import com.eazyverse.testtrack.net.unblock
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A member the reader blocked. [photo] is blank with none; [since] is when, as the server says it. */
data class BlockedMember(
    val userId: String,
    val name: String,
    val photo: String,
    val since: String,
)

/** The members the reader blocked, from Settings, with a way to unblock each. */
class BlockedMembersViewModel : ViewModel() {
    /** Null until the first read lands. */
    var members by mutableStateOf<List<BlockedMember>?>(null)
        private set

    /** The first read failed, in words: the page then offers Try again. Never set once there is a list. */
    var failed by mutableStateOf<String?>(null)
        private set

    /** The member whose Unblock is on its way; one at a time. */
    var busy by mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            try {
                members = Api.blocks().map { BlockedMember(it.userId, it.name, it.photo, it.blockedAt) }
                failed = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                if (members == null) failed = e.message else Snacks.error(e.message)
            } catch (_: Exception) {
                if (members == null) failed = Snacks.OFFLINE_LINE else Snacks.offline { load() }
            }
        }
    }

    /** Shows their messages again. The row goes when the server has said so; there is no thank-you. */
    fun unblock(member: BlockedMember) {
        if (busy != null) return
        busy = member.userId
        viewModelScope.launch {
            try {
                Api.unblock(member.userId)
                members = members?.filterNot { it.userId == member.userId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                Snacks.error(e.message)
            } catch (_: Exception) {
                Snacks.offline { unblock(member) }
            } finally {
                busy = null
            }
        }
    }
}
