package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
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
 * The arrow's glyph lines up with the page's gutter, so it sits over the content's left edge.
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
            // The 48dp button centres a 22dp glyph 13dp in, so starting it 3dp out puts the glyph
            // on the 16dp gutter.
            .padding(start = Gutter - 13.dp, end = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ArrowLeft, "Back", Modifier.size(22.dp), tint = Tt.colors.text)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            Modifier.weight(1f),
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
