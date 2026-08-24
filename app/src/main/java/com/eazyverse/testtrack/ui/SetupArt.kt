package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.R

/**
 * The page itself, twice, with the difference between the two marked.
 *
 * Drawn from the real thing rather than approximated, because the whole problem is where a
 * particular button sits relative to a particular message, and a tidied-up illustration would put
 * them wherever it was convenient. The ring and the dashed box are the only things added.
 *
 * Above: the way in, which is small, at the top, and easy to miss under a refusal set three times
 * its size. Below: the same page on the wrong Google account, where the button is simply absent,
 * which is a different problem with a different fix and looks almost identical until you know
 * where to look.
 */
@Composable
fun GroupPageShots() {
    Column(Modifier.fillMaxWidth()) {
        Shot(
            R.drawable.group_page_join,
            296.dp,
            "Tap the button. When it disappears, you have joined.",
        )
        Spacer(Modifier.height(14.dp))
        Shot(
            R.drawable.group_page_no_join,
            281.dp,
            // No caption. The picture is the same page without the button, which is the whole of
            // what it has to say, and the paragraph above already covers the two reasons for that.
            null,
        )
    }
}

/**
 * The four screens of the restricted setting, in the order they happen.
 *
 * This step used to carry six numbered instructions and a paragraph explaining why they were
 * needed, which is the longest piece of writing in the app and describes a route through somebody
 * else's Settings. Every one of those screens looks like every other screen in Settings, so the
 * words had to do all the identifying: the three dot menu in the top right corner, the switch that
 * says Controlled by Restricted Setting.
 *
 * Pictures do that better and in a quarter of the space. The order is the instruction, and the
 * only text left is what a picture cannot say: that nothing is wrong with the phone, and that the
 * menu in the third one does not exist until the message in the second has been seen once.
 */
@Composable
fun RestrictedSettingShots() {
    Column(Modifier.fillMaxWidth()) {
        Shot(R.drawable.usage_switch, 184.dp, "Tap the switch, even though it looks disabled.")
        Spacer(Modifier.height(14.dp))
        Shot(
            R.drawable.usage_denied,
            322.dp,
            "Close this. It has to be seen once before the menu below appears.",
        )
        Spacer(Modifier.height(14.dp))
        Shot(
            R.drawable.usage_menu,
            112.dp,
            "Settings, then Apps, then TestTrack. Open the three dot menu.",
        )
        Spacer(Modifier.height(14.dp))
        Shot(
            R.drawable.usage_allow,
            121.dp,
            "Choose this and confirm with your PIN. The switch will work now.",
        )
    }
}

/**
 * [height] is stated rather than derived, which looks like the lazy way round and is not.
 *
 * The rail this sits inside is a Row measured at IntrinsicSize.Min, so the connector can run the
 * height of whatever the step turns out to be. Intrinsic measurement asks a child how tall it
 * wants to be without telling it how wide it will end up, and an image sized from its own aspect
 * ratio cannot answer that: it reports something far too short, gets laid out at that height, and
 * loses its top. Both screenshots came out missing the title row, which is where the button and
 * the ring are, so the picture lost the only thing it was there to show.
 *
 * A stated height answers the intrinsic query honestly, and FillWidth then fills that width on any
 * screen. Fit was there first and was wrong in a quieter way: it scales to whichever side runs out
 * of room first, so on a narrower phone the picture shrank and left the column with a margin down
 * both sides.
 *
 * Every height here is a little larger than the picture needs at the width this column has, so
 * what is left over is a band above and below rather than a piece taken off the top. Against the
 * page ground that band is invisible.
 */
@Composable
private fun Shot(
    id: Int,
    height: Dp,
    caption: String?,
) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    )
    caption?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.4f,
        )
    }
}
