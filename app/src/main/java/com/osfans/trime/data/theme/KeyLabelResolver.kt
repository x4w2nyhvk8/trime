/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyLabel

/**
 * The app state a dynamic label reads (doc/Keyboard.md §2.2). A value that is
 * not available is "", so a dynamic label with nothing to show renders nothing
 * instead of failing.
 */
data class LabelContext(
    /** The current schema's display name; read by [KeyLabel.Dynamic.Source.SCHEMA_NAME]. */
    val schemaName: String = "",
    /** The current schema's id; read by [KeyLabel.Dynamic.Source.SCHEMA_ID]. */
    val schemaId: String = "",
    /** The app-provided enter-key label; read by [KeyLabel.Dynamic.Source.ENTER_LABEL]. */
    val enterLabel: String = "",
)

/**
 * Resolves the labels of doc/Keyboard.md §2.2 and the priority of §2.1. Pure, so
 * the spelling matrix is unit-tested instead of hiding behind the views.
 */
object KeyLabelResolver {
    /** The text [label] shows under [context]; "" when it shows none. */
    fun text(
        label: KeyLabel,
        context: LabelContext = LabelContext(),
    ): String = when (label) {
        KeyLabel.None -> ""

        is KeyLabel.Text -> label.text

        is KeyLabel.Dynamic ->
            when (label.source) {
                KeyLabel.Dynamic.Source.SCHEMA_NAME -> context.schemaName
                KeyLabel.Dynamic.Source.SCHEMA_ID -> context.schemaId
                KeyLabel.Dynamic.Source.ENTER_LABEL -> context.enterLabel
                KeyLabel.Dynamic.Source.CUSTOM -> label.template
            }
    }

    /**
     * A key's main label under doc/Keyboard.md §2.1's priority: what the entry
     * itself declares — its `label`, or the style node's `text` — when it
     * declares anything and [showPlacement] holds, else the action's label: the
     * preset's, or the key code's built-in default.
     *
     * @param placementText the entry's own label, already resolved through
     *   [text]; "" when it declares none or its source has nothing to show.
     * @param showPlacement whether the entry's label wins at all right now (it
     *   does not while the keyboard is in ascii mode, for instance).
     */
    fun resolve(
        placementText: String,
        actionLabel: String,
        showPlacement: Boolean,
    ): String = if (showPlacement && placementText.isNotEmpty()) placementText else actionLabel
}
