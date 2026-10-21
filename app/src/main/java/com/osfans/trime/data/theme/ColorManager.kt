// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.NinePatch
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.NinePatchDrawable
import androidx.annotation.ColorInt
import androidx.collection.LruCache
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.NinePatchBitmapFactory
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.isNightMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Global entry point for the colors and drawables of the active theme.
 *
 * The activation state itself lives in the current [ThemeScope], which
 * [attachTheme] replaces on every theme switch; scheme switches update the
 * same scope in place. String-keyed lookups below ([getColor], [getDrawable])
 * resolve through that scope so that keys only a theme can define (per-key
 * keyboard colors, image-valued entries) keep working, while [activeColorScheme]
 * and the scope's [ThemeScope.colors] expose the typed view for UI code.
 */
object ColorManager {
    private val GRADIENT_PATTERN =
        Regex("""^(linear-gradient|radial-gradient|sweep-gradient)\((.*)\)$""")
    private var scope: ThemeScope? = null
    private val prefs = ThemeManager.prefs

    private var isNightMode = false

    val activeColorScheme: ColorScheme
        get() = requireNotNull(requireScope().activeColorScheme) { "ColorManager is not initialized" }

    private var bitmapCache: LruCache<String, Bitmap>? = null

    private var generation = 0L

    /**
     * Bumped whenever the active scheme changes. Scheme-dependent caches
     * (e.g. per-key colors in Key) re-resolve when it moves.
     */
    val colorGeneration: Long
        get() = generation

    fun interface OnColorChangeListener {
        fun onColorChange(theme: Theme)
    }

    private val onChangeListeners = WeakHashSet<OnColorChangeListener>()

    fun addOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnColorChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onColorChange(requireScope().theme) }
    }

    fun init(configuration: Configuration) {
        isNightMode = configuration.isNightMode()
        activateScheme(notify = false)

        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val cacheSize = maxMemory / 8
        bitmapCache =
            object : LruCache<String, Bitmap>(cacheSize.toInt()) {
                override fun sizeOf(
                    key: String,
                    value: Bitmap,
                ): Int = value.byteCount / 1024
            }
    }

    fun onSystemNightModeChange(isNight: Boolean) {
        isNightMode = isNight
        activateScheme(notify = true)
    }

    /** The current theme scope, or null before the first theme is attached. */
    fun currentScope(): ThemeScope? = scope

    /**
     * Attaches a theme, replacing the current scope. Theme switches notify
     * through ThemeManager, so no color listener fires here.
     */
    fun attachTheme(theme: Theme) {
        bitmapCache?.evictAll()
        scope = ThemeScope(theme)
        activateScheme(notify = false)
    }

    /**
     * Switches to the scheme [schemeId] and remembers it as the user's choice.
     * The scheme is resolved from the preference afterwards, so the day/night
     * links a scheme may declare are honoured, like an automatic switch.
     */
    fun setColorScheme(schemeId: String) {
        prefs.normalModeColor.setValue(schemeId)
        activateScheme(notify = true)
    }

    private fun requireScope(): ThemeScope = requireNotNull(scope) { "ColorManager is not initialized" }

    private fun resolveActiveScheme(theme: Theme): ColorScheme = ColorSchemeResolver.resolve(
        schemes = theme.presetColorSchemes,
        selectedSchemeId = prefs.normalModeColor.getValue(),
        followSystemDayNight = prefs.followSystemDayNight.getValue(),
        isNightMode = isNightMode,
    )

    /**
     * Re-resolves the active scheme from the prefs and current theme, or
     * activates the given one. Listener notification fires only when the
     * scheme actually changed.
     */
    private fun activateScheme(
        scheme: ColorScheme? = null,
        notify: Boolean,
    ) {
        val activeScope = scope ?: return
        val target = scheme ?: resolveActiveScheme(activeScope.theme)
        if (activeScope.activeColorScheme == target) return
        activeScope.updateScheme(target)
        generation++
        if (notify) fireChange()
    }

    private fun backgroundFolder(scope: ThemeScope) = scope.theme.style.backgroundFolder

    /**
     * Resolves a color key against the given scope. Exposed so UI code can
     * look up keys only a theme can define through an injected scope.
     */
    @ColorInt
    internal fun resolveColor(
        scope: ThemeScope,
        key: String,
    ): Int {
        val tableEntry = ColorKey.from(key)?.let { scope.colorTable?.get(it) }
        if (tableEntry is ColorTable.Value.Color) return tableEntry.argb
        // Keys defined only by a theme resolve through the same chain rules.
        val scheme = requireNotNull(scope.activeColorScheme)
        val raw = ColorTable.resolveRaw(key, scheme, scope.theme.fallbackColors)
        return try {
            if (raw == null) throw IllegalArgumentException("$key not found")
            ColorUtils.parseColor(raw)
        } catch (_: IllegalArgumentException) {
            ColorUtils.parseColor(key)
        }
    }

    /** Resolves a drawable key (color or image asset) against the given scope. */
    internal fun resolveDrawable(
        scope: ThemeScope,
        key: String,
    ): Drawable? {
        val tableEntry = ColorKey.from(key)?.let { scope.colorTable?.get(it) }
        if (tableEntry != null) {
            return when (tableEntry) {
                is ColorTable.Value.Color -> GradientDrawable().apply { setColor(tableEntry.argb) }
                is ColorTable.Value.Image -> imageDrawable(scope, tableEntry.path)
                ColorTable.Value.None -> parseDrawable(scope, key)
            }
        }
        // Keys defined only by a theme resolve through the same chain rules.
        val scheme = requireNotNull(scope.activeColorScheme)
        val raw = ColorTable.resolveRaw(key, scheme, scope.theme.fallbackColors)
        return parseDrawable(scope, raw ?: key)
    }

    private fun parseDrawable(
        scope: ThemeScope,
        value: String,
    ): Drawable? {
        if (value.isEmpty()) return null
        if (ColorTable.isImageValue(value)) return imageDrawable(scope, value)
        parseGradient(scope, value)?.let { return it }
        val color = runCatching { ColorUtils.parseColor(value) }.getOrDefault(Color.TRANSPARENT)
        return GradientDrawable().apply { setColor(color) }
    }

    /**
     * Parses a parameterised gradient value:
     * `linear-gradient(<angle>deg, <color>[, <color>...])`,
     * `radial-gradient(<color>[, <color>...])`,
     * `sweep-gradient(<color>[, <color>...])`.
     * Colors may be literals or keys of the active color scheme.
     */
    private fun parseGradient(
        scope: ThemeScope,
        value: String,
    ): Drawable? {
        val match = GRADIENT_PATTERN.matchEntire(value.trim()) ?: return null
        val type = match.groupValues[1]
        val args = match.groupValues[2].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        var angle = 90f
        val colorArgs =
            if (type == "linear-gradient" && args.firstOrNull()?.endsWith("deg") == true) {
                angle = args.first().removeSuffix("deg").toFloatOrNull() ?: 90f
                args.drop(1)
            } else {
                args
            }
        if (colorArgs.size < 2) return null
        val colors =
            colorArgs.map { token ->
                runCatching { resolveColor(scope, token) }.getOrDefault(Color.TRANSPARENT)
            }.toIntArray()
        return GradientFillDrawable(type, colors, angle)
    }

    private fun imageDrawable(
        scope: ThemeScope,
        value: String,
    ): Drawable? {
        val path = resolveImageFilePath(scope, value)
        val bitmap =
            bitmapCache?.get(path)
                ?: BitmapFactory.decodeFile(path)?.also {
                    bitmapCache?.put(path, it)
                } ?: return null
        if (path.endsWith(".9.png")) {
            val chunk = bitmap.ninePatchChunk
            return if (NinePatch.isNinePatchChunk(chunk)) {
                // for compiled nine patch image
                NinePatchDrawable(Resources.getSystem(), bitmap, chunk, Rect(), null)
            } else {
                // for source nine patch image
                NinePatchBitmapFactory.createNinePatchDrawable(Resources.getSystem(), bitmap)
            }
        }
        return bitmap.toDrawable(Resources.getSystem())
    }

    private fun resolveImageFilePath(
        scope: ThemeScope,
        value: String,
    ): String {
        val default = DataManager.userDataDir.resolve("backgrounds/${backgroundFolder(scope)}/$value")
        if (!default.exists()) {
            val fallback = DataManager.userDataDir.resolve("backgrounds/$value")
            if (fallback.exists()) return fallback.absolutePath
        }
        return default.absolutePath
    }

    @ColorInt
    fun getColor(key: String): Int = resolveColor(requireScope(), key)

    fun getDrawable(key: String): Drawable? = resolveDrawable(requireScope(), key)

    internal fun resolveDecorDrawable(
        scope: ThemeScope,
        colorKey: String,
        borderColorKey: String?,
        borderPx: Int,
        cornerRadius: Float,
        alpha: Int,
    ): Drawable? = when (val drawable = resolveDrawable(scope, colorKey)) {
        is GradientDrawable ->
            drawable.also {
                it.cornerRadius = cornerRadius
                it.alpha = MathUtils.clamp(alpha, 0, 255)
                if (!borderColorKey.isNullOrEmpty()) {
                    try {
                        val borderColor = resolveColor(scope, borderColorKey)
                        it.setStroke(borderPx, borderColor)
                    } catch (_: Exception) {
                    }
                }
            }

        else -> drawable?.also {
            it.alpha = MathUtils.clamp(alpha, 0, 255)
            (it as? GradientFillDrawable)?.cornerRadius = cornerRadius
        }
    }

    fun getDecorDrawable(
        colorKey: String,
        borderColorKey: String? = null,
        borderPx: Int = 0,
        cornerRadius: Float = 0f,
        alpha: Int = 255,
    ): Drawable? = resolveDecorDrawable(requireScope(), colorKey, borderColorKey, borderPx, cornerRadius, alpha)
}

/**
 * Drawable filled with a shader, supporting linear, radial and sweep gradients.
 * Key backgrounds are clipped by KeyView; decoration callers can set
 * [cornerRadius] instead.
 */
private class GradientFillDrawable(
    private val type: String,
    private val colors: IntArray,
    private val angleDegrees: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: Shader? = null

    /** Corner radius applied when draw() is used by decoration drawables */
    var cornerRadius: Float = 0f

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        shader =
            when (type) {
                "linear-gradient" -> {
                    val radians = Math.toRadians(angleDegrees.toDouble())
                    val dx = sin(radians).toFloat()
                    val dy = -cos(radians).toFloat()
                    val half = (abs(width * dx) + abs(height * dy)) / 2f
                    LinearGradient(
                        centerX - dx * half,
                        centerY - dy * half,
                        centerX + dx * half,
                        centerY + dy * half,
                        colors,
                        null,
                        Shader.TileMode.CLAMP,
                    )
                }

                "radial-gradient" ->
                    RadialGradient(
                        centerX,
                        centerY,
                        max(width, height) / 2f,
                        colors,
                        null,
                        Shader.TileMode.CLAMP,
                    )

                else -> SweepGradient(centerX, centerY, colors, null)
            }
    }

    override fun draw(canvas: Canvas) {
        val current = shader ?: return
        paint.shader = current
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(
                bounds.left.toFloat(),
                bounds.top.toFloat(),
                bounds.right.toFloat(),
                bounds.bottom.toFloat(),
                cornerRadius,
                cornerRadius,
                paint,
            )
        } else {
            canvas.drawRect(bounds, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
