/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

/**
 * The `clipboard:` top-level section (doc/Theme.md §8.1): which `styles:` names
 * the clipboard page's slots resolve through. A name that is empty leaves that
 * slot on its built-in default.
 *
 * The list entries are **not** keys: they are runtime clipboard rows, and no key
 * name rule applies to them (doc/Theme.md §8.5).
 */
@Serializable
data class Clipboard(
    val panelStyle: String = "",
    /** The slot of an ordinary text row (§8.4). */
    val itemStyle: String = "",
    /** The slot of a pinned row, which shows the pin marker (§8.4). */
    val pinnedItemStyle: String = "",
    /** The slot of the title row, including the delete-all button (§8.4). */
    val titleItemStyle: String = "",
)
