/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

/**
 * The `option_panel:` top-level section (doc/Theme.md §8.1): which `styles:`
 * names the switch/option panel's slots resolve through. A name that is empty
 * leaves that slot on its built-in default.
 *
 * The panel's entries are **not** keys: they are not `preset_keys`, and no key
 * name rule applies to them (doc/Theme.md §8.5).
 */
@Serializable
data class OptionPanel(
    val panelStyle: String = "",
    /** The slot of the panel's own rows (`SwitchOptionEntry.Static`, §8.4). */
    val actionItemStyle: String = "",
    /** The slot of a Rime switch row (`SwitchOptionEntry.Custom`, §8.4). */
    val switchItemStyle: String = "",
    /** The slot of a group/title row (§8.4). */
    val titleItemStyle: String = "",
)
