/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Pins ColorKey against the historical built-in fallback chains that lived in
 * ColorManager, so future edits cannot silently change resolution semantics.
 */
class ColorKeyTest :
    BehaviorSpec({
        Given("the ColorKey enum") {
            Then("every entry maps to its lowercased YAML name and back") {
                for (entry in ColorKey.entries) {
                    ColorKey.from(entry.key) shouldBe entry
                }
            }
            Then("unknown strings are not typed color keys") {
                ColorKey.from("no_such_key").shouldBeNull()
                ColorKey.from("").shouldBeNull()
            }
        }
        Given("the built-in fallback chain table") {
            val historical = LEGACY_BUILTIN_FALLBACK_COLORS
            Then("it matches the historical table that lived in ColorManager") {
                ColorKey.builtinFallbackColors.mapKeys { it.key.key }
                    .mapValues { it.value.key } shouldBe historical
            }
            Then("the graph is acyclic and every chain terminates") {
                val reachable = mutableSetOf<ColorKey>()
                for (entry in ColorKey.entries) {
                    var current: ColorKey? = entry
                    val seen = mutableSetOf<ColorKey>()
                    while (current != null && seen.add(current)) {
                        reachable.add(current)
                        current = ColorKey.fallbackOf(current)
                    }
                    current.shouldBeNull()
                    check(seen.size <= ColorKey.entries.size) { "cycle detected from ${entry.key}" }
                }
                reachable.size shouldBe ColorKey.entries.size
            }
            Then("keys without a chain entry are the terminals requested by code") {
                val terminals = ColorKey.entries.filter { ColorKey.fallbackOf(it) == null }
                terminals.map { it.key } shouldContainExactlyInAnyOrder
                    listOf(
                        "keyboard_normal_back_color",
                        "text_normal_text_color",
                        "candidate_normal_border_color",
                        "preview_normal_back_color",
                        "preview_normal_text_color",
                    )
            }
        }
    })

/**
 * The fallback chains as originally hardcoded in ColorManager (removed in R2),
 * keyed by YAML string. ColorKeyTest asserts the typed graph equals this.
 */
private val LEGACY_BUILTIN_FALLBACK_COLORS =
    mapOf(
        "candidate_normal_text_color" to "text_normal_text_color",
        "comment_normal_text_color" to "candidate_normal_text_color",
        "keyboard_normal_border_color" to "keyboard_normal_back_color",
        "candidate_normal_separator_color" to "keyboard_normal_border_color",
        "text_selected_text_color" to "text_normal_text_color",
        "text_selected_back_color" to "keyboard_normal_back_color",
        "candidate_selected_text_color" to "text_selected_text_color",
        "candidate_selected_back_color" to "text_selected_back_color",
        "candidate_button_selected_back_color" to "candidate_selected_back_color",
        "label_selected_text_color" to "candidate_selected_text_color",
        "comment_selected_text_color" to "comment_normal_text_color",
        "key_pressed_back_color" to "candidate_selected_back_color",
        "key_pressed_border_color" to "key_normal_border_color",
        "key_pressed_text_color" to "candidate_selected_text_color",
        "key_pressed_symbol_color" to "comment_selected_text_color",
        "function_key_pressed_back_color" to "key_pressed_back_color",
        "function_key_on_pressed_back_color" to "key_pressed_back_color",
        "function_key_pressed_border_color" to "key_pressed_border_color",
        "function_key_on_pressed_border_color" to "key_pressed_border_color",
        "function_key_pressed_text_color" to "key_pressed_text_color",
        "function_key_on_pressed_text_color" to "key_pressed_text_color",
        "function_key_pressed_symbol_color" to "key_pressed_symbol_color",
        "function_key_on_pressed_symbol_color" to "key_pressed_symbol_color",
        "key_normal_back_color" to "keyboard_normal_back_color",
        "key_normal_border_color" to "keyboard_normal_border_color",
        "key_normal_text_color" to "candidate_normal_text_color",
        "key_normal_symbol_color" to "comment_normal_text_color",
        "label_normal_text_color" to "candidate_normal_text_color",
        "function_key_normal_back_color" to "key_normal_back_color",
        "function_key_normal_border_color" to "key_normal_border_color",
        "function_key_normal_text_color" to "key_normal_text_color",
        "function_key_normal_symbol_color" to "key_normal_symbol_color",
        "function_key_on_back_color" to "key_pressed_back_color",
        "function_key_on_border_color" to "key_pressed_border_color",
        "function_key_on_text_color" to "key_pressed_text_color",
        "function_key_on_symbol_color" to "key_pressed_symbol_color",
        "popup_normal_back_color" to "key_normal_back_color",
        "popup_normal_text_color" to "key_normal_text_color",
        "popup_selected_back_color" to "key_pressed_back_color",
        "popup_selected_text_color" to "key_pressed_text_color",
        "key_normal_shadow_color" to "keyboard_normal_border_color",
        "root_normal_back_color" to "keyboard_normal_back_color",
        "candidate_normal_back_color" to "keyboard_normal_back_color",
        "panel_normal_back_color" to "keyboard_normal_back_color",
        "text_normal_back_color" to "keyboard_normal_back_color",
        "long_text_normal_text_color" to "key_normal_text_color",
        "long_text_normal_back_color" to "key_normal_back_color",
    ).also {
        check(it.size == 47) { "historical fallback table changed: ${it.size}" }
    }
