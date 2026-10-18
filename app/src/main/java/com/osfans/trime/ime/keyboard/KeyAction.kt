// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.LazyPreferenceDelegate
import com.osfans.trime.data.theme.KeyLabelResolver
import com.osfans.trime.data.theme.LabelContext
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.KeyLabel
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.util.virtualKeyCharacterMap
import timber.log.Timber

/**
 * The live state a dynamic key label reads (doc/Keyboard.md §2.2): the current
 * schema from rime, and the app-provided enter-key label the caller passes down
 * from the view layer (see `EnterKeyDisplayDelegate`).
 */
internal fun keyLabelContext(enterLabel: String = ""): LabelContext {
    val rime = RimeDaemon.getFirstSessionOrNull()
    // The same expression the old implicit space-key label used, so a theme
    // asking for the schema name keeps the schema-id fallback.
    val name = rime?.run { statusCached.schemaName }.orEmpty()
    val id = rime?.run { schemaCached.schemaId }.orEmpty()
    return LabelContext(
        schemaName = name.ifEmpty { id.takeIf { it.isNotEmpty() && it != ".default" } ?: "" },
        schemaId = rime?.run { statusCached.schemaId }.orEmpty(),
        enterLabel = enterLabel,
    )
}

/** [按鍵][Key]的各種事件（單擊、長按、滑動等）  */
class KeyAction(
    token: KeyActionToken,
    presetKeys: Map<String, PresetKey>,
) {
    var code = 0
        private set
    var modifier = 0
        private set
    var text: String = ""
        private set
    var command: String = ""
        private set
    var option: String = ""
        private set
    var select: String = ""
        private set
    var toggle: String = ""
        private set
    var commit: String = ""
        private set
    var shiftLock: String = ""
        private set
    var isFunctional = false
        private set
    var isRepeatable = false
        private set
    var isSticky = false
        private set
    var isSlideCursor = false
        private set
    var isSlideDelete = false
        private set

    val isModifierKey: Boolean
        // Trime把function键消费掉了，因此键盘只处理function键以外的修饰键
        get() = KeyEvent.isModifierKey(this.code) && this.code != KeyEvent.KEYCODE_FUNCTION

    val isShiftLock: Boolean
        get() =
            when (shiftLock) {
                "long" -> false

                // 长按锁定
                "click" -> true

                // 点击锁定
                "ascii_long" -> !rime.run { statusCached }.isAsciiMode

                // 英文长按锁定，中文点击锁定
                else -> false
            }

    val modifierKeyOnMask: Int
        get() = getModifierKeyOnMask(this.code)

    // The label the theme declared for this action (doc/Keyboard.md §2.2); a
    // dynamic source is resolved when the label is read, not here.
    private var labelSpec: KeyLabel = KeyLabel.None

    // The label derived from the key code, used when the theme declares none.
    private var derivedLabel = ""
    private var shiftLabel = ""
    private var labelsReady = false

    private var preview: String? = null
    private var states: List<String> = listOf()

    // Resolved on first read, since the preference store needs the app context.
    private val hookShiftNum: Boolean by LazyPreferenceDelegate {
        AppPrefs.defaultInstance().keyboard.hookShiftNum
    }
    private val hookShiftSymbol: Boolean by LazyPreferenceDelegate {
        AppPrefs.defaultInstance().keyboard.hookShiftSymbol
    }

    private val rime get() = RimeDaemon.getFirstSessionOrNull()!!

    /**
     * Fills in the label of a key that declared none, deriving it from the key
     * code and the shifted character map. The calls are deferred out of the
     * constructor so that parsing stays pure.
     */
    private fun ensureLabels() {
        if (labelsReady) return
        labelsReady = true
        derivedLabel =
            labelSpec.staticText.ifEmpty {
                when (code) {
                    KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_SPACE -> ""
                    else -> KeyCode.getDisplayLabel(code, modifier)
                }
            }
        shiftLabel = derivedLabel
        if (KeyCode.isStandardKey(code) && virtualKeyCharacterMap.isPrintingKey(code)) {
            val charCode = virtualKeyCharacterMap.get(code, modifier or KeyEvent.META_SHIFT_ON)
            if (charCode != 0) {
                shiftLabel = charCode.toChar().toString()
            }
        }
    }

    private fun adjustCase(
        str: String,
        keyboard: Keyboard,
    ): String {
        val status = rime.run { statusCached }
        return if (str.length == 1 && (keyboard.isShifted || (!status.isAsciiMode && keyboard.isLabelUppercase))) {
            str.uppercase()
        } else {
            str
        }
    }

    fun getLabel(
        keyboard: Keyboard,
        enterLabel: String = "",
    ): String {
        ensureLabels()
        if (states.isNotEmpty() && toggle.isNotEmpty()) {
            return states[if (rime.run { getRuntimeOption(toggle) }) 1 else 0]
        }
        if (keyboard.isOnlyShiftOn) {
            val asciiMode = rime.run { statusCached }.isAsciiMode
            val composing = rime.run { statusCached }.isComposing
            if (!hookShiftNum && !composing && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                return adjustCase(shiftLabel, keyboard)
            }
            if (!hookShiftSymbol &&
                // TODO: 判断中英模式仅能正确处理已配置映射的符号，对于未配置映射的符号，即使在中文模式下也能上屏 Shift 切换的符号。
                asciiMode &&
                (
                    code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH ||
                        code == KeyEvent.KEYCODE_COMMA ||
                        code == KeyEvent.KEYCODE_PERIOD
                    )
            ) {
                return adjustCase(shiftLabel, keyboard)
            }
        }
        // The theme's own label — static, or a dynamic source read now, so a
        // schema switch shows up on the next draw — wins over the label derived
        // from the key code. Nothing else is implicit: an empty space key shows
        // no text at all (doc/Keyboard.md §2.2).
        val displayLabel = KeyLabelResolver.text(labelSpec, keyLabelContext(enterLabel)).ifEmpty { derivedLabel }
        return adjustCase(displayLabel, keyboard)
    }

    fun getText(keyboard: Keyboard): String {
        ensureLabels()
        return if (text.isNotEmpty()) {
            adjustCase(text, keyboard)
        } else if (keyboard.isShifted && code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && modifier == 0) {
            if (rime.run { statusCached.isAsciiMode }) "" else adjustCase(derivedLabel, keyboard)
        } else {
            text
        }
    }

    fun getPreview(
        keyboard: Keyboard,
        enterLabel: String = "",
    ): String = preview ?: getLabel(keyboard, enterLabel)

    init {
        when (token) {
            is KeyActionToken.Plain -> {
                // match like: { x: BackSpace } -> preset_keys/BackSpace: {..., send: BackSpace }
                val preset = presetKeys[token.token]
                if (preset != null) {
                    command = preset.command
                    option = preset.option
                    select = preset.select
                    toggle = preset.toggle
                    preview = preset.preview
                    shiftLock = preset.shiftLock
                    commit = preset.commit
                    text = preset.text
                    isSticky = preset.sticky
                    isRepeatable = preset.repeatable
                    isFunctional = preset.functional
                    isSlideCursor = preset.slideCursor
                    isSlideDelete = preset.slideDelete
                    states = preset.states

                    labelSpec = preset.label

                    val (keycode, modifiers) = KeyCode.parse(preset.send)
                    if (keycode != 0 || modifiers != 0) {
                        code = keycode
                        modifier = modifiers
                    } else if (preset.command.isNotEmpty()) {
                        code = KeyEvent.KEYCODE_FUNCTION
                    } else if (preset.send.isNotEmpty()) {
                        // A send value that resolved to nothing leaves the key
                        // inert; report it once per token (actions are cached).
                        Timber.e("preset '%s' has an unrecognized send '%s'", token.token, preset.send)
                    }
                } else {
                    // match like: { x: "{Control+a}" }
                    val (keycode, modifiers) = KeyCode.parse(token.token)
                    if (keycode != 0 || modifiers != 0) {
                        code = keycode
                        modifier = modifiers
                    } else {
                        // match like: { x: 1 } or { x: q } ...
                        code = KeyCode.nameToKeyCode(token.token)
                        // match like: { x: "(){Left}" } (key sequence to simulate)
                        if (token.token.isNotEmpty() && !KeyCode.isStandardKey(code)) {
                            text = token.token
                            labelSpec = KeyLabel.Text(token.token.replace(BRACED_PATTERN, ""))
                        }
                    }
                }
            }

            // match: { x: { commit: a, text: b, label: c } }
            is KeyActionToken.Inline -> {
                commit = token.commit ?: ""
                text = token.text ?: ""
                labelSpec = token.label?.takeIf { it.isNotEmpty() }?.let { KeyLabel.Text(it) } ?: KeyLabel.None
            }
        }
    }

    companion object {
        private val BRACED_PATTERN = Regex("""\{[^{}]+\}""")

        fun getModifierKeyOnMask(keycode: Int): Int = when (keycode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> KeyEvent.META_META_ON
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON
            KeyEvent.KEYCODE_SYM -> KeyEvent.META_SYM_ON
            else -> 0
        }
    }
}
