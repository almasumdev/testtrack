package com.eazyverse.testtrack.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.R

/**
 * One link in the About page's community grid. A brand mark is a [logo] drawable that keeps its own
 * colours; the app's own site is a [vector] drawn in the accent ink. [tintWithInk] is for the one
 * brand mark that is a single colour (GitHub), drawn in the page's text ink so it reads on both
 * grounds. The label says only where the link goes.
 */
data class CommunityLink(
    @DrawableRes val logo: Int?,
    val vector: ImageVector?,
    val label: String,
    val url: String,
    val tintWithInk: Boolean = false,
)

private val WHATSAPP_GROUP_LINK = CommunityLink(R.drawable.ic_brand_whatsapp, null, "WhatsApp group", WHATSAPP_GROUP)
private val WHATSAPP_CHANNEL_LINK =
    CommunityLink(R.drawable.ic_brand_whatsapp_channel, null, "WhatsApp channel", WHATSAPP_CHANNEL)
private val TELEGRAM_LINK = CommunityLink(R.drawable.ic_brand_telegram, null, "Telegram", TELEGRAM_GROUP)
private val WEBSITE_LINK = CommunityLink(null, Lucide.Globe, "Website", APP_SITE)
private val GITHUB_LINK = CommunityLink(R.drawable.ic_brand_github, null, "GitHub", SOURCE_REPO, tintWithInk = true)
private val YOUTUBE_LINK = CommunityLink(R.drawable.ic_brand_youtube, null, "YouTube", YOUTUBE)

/** Defined, but not in [COMMUNITY] yet. */
internal val FACEBOOK_LINK = CommunityLink(R.drawable.ic_brand_facebook, null, "Facebook group", FACEBOOK_GROUP)

/** The grid's links, in order. A link with no address yet is left out. */
internal val COMMUNITY: List<CommunityLink> =
    listOf(WHATSAPP_GROUP_LINK, WHATSAPP_CHANNEL_LINK, TELEGRAM_LINK, WEBSITE_LINK, GITHUB_LINK, YOUTUBE_LINK)
        .filter { it.url.isNotBlank() }
