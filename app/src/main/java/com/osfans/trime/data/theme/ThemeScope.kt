/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.graphics.drawable.Drawable
import com.osfans.trime.util.ColorUtils
import timber.log.Timber
import java.io.File

/**
 * The runtime state of an activated theme: its config plus the currently
 * active color table, the pre-compiled [ColorTable] and the typed [colors]
 * view over it.
 *
 * A scope outlives day/night switches — switching mode only rebuilds the table
 * and [colors] — but not theme switches: activating another theme replaces the
 * scope (see [ColorManager.attachTheme]). Views must therefore re-read colors
 * from [colors] (or through [ColorManager]) every time they bind or draw;
 * values read once at construction go stale on a mode change.
 */
class ThemeScope internal constructor(
    val theme: Theme,
    /**
     * The theme package the theme was read from, or null when it was decoded
     * without one (a test fixture). The resource lookup resolves inside this
     * directory (see [ThemeResources]); it defaults to what the theme carries,
     * so only tests have a reason to pass it explicitly.
     */
    val packageDir: File? = theme.packageDir,
    private val parseColor: (String) -> Int? = { value ->
        runCatching { ColorUtils.parseColor(value) }.getOrNull()
    },
) {
    /** The currently active color table, or null before the first activation. */
    var activeColorScheme: ColorScheme? = null
        private set

    /**
     * The mode [activeColorScheme] belongs to, so the resource lookup knows
     * which `images/` directory to read. [ThemeMode.DAY] until the first
     * activation.
     */
    internal var activeMode: ThemeMode = ThemeMode.DAY
        private set

    private var colorsValue: ThemeColors? = null

    internal var colorTable: ColorTable? = null
        private set

    /** Typed view over the colors of the active table. */
    val colors: ThemeColors
        get() = requireNotNull(colorsValue) { "ThemeScope is not initialized" }

    /** Activates [scheme], the table of [mode], rebuilding [colors]. */
    internal fun updateScheme(
        scheme: ColorScheme,
        mode: ThemeMode = ThemeMode.DAY,
    ) {
        activeColorScheme = scheme
        activeMode = mode
        val table = ColorTable.resolve(scheme, theme.fallbackColors, parseColor)
        colorTable = table
        colorsValue = ThemeColors(table)
        // Values the theme file cannot resolve are reported once at load, per
        // table (see ThemeDiagnostics); this is the demand-driven remainder.
        if (table.unresolvedKeys.isNotEmpty()) {
            Timber.w("Unknown color key: %s", table.unresolvedKeys.joinToString { it.key })
        }
    }

    /** Resolves a color key through this scope; theme-only keys resolve via the fallback chain. */
    fun color(key: String): Int = ColorManager.resolveColor(this, key)

    /** Resolves a drawable key (a color or an image asset) through this scope. */
    fun drawable(key: String): Drawable? = ColorManager.resolveDrawable(this, key)

    /**
     * Builds a decorated drawable (rounded corners, optional border) from the
     * color or image asset resolved for [colorKey] in this scope.
     */
    fun decorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = ColorManager.resolveDecorDrawable(this, colorKey, borderColorKey, borderPx, cornerRadius, alpha)
}
