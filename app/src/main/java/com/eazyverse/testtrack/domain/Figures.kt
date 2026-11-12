package com.eazyverse.testtrack.domain

import java.text.NumberFormat
import java.util.Locale

/** A whole number with the phone's thousands separators, e.g. 12,450 or 12.450. */
fun grouped(
    n: Int,
    locale: Locale = Locale.getDefault(),
): String = NumberFormat.getIntegerInstance(locale).format(n)
