package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The bar across the top of every page you go back from: one arrow, one title, one size.
 *
 * Each page used to bring its own. Some had a chevron and some an arrow; the stock Material bar
 * set titles at 22sp and the hand-made rows at 16sp; and the stock bar added the status bar's
 * height a second time on pages that were already inset, so the arrow sat lower on some pages
 * than others. This is a plain row with no insets of its own, since every page is already inset
 * where it is routed.
 *
 * The back mark is always Material's arrow_back_ios_new, lined up with the page's gutter so it sits
 * over the content's left edge.
 */
@Composable
fun PageBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(PageBarHeight)
            .padding(end = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The 48dp button centres a 20dp glyph 14dp in, and the arrow's stroke starts 5dp into
        // that, so the stroke is 19dp in. Shifted 3dp left it lands on the 16dp gutter.
        IconButton(onClick = onBack, modifier = Modifier.offset(x = Gutter - 19.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_back_ios_new), "Back", Modifier.size(20.dp), tint = Tt.colors.text)
        }
        Text(
            title,
            // Pulled in by the same 3dp and 9dp more, which the shifted button still reserves, so
            // the title sits a short step after the arrow rather than a button's width away.
            Modifier.weight(1f).offset(x = Gutter - 19.dp - 9.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 24.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

val PageBarHeight = 56.dp
