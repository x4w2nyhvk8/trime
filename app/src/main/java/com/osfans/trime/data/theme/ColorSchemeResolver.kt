/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * Which of a theme's two presentations is in use. Its [dirName] names the
 * directory under the package's `images/` that holds this mode's resources
 * (doc/Theme.md §3), so the color-table lookup and the resource lookup share
 * one decision instead of each deriving its own.
 */
internal enum class ThemeMode(
    val dirName: String,
) {
    DAY("day"),
    NIGHT("night"),
}

/**
 * Pure day/night selection logic: picks the mode and the active color table
 * from the theme's `day.yaml`/`night.yaml` tables, the follow-system-day-night
 * preference and the current night state. Extracted from ColorManager so it is
 * unit-testable.
 *
 * A v3 theme has no concept of a "color scheme", so there is no selection:
 * either the day table is always used, or the day/night pair follows the mode.
 */
internal object ColorSchemeResolver {
    /**
     * The mode the theme is drawn in, and therefore the `images/` directory its
     * resources resolve in.
     *
     * @param dayColors the theme's day color table; [ThemeLoader] refuses a
     *   theme with an empty one, so it is never empty here.
     * @param nightColors the theme's night color table, or null/empty when the
     *   theme declares none, in which case night falls back to [ThemeMode.DAY].
     */
    fun modeOf(
        dayColors: ColorScheme,
        nightColors: ColorScheme?,
        followSystemDayNight: Boolean,
        isNightMode: Boolean,
    ): ThemeMode {
        require(dayColors.isNotEmpty()) { "The theme defines no day colors" }
        if (followSystemDayNight && isNightMode && !nightColors.isNullOrEmpty()) return ThemeMode.NIGHT
        return ThemeMode.DAY
    }

    /**
     * @return the color table in use, i.e. the one [modeOf] names. Night only
     *   selects a table of its own; a theme without one keeps the day table.
     */
    fun resolve(
        dayColors: ColorScheme,
        nightColors: ColorScheme?,
        followSystemDayNight: Boolean,
        isNightMode: Boolean,
    ): ColorScheme = when (modeOf(dayColors, nightColors, followSystemDayNight, isNightMode)) {
        ThemeMode.DAY -> dayColors
        ThemeMode.NIGHT -> nightColors ?: dayColors
    }
}
