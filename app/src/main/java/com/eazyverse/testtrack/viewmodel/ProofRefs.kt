package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.data.DriveFile
import com.eazyverse.testtrack.net.Api

/** A proof kept in the sender's own Drive is sent as this followed by the file's id. */
const val DRIVE_PREFIX = "drive:"

/**
 * Where a proof's picture is fetched from. Drive, shared by link, for every proof sent now; our
 * own server, behind the session, for older ones uploaded there; a file for one still on the phone.
 */
fun proofSource(ref: String): String =
    when {
        ref.startsWith(DRIVE_PREFIX) -> DriveFile(ref.removePrefix(DRIVE_PREFIX), "").thumbUrl
        ref.startsWith("file:") -> ref
        else -> Api.proofUrl(ref)
    }
