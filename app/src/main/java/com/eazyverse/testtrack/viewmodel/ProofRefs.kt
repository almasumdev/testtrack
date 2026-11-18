package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.net.Api

/** A proof sent by an older build was kept in its sender's own Google Drive and named like this. */
private const val DRIVE_PREFIX = "drive:"

/**
 * Where a proof's picture is fetched from. Our own server, behind the session, for every proof
 * sent now; a file for one still on the phone. An older proof stays where it was put, in its
 * sender's Drive, and is shown by its public link: nothing was copied across.
 */
fun proofSource(ref: String): String =
    when {
        ref.startsWith(DRIVE_PREFIX) -> "https://drive.google.com/thumbnail?id=${ref.removePrefix(DRIVE_PREFIX)}&sz=w1200"
        ref.startsWith("file:") -> ref
        else -> Api.proofUrl(ref)
    }
