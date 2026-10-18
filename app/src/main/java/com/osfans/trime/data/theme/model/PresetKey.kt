/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable
data class PresetKey(
    val command: String = "",
    val option: String = "",
    val select: String = "",
    val toggle: String = "",
    /** The key's label: a static text, a dynamic source or none (doc/Keyboard.md §2.2). */
    val label: KeyLabel = KeyLabel.None,
    val preview: String? = null,
    val shiftLock: String = "",
    val commit: String = "",
    val text: String = "",
    val sticky: Boolean = false,
    val repeatable: Boolean = false,
    val slideCursor: Boolean = false,
    val slideDelete: Boolean = false,
    val functional: Boolean = false,
    @Serializable(with = LenientStringListSerializer::class)
    val states: List<String> = emptyList(),
    val send: String = "",
)
