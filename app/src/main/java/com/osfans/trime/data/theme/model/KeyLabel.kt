/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

/**
 * A key's label, as doc/Keyboard.md §2.2 spells it: a fixed text, a text
 * computed from the app's state when the key is drawn, or nothing at all.
 *
 * - `label: 空格` and `label: {text: 空格}` both decode to [Text];
 * - `label: {source: …}` decodes to [Dynamic];
 * - a missing (or blank) `label` decodes to [None], which means **no text**:
 *   the v3 format has no implicit label (an empty space key no longer shows the
 *   schema name).
 */
@Serializable(with = KeyLabelSerializer::class)
sealed interface KeyLabel {
    /** No label: the key shows no text of its own. */
    data object None : KeyLabel

    /** A text written in the theme, shown as-is. */
    data class Text(val text: String) : KeyLabel

    /**
     * A text computed from the app state when the key is drawn, so switching
     * the schema or the editor action updates the label without rebuilding the
     * keyboard.
     *
     * [template] belongs to [Source.CUSTOM] and is ignored by the other
     * sources.
     */
    data class Dynamic(
        val source: Source,
        val template: String = "",
    ) : KeyLabel {
        /** The state a `{source: …}` label reads. */
        enum class Source(
            /** The camelCase spelling the theme contract uses. */
            val yamlName: String,
        ) {
            SCHEMA_NAME("schemaName"),
            SCHEMA_ID("schemaId"),
            ENTER_LABEL("enterLabel"),
            CUSTOM("custom"),
            ;

            companion object {
                /**
                 * The source [raw] names, matching with `_` folded and
                 * case-insensitively, so `schema_name` is accepted as well.
                 */
                fun of(raw: String): Source? {
                    val normalized = raw.trim().replace("_", "").lowercase()
                    return entries.firstOrNull { it.yamlName.replace("_", "").lowercase() == normalized }
                }
            }
        }
    }

    /** The text this label carries without any state; "" for [Dynamic] and [None]. */
    val staticText: String
        get() = when (this) {
            None -> ""
            is Text -> text
            is Dynamic -> ""
        }
}
