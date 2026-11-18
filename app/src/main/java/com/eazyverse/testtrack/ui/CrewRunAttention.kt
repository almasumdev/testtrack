package com.eazyverse.testtrack.ui

internal enum class CrewRunAttentionKind { FEEDBACK, UPDATE, MIXED }

internal data class CrewRunAttention(
    val apps: List<CrewApp>,
    val kind: CrewRunAttentionKind,
) {
    val title: String
        get() {
            val prefix = "${apps.size} ${if (apps.size == 1) "app needs" else "apps need"}"
            return when (kind) {
                CrewRunAttentionKind.FEEDBACK -> "$prefix feedback"
                CrewRunAttentionKind.UPDATE -> "$prefix an update"
                CrewRunAttentionKind.MIXED -> "$prefix attention"
            }
        }
    val explanation: String
        get() =
            when (kind) {
                CrewRunAttentionKind.FEEDBACK -> "Give private feedback on Google Play for these apps before their tests can count."
                CrewRunAttentionKind.UPDATE ->
                    "The installed copies are older than the required versions. " +
                        "Update from Google Play before testing."
                CrewRunAttentionKind.MIXED ->
                    "Some apps need private feedback; others need a Google Play update. " +
                        "View the list for each action."
            }
}

/** Apps omitted by Run all, after installs and pending pictures have been explained separately. */
internal fun crewRunAttention(
    tonight: List<CrewApp>,
    actions: CrewActions,
): CrewRunAttention? {
    val ready = openAllOf(tonight, { actions.copyOf(it) }, actions.sendingPicture).map { it.id }.toSet()
    val blocked = tonight.filter { !it.done && !it.reported && it.id !in ready }.distinctBy { it.id }
    if (blocked.isEmpty()) return null
    val kind =
        when {
            blocked.all { it.feedbackLate } -> CrewRunAttentionKind.FEEDBACK
            blocked.all {
                refusedHere(
                    it,
                    actions.copyOf(it.packageName),
                ) &&
                    actions.fromPlay(it.packageName)
            } -> CrewRunAttentionKind.UPDATE
            else -> CrewRunAttentionKind.MIXED
        }
    return CrewRunAttention(blocked, kind)
}
