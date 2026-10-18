/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlMap

/**
 * The day/night color tables of a theme together with where they were read
 * from.
 *
 * The tables themselves end up on [Theme.dayColors] / [Theme.nightColors],
 * which is what the runtime resolves against; this structure is the loader's
 * view of the same data. It keeps the provenance ([dayPath] / [nightPath] and
 * the source mappings) that the diagnostics need to point a finding at the
 * file and key an author wrote, and that [Theme] must not carry because it is
 * decoded from `theme.yaml` alone.
 */
data class ResolvedColors(
    /** The day color table; empty only for a theme that cannot be rendered. */
    val day: ColorScheme,
    /** The night color table, or null when night falls back to [day]. */
    val night: ColorScheme? = null,
    /** Where [day] was read from, for diagnostics paths. */
    val dayPath: String = DAY_FILE,
    /** Where [night] was read from, for diagnostics paths. */
    val nightPath: String = NIGHT_FILE,
    /**
     * The mapping [day] was read from when the theme came from a package, else
     * null. Only a read `day.yaml` has a key set and value shapes to check.
     */
    val dayNode: YamlMap? = null,
    /** The mapping [night] was read from when the theme is a package. */
    val nightNode: YamlMap? = null,
) {
    companion object {
        /** Required day color table of a theme package. */
        const val DAY_FILE = "day.yaml"

        /** Optional night color table of a theme package. */
        const val NIGHT_FILE = "night.yaml"
    }
}
