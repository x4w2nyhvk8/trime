/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.naming

import com.osfans.trime.data.theme.model.PresetKey

/**
 * 封闭动作动词枚举：统一命名规范 `action.<domain>.<verb>` 的运行时词表。
 *
 * 设计约束（见 doc/naming/Naming.md §5）：
 * - 动词表必须**封闭**：新增动作是改本枚举 + 改 doc/naming/vocabulary.yaml，
 *   不是造一个新 preset_keys 名字，否则会滑回 Return1 那种替身键；
 * - [id] 用 B1 lowerCamel 且必须逐字命中：写成 `capslock` 不会报错，
 *   只会被 [resolve] 判为 Invalid，避免静默失配；
 * - 每个动词归一化成一个现有 [PresetKey]，从而复用 KeyAction 既有的
 *   command / option / toggle / select / send 解析链，**不新增运行时分支**；
 * - [defaults] 为 null 表示同文当前无对应行为（见 ActionCatalog.yaml 的
 *   status: missing），解析时返回明确的 [ActionResolution.Unimplemented]。
 *
 * 本文件不依赖任何 Android API，可在纯 JVM 单元测试中直接构造。
 */
enum class ActionVerb(
    val id: String,
    val domain: String,
    val defaults: PresetKey?,
) {
    // ── text ────────────────────────────────────────────────────
    COMMIT("commit", "text", PresetKey(commit = " ")),
    INSERT_TEXT("insertText", "text", PresetKey()),
    REPEAT_COMMIT("repeatCommit", "text", PresetKey(command = "commit", option = "%1" + '$' + "s")),
    WEB_SEARCH("webSearch", "text", PresetKey(command = "web_search", option = "%4" + '$' + "s", send = "FUNCTION")),
    SHARE("share", "text", PresetKey(command = "send", option = "%s", send = "FUNCTION")),
    DATE("date", "text", PresetKey(command = "date", option = "yyyy-MM-dd", send = "FUNCTION")),
    GLIDE_TYPE("glideType", "text", null),

    // ── edit ────────────────────────────────────────────────────
    BACKSPACE("backspace", "edit", PresetKey(send = "BackSpace", repeatable = true)),
    DELETE("delete", "edit", PresetKey(send = "Delete")),
    DELETE_WORD("deleteWord", "edit", PresetKey(send = "BackSpace", slideDelete = true)),
    SELECT_ALL("selectAll", "edit", PresetKey(send = "{Control+a}")),
    SELECT_TEXT("selectText", "edit", null),
    CUT("cut", "edit", PresetKey(send = "{Control+x}")),
    COPY("copy", "edit", PresetKey(send = "{Control+c}")),
    PASTE("paste", "edit", PresetKey(send = "{Control+v}")),
    UNDO("undo", "edit", PresetKey(send = "{Control+z}")),
    REDO("redo", "edit", PresetKey(send = "{Control+y}")),
    MOVE_CURSOR("moveCursor", "edit", PresetKey(send = "Left", slideCursor = true)),
    MOVE_CURSOR_LINE_START("moveCursorLineStart", "edit", PresetKey(send = "Home")),
    MOVE_CURSOR_LINE_END("moveCursorLineEnd", "edit", PresetKey(send = "End")),
    MOVE_CURSOR_PAGE_START("moveCursorPageStart", "edit", PresetKey(send = "Page_Up")),
    MOVE_CURSOR_PAGE_END("moveCursorPageEnd", "edit", PresetKey(send = "Page_Down")),

    // ── modifier ────────────────────────────────────────────────
    SHIFT("shift", "modifier", PresetKey(send = "Shift_L")),
    TOGGLE_CAPS_LOCK("toggleCapsLock", "modifier", PresetKey(send = "Shift_L", shiftLock = "click")),

    // ── rime ────────────────────────────────────────────────────
    TOGGLE_OPTION(
        "toggleOption",
        "rime",
        PresetKey(toggle = "ascii_mode", send = "SWITCH_CHARSET", states = listOf("中文", "西文")),
    ),

    // ── layout ──────────────────────────────────────────────────
    CYCLE_KEYBOARD("cycleKeyboard", "layout", PresetKey(select = ".next", send = "Eisu_toggle")),
    SWITCH_KEYBOARD("switchKeyboard", "layout", PresetKey(select = "symbols", send = "Eisu_toggle")),

    // ── trime ───────────────────────────────────────────────────
    SHOW_CLIPBOARD("showClipboard", "trime", PresetKey(command = "clipboard_window", option = "0", send = "FUNCTION")),
    SHOW_EMOJI("showEmoji", "trime", PresetKey(command = "liquid_keyboard", option = "emoji", send = "FUNCTION")),
    SHOW_LIQUID_KEYBOARD(
        "showLiquidKeyboard",
        "trime",
        PresetKey(command = "liquid_keyboard", option = "更多", send = "FUNCTION"),
    ),
    SHOW_SETTINGS("showSettings", "trime", PresetKey(send = "SETTINGS")),
    DEPLOY("deploy", "trime", PresetKey(command = "apply", option = "DEPLOY", send = "FUNCTION")),
    SYNC("sync", "trime", PresetKey(command = "apply", option = "SYNC_USER_DATA", send = "FUNCTION")),
    RUN("run", "trime", PresetKey(command = "run", send = "FUNCTION")),

    // ── system ──────────────────────────────────────────────────
    SHOW_IME_PICKER("showImePicker", "system", PresetKey(select = ".next", send = "LANGUAGE_SWITCH")),
    HIDE_KEYBOARD("hideKeyboard", "system", null),
    EXPAND_POPUP("expandPopup", "system", null),
    REPEAT("repeat", "system", PresetKey(repeatable = true)),
    NONE("none", "system", PresetKey()),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        /** 供校验器与诊断使用的封闭动词 id 集合。 */
        val ids: Set<String> get() = byId.keys

        /**
         * 把 `action.<domain>.<verb>` 归一化成现有 [PresetKey]。
         *
         * 非 action token 一律返回 [ActionResolution.NotAction]，调用方继续走
         * 原有 `presetKeys` 查表与字面字符解析——存量主题零行为变化。
         */
        fun resolve(token: String): ActionResolution {
            if (!token.startsWith("action.")) return ActionResolution.NotAction
            val parts = token.split('.')
            if (parts.size != 3) {
                return ActionResolution.Invalid(token, "格式应为 action.<domain>.<verb>")
            }
            val domain = parts[1]
            val verb = parts[2]
            val found = byId[verb]
                ?: return ActionResolution.Invalid(token, "动词 '$verb' 不在封闭枚举（注意大小写）")
            if (found.domain != domain) {
                return ActionResolution.Invalid(token, "动词 '$verb' 属于 ${found.domain} 域，不是 $domain")
            }
            val defaults = found.defaults
                ?: return ActionResolution.Unimplemented(token, found)
            return ActionResolution.Ok(found, defaults)
        }
    }
}

/** [ActionVerb.resolve] 的结果。Invalid / Unimplemented 由调用方记录诊断并退化为 inert 键。 */
sealed interface ActionResolution {
    /** 已归一化，[defaults] 可直接交给 KeyAction 的既有解析链。 */
    data class Ok(
        val verb: ActionVerb,
        val defaults: PresetKey,
    ) : ActionResolution

    /** 动词合法但同文当前无对应行为，不应静默生效。 */
    data class Unimplemented(
        val token: String,
        val verb: ActionVerb,
    ) : ActionResolution

    /** token 以 `action.` 开头但格式 / 大小写 / 域不匹配。 */
    data class Invalid(
        val token: String,
        val reason: String,
    ) : ActionResolution

    /** 不是 action token，调用方沿用旧路径。 */
    data object NotAction : ActionResolution
}
