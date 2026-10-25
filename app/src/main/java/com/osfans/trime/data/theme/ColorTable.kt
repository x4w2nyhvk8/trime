/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import androidx.annotation.ColorInt
import java.util.EnumMap

/**
 * Pre-compiled color lookup table for one (theme, active scheme) pair.
 *
 * A color key resolves to the first non-empty value found while walking
 * scheme entries -> theme `fallback_colors` -> built-in fallback chains
 * ([ColorKey.fallbackOf]). Values are resolved once at table build time so
 * runtime lookups are O(1); the walk is replayable for theme-defined keys.
 */
internal class ColorTable private constructor(
    private val entries: EnumMap<ColorKey, Value>,
    val unresolvedKeys: List<ColorKey>,
    val invalidValues: List<ColorKey>,
) {
    /**
     * A resolved color key value. Image values are kept as raw strings; the
     * bitmap is decoded lazily so a missing file stays a runtime concern.
     */
    sealed interface Value {
        /** Chain exhausted: no usable value found for the key. */
        data object None : Value

        /** The value is a parseable color. */
        data class Color(@ColorInt val argb: Int) : Value

        /** The value names an image file under the theme background folder. */
        data class Image(val path: String) : Value

        /** The value is a linear gradient between two colors. */
        data class Gradient(
            @ColorInt val start: Int,
            @ColorInt val end: Int,
            val orientation: GradientOrientation,
        ) : Value
    }

    /** Direction of a gradient, ordered from [Gradient.start] to [Gradient.end]. */
    enum class GradientOrientation {
        TOP_BOTTOM,
        BOTTOM_TOP,
        LEFT_RIGHT,
        RIGHT_LEFT,
        TL_BR,
        TR_BL,
        BL_TR,
        BR_TL,
    }

    operator fun get(key: ColorKey): Value = entries[key] ?: Value.None

    companion object {
        private val IMAGE_SUFFIXES = arrayOf(".png", ".webp", ".jpg", ".gif")

        /** Whether a scheme value names an image file instead of a color. */
        fun isImageValue(value: String): Boolean = IMAGE_SUFFIXES.any { value.endsWith(it) }

        private val GRADIENT_PATTERN =
            Regex("""^gradient\(\s*([^,]+?)\s*,\s*([^,]+?)\s*,\s*([a-zA-Z_]+)\s*\)$""", RegexOption.IGNORE_CASE)

        /**
         * Parses `gradient(start, end, direction)` into a [Value.Gradient].
         * Colors are parsed with [parseColor], so they accept the same forms as
         * solid colors. Returns null when the value is not a gradient or any
         * part cannot be parsed.
         */
        fun parseGradient(
            value: String,
            parseColor: (String) -> Int?,
        ): Value.Gradient? {
            val match = GRADIENT_PATTERN.matchEntire(value.trim()) ?: return null
            val start = parseColor(match.groupValues[1].trim()) ?: return null
            val end = parseColor(match.groupValues[2].trim()) ?: return null
            val orientation = parseGradientOrientation(match.groupValues[3]) ?: return null
            return Value.Gradient(start, end, orientation)
        }

        private fun parseGradientOrientation(value: String): GradientOrientation? = when (value.lowercase()) {
            "vertical", "top_bottom", "tb" -> GradientOrientation.TOP_BOTTOM
            "bottom_top", "bt" -> GradientOrientation.BOTTOM_TOP
            "horizontal", "left_right", "lr" -> GradientOrientation.LEFT_RIGHT
            "right_left", "rl" -> GradientOrientation.RIGHT_LEFT
            "diagonal", "tl_br" -> GradientOrientation.TL_BR
            "diagonal_reverse", "tr_bl" -> GradientOrientation.TR_BL
            "bl_tr" -> GradientOrientation.BL_TR
            "br_tl" -> GradientOrientation.BR_TL
            else -> null
        }

        /**
         * The first non-empty value for [key] together with the key that
         * carries it, walking scheme colors -> theme fallback_colors ->
         * built-in fallback chains, or null when the chain is exhausted or
         * cyclic.
         *
         * The source key differs from [key] when the value was inherited: a
         * theme author fixes it at the source.
         */
        fun resolveRawSource(
            key: String,
            schemeColors: Map<String, String>,
            fallbackColors: Map<String, String>,
        ): Pair<String, String>? {
            var current = key
            val visited = HashSet<String>()
            while (visited.add(current)) {
                val value = schemeColors[current]
                if (!value.isNullOrEmpty()) return current to value
                val fallback = fallbackColors[current]
                if (!fallback.isNullOrEmpty()) {
                    current = fallback
                    continue
                }
                val altFallback = ColorKey.from(current)?.let(ColorKey::fallbackOf)
                if (altFallback != null) {
                    current = altFallback.key
                } else {
                    return null
                }
            }
            return null
        }

        /**
         * The first non-empty value for [key], walking
         * scheme colors -> theme fallback_colors -> built-in fallback chains,
         * or null when the chain is exhausted or cyclic.
         *
         * This mirrors the resolution rules of the color table so that keys
         * defined only by a theme resolve exactly like built-in keys.
         */
        fun resolveRaw(
            key: String,
            schemeColors: Map<String, String>,
            fallbackColors: Map<String, String>,
        ): String? = resolveRawSource(key, schemeColors, fallbackColors)?.second

        /**
         * Builds the table for one (theme, scheme) pair.
         *
         * @param parseColor parses a color string, returning null when the
         * value is not a color (dependency-injected for JVM testability).
         */
        fun resolve(
            scheme: ColorScheme,
            fallbackColors: Map<String, String>,
            parseColor: (String) -> Int?,
        ): ColorTable {
            val entries = EnumMap<ColorKey, Value>(ColorKey::class.java)
            val unresolvedKeys = mutableListOf<ColorKey>()
            val invalidValues = mutableListOf<ColorKey>()
            for (key in ColorKey.entries) {
                val raw = resolveRaw(key.key, scheme, fallbackColors)
                if (raw == null) {
                    unresolvedKeys += key
                    continue
                }
                val gradient = parseGradient(raw, parseColor)
                val parsed = if (gradient == null) parseColor(raw) else null
                entries[key] =
                    when {
                        isImageValue(raw) -> Value.Image(raw)

                        gradient != null -> gradient

                        parsed != null -> Value.Color(parsed)

                        else -> {
                            invalidValues += key
                            Value.None
                        }
                    }
            }
            return ColorTable(entries, unresolvedKeys, invalidValues)
        }
    }
}
