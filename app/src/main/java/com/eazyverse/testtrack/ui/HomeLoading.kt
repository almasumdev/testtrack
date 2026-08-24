package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Home, before any of it has arrived.
 *
 * Its own rather than [SkeletonPage], which is built for the group and dashboard screens: one
 * header, one list, and an action button sitting under the header. Home is not that shape. It has
 * a headline over a meter, then cohorts, then apps still waiting for one, and its button is at the
 * bottom under all of it.
 *
 * Drawn long on purpose. The old one stopped after five short rows against a full-height screen,
 * which reads as a finished page with very little on it rather than a page still loading, and home
 * is the screen most likely to be opened cold on a slow connection.
 */
@Composable
fun HomeSkeleton() {
    Spacer(Modifier.height(8.dp))
    Column(Modifier.padding(horizontal = Gutter, vertical = 4.dp)) {
        Skeleton(width = 148.dp, height = 34.dp, corner = 10.dp)
        Spacer(Modifier.height(12.dp))
        Skeleton(width = 210.dp, height = 12.dp)
        Spacer(Modifier.height(14.dp))
        SkeletonMeter()
    }

    Spacer(Modifier.height(30.dp))
    Rows { SkeletonCohortList(3) }

    // The apps waiting on an admin. Two, because more than that is unusual and a skeleton that
    // promises a queue nobody has is worse than one that stops short.
    Spacer(Modifier.height(26.dp))
    Rows { SkeletonAppList(2) }

    // Where Submit an app lands, at the bottom rather than under the header.
    Spacer(Modifier.height(26.dp))
    Column(Modifier.padding(horizontal = Gutter)) {
        Skeleton(height = 52.dp, corner = 16.dp)
    }
    Spacer(Modifier.height(24.dp))
}
