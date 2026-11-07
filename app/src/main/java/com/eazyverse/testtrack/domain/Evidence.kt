package com.eazyverse.testtrack.domain

/**
 * What each side put in front of the members in a dispute: the owner's picture with the refusal,
 * and the reviewer's one answer (words and a picture, either may be blank).
 */
data class Evidence(
    val ownerProofRef: String = "",
    val reply: String = "",
    val replyProofRef: String = "",
) {
    val isEmpty: Boolean get() = ownerProofRef.isBlank() && reply.isBlank() && replyProofRef.isBlank()
}
