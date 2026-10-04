package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eazyverse.testtrack.R

/**
 * The bar across the top of every page you go back from.
 *
 * Material's own top app bar, set up the way the review page's always was: transparent, no window
 * insets of its own (every page is already inset where it is routed, and the stock bar adding the
 * status bar again is what sat some arrows lower than others), a bold title, and anything extra
 * in the actions slot with the page's gutter after it. The back mark is always Material's
 * arrow_back_ios_new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
        title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back_ios_new), "Back")
            }
        },
        actions = {
            Box(Modifier.padding(end = Gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}
