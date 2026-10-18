/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * The three families one `styles:` table holds (doc/Theme.md §8.5): key styles
 * (doc/Theme.md §4.1), panel styles (§8.2) and item styles (§8.3). A name
 * belongs to exactly one family — 一义一名 — and the family is not a field of the
 * node: it is whichever reference sites name it.
 */
enum class StyleFamily {
    KEY,
    PANEL,
    ITEM,
}

/**
 * Derives each `styles:` name's family from the theme's reference sites. Pure,
 * so the rule is unit-tested instead of being spread over the callers:
 *
 * - a key entry's `style:`;
 * - `option_panel.panel_style` and its three item slots (doc/Theme.md §8.1);
 * - `clipboard.panel_style` and its three item slots.
 *
 * A node's own fields cannot decide this: the families share fields such as
 * `corner_radius`, and only a key style has `content`.
 */
object StyleFamilyResolver {
    /** Fields only a key style node knows; carrying one declares the key family (doc/Theme.md §4.1). */
    val KEY_FIELDS: Set<String> =
        setOf(
            "content",
            "text",
            "text_size",
            "font_face",
            "system_icon",
            "asset_icon",
            "icon_size",
            "nine_patch",
            "corner_radius",
            "border_width",
            "border_color",
        )

    /** The panel style fields (doc/Theme.md §8.2). */
    val PANEL_FIELDS: Set<String> =
        setOf(
            "background",
            "corner_radius",
            "padding",
            "max_height",
            "side_margin",
            "item_spacing",
            "action_button_size",
            "divider",
            "divider_color",
        )

    /** The item style fields (doc/Theme.md §8.3). */
    val ITEM_FIELDS: Set<String> =
        setOf(
            "height",
            "min_height",
            "corner_radius",
            "border_width",
            "border_color",
            "background",
            "background_on",
            "background_highlight",
            "text_color",
            "label_size",
            "max_lines",
            "ellipsize",
            "icon_size",
            "icon_color",
            "pin_size",
            "pin_color",
            "pin_inset",
            "padding",
        )

    /** Every field any family knows; anything else is a typo (doc/Theme.md §8.5). */
    val ALL_FIELDS: Set<String> = KEY_FIELDS + PANEL_FIELDS + ITEM_FIELDS

    /** The fields a node's own text declares as key-style (see [declaresKeyFamily]). */
    private val KEY_ONLY_FIELDS: Set<String> = setOf("content", "text", "text_size", "system_icon", "asset_icon", "nine_patch")

    /** The fields of [family]. */
    fun fieldsOf(family: StyleFamily): Set<String> = when (family) {
        StyleFamily.KEY -> KEY_FIELDS
        StyleFamily.PANEL -> PANEL_FIELDS
        StyleFamily.ITEM -> ITEM_FIELDS
    }

    /**
     * Every `styles:` name the theme references, with the families that
     * reference it. An unreferenced node is absent.
     */
    fun references(theme: Theme): Map<String, Set<StyleFamily>> {
        val references = mutableMapOf<String, MutableSet<StyleFamily>>()
        fun reference(
            name: String,
            family: StyleFamily,
        ) {
            if (name.isEmpty()) return
            references.getOrPut(name) { mutableSetOf() } += family
        }
        theme.presetKeyboards.values.forEach { keyboard ->
            keyboard.keys.forEach { key -> reference(key.style, StyleFamily.KEY) }
        }
        theme.optionPanel.run {
            reference(panelStyle, StyleFamily.PANEL)
            reference(actionItemStyle, StyleFamily.ITEM)
            reference(switchItemStyle, StyleFamily.ITEM)
            reference(titleItemStyle, StyleFamily.ITEM)
        }
        theme.clipboard.run {
            reference(panelStyle, StyleFamily.PANEL)
            reference(itemStyle, StyleFamily.ITEM)
            reference(pinnedItemStyle, StyleFamily.ITEM)
            reference(titleItemStyle, StyleFamily.ITEM)
        }
        return references
    }

    /**
     * Whether a node's own fields declare it a key style: it sets `content`, or
     * carries a field no other family knows. The key-family layer rule of
     * doc/Theme.md §4.1 applies to such a node even when no key references it
     * yet; a node whose fields are all shared or panel/item-only is left to its
     * reference sites.
     */
    fun declaresKeyFamily(fieldNames: Set<String>): Boolean = fieldNames.any { it in KEY_ONLY_FIELDS }

    /**
     * The fields a node may carry, given the families that reference it. An
     * unreferenced node may carry any family's fields, since nothing says which
     * it is.
     */
    fun allowedFields(families: Set<StyleFamily>): Set<String> = if (families.isEmpty()) ALL_FIELDS else families.flatMapTo(mutableSetOf()) { fieldsOf(it) }
}
