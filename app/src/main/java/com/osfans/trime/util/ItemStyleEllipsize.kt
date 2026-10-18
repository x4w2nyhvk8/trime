/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.text.TextUtils
import com.osfans.trime.data.theme.model.ItemStyle

/**
 * The Android truncation mode of a theme's `ellipsize` (doc/Theme.md §8.3):
 * `null` (nothing declared) and [ItemStyle.Ellipsize.NONE] both mean the label
 * is not truncated.
 */
fun ItemStyle.Ellipsize?.toTruncateAt(): TextUtils.TruncateAt? = when (this) {
    null, ItemStyle.Ellipsize.NONE -> null
    ItemStyle.Ellipsize.START -> TextUtils.TruncateAt.START
    ItemStyle.Ellipsize.MIDDLE -> TextUtils.TruncateAt.MIDDLE
    ItemStyle.Ellipsize.END -> TextUtils.TruncateAt.END
    ItemStyle.Ellipsize.MARQUEE -> TextUtils.TruncateAt.MARQUEE
}
