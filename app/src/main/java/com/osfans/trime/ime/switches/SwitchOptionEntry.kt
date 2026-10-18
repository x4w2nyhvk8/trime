/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.switches

import androidx.annotation.DrawableRes
import com.osfans.trime.core.RimeSchema
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.OptionPanelItemKind

sealed class SwitchOptionEntry(
    val label: String,
    @param:DrawableRes
    val icon: Int,
) {
    /**
     * The option-panel style slot this kind of row draws through (doc/Theme.md
     * §8.4). It lives on the entry so no view has to judge it.
     */
    abstract val itemKind: OptionPanelItemKind

    /** Whether the row's Rime switch is on, which selects `background_on`; a [Static] row has no state. */
    open val isOn: Boolean = false

    class Static(label: String, icon: Int, val type: Type) : SwitchOptionEntry(label, icon) {
        override val itemKind: OptionPanelItemKind = OptionPanelItemKind.ACTION

        enum class Type {
            SchemaList,
            UpdateConfig,
            Keyboard,
            ThemeList,
        }
    }

    class Custom(
        val switch: RimeSchema.Switch,
        override val isOn: Boolean,
        label: String,
        icon: Int,
    ) : SwitchOptionEntry(label, icon) {
        override val itemKind: OptionPanelItemKind = OptionPanelItemKind.SWITCH
    }

    companion object {
        fun fromSwitch(rime: RimeSession, it: RimeSchema.Switch): Custom? {
            val labels = it.states
            if (labels.size <= 1) return null
            return if (it.name.isNotEmpty()) {
                if (labels.size != 2) return null
                val (disabledText, enabledText) = labels
                val value = rime.run { getRuntimeOption(it.name) }
                val label = if (value) "$enabledText → $disabledText" else "$disabledText → $enabledText"
                Custom(it, value, label, 0)
            } else {
                val options = it.options
                if (options.size != labels.size) return null
                val index = options.indexOfFirst { rime.run { getRuntimeOption(it) } }
                val label = labels[if (index >= 0) index else 0]
                Custom(it, index >= 0, label, 0)
            }
        }
    }
}
