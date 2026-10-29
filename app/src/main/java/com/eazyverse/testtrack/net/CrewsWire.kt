package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Crews, as the server says them: a crew, its seats, and what a member sends about one.
 * See the backend's app/services/crews.py for the rules these fields carry.
 */

@Serializable
data class SeatIn(
    /** Optional. Empty takes the name Play shows for the app. */
    @SerialName("app_name") val appName: String = "",
    /** A package name or a Play link carrying one. The server reads either. */
    @SerialName("package_name") val packageName: String,
    val notes: String = "",
    @SerialName("device_fp") val deviceFp: String,
    val timezone: String = "UTC",
    @SerialName("app_id") val appId: String? = null,
)

@Serializable
data class VisitIn(
    /** Real foreground milliseconds, measured on this phone. The server holds it to its floor. */
    @SerialName("used_ms") val usedMs: Long,
    /** This build takes a picture of each app: the visit counts once its picture is in. */
    val pictures: Boolean = true,
    /** The version on this phone, and who installed it: an open of a copy Play did not put here does not count. */
    @SerialName("version_code") val versionCode: Long? = null,
    val installer: String? = null,
)

/** The owner's own copy, as Android reads it, to tell the crew a newer version is out. */
@Serializable
data class VersionIn(
    @SerialName("version_code") val versionCode: Long,
    @SerialName("version_name") val versionName: String = "",
    val installer: String = "",
)

/** A member's private feedback on Play for one app: its picture in their own Drive. */
@Serializable
data class FeedbackIn(
    @SerialName("proof_ref") val proofRef: String,
    @SerialName("drive_token") val driveToken: String,
)

/** A visit's picture, in the member's own Drive and shared by link. */
@Serializable
data class PictureIn(
    @SerialName("proof_ref") val proofRef: String,
    /** A short-lived Drive grant the server uses once to check the file is ours, never kept. */
    @SerialName("drive_token") val driveToken: String,
    /** Picked from the phone after TestTrack's own picture failed. */
    @SerialName("by_hand") val byHand: Boolean = false,
)

/** One member's pictures of the reader's own app, night by night. */
@Serializable
data class ApiNightPicture(
    val day: Int,
    val at: String,
    @SerialName("used_ms") val usedMs: Long = 0,
    /** "drive:<id>", or empty for a visit from before pictures or one still waiting on its own. */
    val proof: String = "",
    @SerialName("by_hand") val byHand: Boolean = false,
    val counts: Boolean = true,
)

@Serializable
data class ApiTesterPictures(
    @SerialName("seat_id") val seatId: String,
    val name: String,
    /** Their own day of the run, and whether their app has left it. */
    val day: Int = 0,
    val out: Boolean = false,
    val nights: List<ApiNightPicture> = emptyList(),
    /** They reported the app as not installing, so they owe it nothing. */
    val excused: Boolean = false,
    @SerialName("first_night") val firstNight: Int = 1,
    /** Nights the team let them off everything. */
    @SerialName("free_days") val freeDays: List<Int> = emptyList(),
)

/** Every member's pictures of the reader's own app in one crew: the owner's grid. */
@Serializable
data class ApiCrewPictures(
    @SerialName("seat_id") val seatId: String,
    @SerialName("app_name") val appName: String,
    @SerialName("run_days") val runDays: Int = 14,
    /** Nights the app was let off for everybody. */
    @SerialName("off_days") val offDays: List<Int> = emptyList(),
    val testers: List<ApiTesterPictures> = emptyList(),
)

@Serializable
data class SeatVoteIn(
    val installed: Boolean,
    val reason: String = "",
)

@Serializable
data class ReportIn(
    /** What went wrong, for the app's owner. Required. */
    val reason: String,
)

@Serializable
data class ApiSeat(
    val id: String,
    @SerialName("crew_id") val crewId: String,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    /** Play's icon for the app, when it was added through My apps; empty otherwise. */
    val icon: String = "",
    val notes: String = "",
    @SerialName("owner_name") val ownerName: String,
    /** checking, confirmed, rejected, withdrawn or removed. */
    val state: String,
    val mine: Boolean,
    /** Reports filed against it so far. */
    val reports: Int = 0,
    /** This account reported it, which excuses this account from it. */
    val reported: Boolean = false,
    /** For an app the Play check could not look at: members' answers so far, and this account's. */
    val yes: Int = 0,
    val no: Int = 0,
    @SerialName("my_vote") val myVote: Boolean? = null,
    /** What the reports said. Only ever sent to the seat's owner, without who said it. */
    val reasons: List<String> = emptyList(),
    /** Why it left the crew. Only ever sent to the seat's owner. */
    @SerialName("out_reason") val outReason: String = "",
    /** Opened by this account on its own current day. */
    @SerialName("opened_today") val openedToday: Boolean = false,
    /** The owner's own day of the fortnight, in their calendar. Zero before their midnight. */
    val day: Int = 0,
    /** What the owner has opened today, out of what they owe today. */
    @SerialName("done_today") val doneToday: Int = 0,
    @SerialName("owed_today") val owedToday: Int = 0,
    /** The owner has opened everything they owe today. */
    @SerialName("night_done") val nightDone: Boolean = false,
    /** Finished days the owner left short. */
    val missed: Int = 0,
    /** The owner's yesterday was one of them: one more and their app is out. */
    @SerialName("missed_yesterday") val missedYesterday: Boolean = false,
    /** This member opened the reader's own app on their current day, and when (ISO time). */
    @SerialName("opened_mine_today") val openedMineToday: Boolean = false,
    @SerialName("opened_mine_at") val openedMineAt: String? = null,
    /** On how many of their days this member has opened the reader's app. */
    @SerialName("opened_mine_days") val openedMineDays: Int = 0,
    /** Tonight's picture of the reader's own app from this member, and whether it was picked by hand. */
    @SerialName("opened_mine_picture") val openedMinePicture: String = "",
    @SerialName("opened_mine_by_hand") val openedMineByHand: Boolean = false,
    /** The reader opened this app tonight and its picture is not in yet, so it has not counted. */
    @SerialName("picture_due") val pictureDue: Boolean = false,
    /** The owner's newest version, when they told the crew, and whether its 24 hours are over. */
    @SerialName("version_code") val versionCode: Long? = null,
    @SerialName("version_name") val versionName: String = "",
    @SerialName("version_at") val versionAt: String? = null,
    @SerialName("update_required") val updateRequired: Boolean = false,
    /** The reader's Play feedback on this app: sent, and the last of their nights to send it by. */
    @SerialName("feedback_sent") val feedbackSent: Boolean = false,
    @SerialName("feedback_due_day") val feedbackDueDay: Int = 0,
    /** Nobody owes this app the reader's tonight: marked down by its owner, or let off. */
    @SerialName("off_tonight") val offTonight: Boolean = false,
    /** Nights its owner can still mark it down; on the owner's own seat only. */
    @SerialName("downs_left") val downsLeft: Int = 0,
    /** The day this seat's own run starts on: 1 unless the team put it in part-way through. */
    @SerialName("first_night") val firstNight: Int = 1,
    /** Tix taken for this seat and given back for it; -1 from an older server that does not say. */
    val charged: Int = -1,
    val refunded: Int = -1,
    /** When it was taken out of the run (ISO time), for every member; null while it is in. */
    @SerialName("out_at") val outAt: String? = null,
    /** The owner's appeal to get it back, on their own seat only; null when there is none. */
    val appeal: ApiAppeal? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ApiAppeal(
    /** waiting, accepted, refused, or closed (the run ended before an answer). */
    val state: String,
    val text: String = "",
    /** The team's reason, when it refused. */
    val answer: String = "",
)

@Serializable
data class AppealIn(
    val text: String,
)

@Serializable
data class ApiCrew(
    val id: String,
    val number: Int,
    val name: String,
    val state: String,
    /** Confirmed seats. A seat still being checked is somebody's intention, not a place filled. */
    val filled: Int,
    /** Seats holding a place either way, which is what decides whether there is room. */
    val taken: Int,
    /** Those seats' app icons, confirmed first; empty where an app has none. Never names. */
    val icons: List<String> = emptyList(),
    val seats: Int,
    @SerialName("run_days") val runDays: Int,
    /** This account's own day of the fortnight. Zero before their own midnight. */
    val day: Int = 0,
    /** The day this account's own run starts on: 1 unless the team put its app in part-way through. */
    @SerialName("first_night") val firstNight: Int = 1,
    @SerialName("starts_in_ms") val startsInMs: Long = 0,
    @SerialName("my_seat_id") val mySeatId: String? = null,
    @SerialName("filled_at") val filledAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    /** Only sent for a crew this account is in. */
    val members: List<ApiSeat> = emptyList(),
)

@Serializable
data class CrewList(
    val mine: List<ApiCrew> = emptyList(),
    /** The crew a new seat would go into, or null if there is nowhere to go. */
    val open: ApiCrew? = null,
    /** Why a seat cannot be taken right now, in words the screen can show as they arrive. */
    val blocked: String? = null,
)
