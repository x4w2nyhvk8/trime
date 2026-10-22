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
import io.kotest.matchers.string.shouldMatch

/**
 * Pins ColorKey against the fallback chains the runtime resolves, so future
 * edits cannot silently change resolution semantics.
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
            Then("keys follow the <role>_[pressed_]<part>_color convention") {
                val canonical = Regex("^[a-z][a-z_]*_(?:(?:pressed)_)?(?:back|border|separator|text|symbol|shadow)_color$")
                // 角色本身含 part 字样的键，省略重复的 part 段
                val roleOnly = setOf("label_color")
                for (entry in ColorKey.entries) {
                    if (entry.key in roleOnly) continue
                    entry.key shouldMatch canonical
                }
            }
            Then("the pressed state is the only spelled-out state") {
                for (entry in ColorKey.entries) {
                    entry.key shouldMatch Regex("^(?!.*_(?:selected|on|normal|hilited)_).*$")
                }
            }
        }
        Given("the built-in fallback chain table") {
            Then("it matches the table the runtime resolves") {
                ColorKey.builtinFallbackColors.mapKeys { it.key.key }
                    .mapValues { it.value.key } shouldBe BUILTIN_FALLBACK_COLORS
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
                        "keyboard_back_color",
                        "keyboard_text_color",
                    )
            }
        }
    })

/** The fallback chains the runtime resolves, keyed by YAML string. */
private val BUILTIN_FALLBACK_COLORS =
    mapOf(
        "border_back_color" to "keyboard_back_color",
        "candidate_text_color" to "keyboard_text_color",
        "comment_text_color" to "candidate_text_color",
        "candidate_separator_color" to "border_back_color",
        "candidate_border_color" to "border_back_color",
        "preedit_text_color" to "keyboard_text_color",
        "preedit_back_color" to "keyboard_back_color",
        "preedit_pressed_text_color" to "keyboard_text_color",
        "preedit_pressed_back_color" to "keyboard_back_color",
        "candidate_pressed_text_color" to "preedit_pressed_text_color",
        "candidate_pressed_back_color" to "preedit_pressed_back_color",
        "candidate_button_back_color" to "candidate_back_color",
        "candidate_button_text_color" to "candidate_text_color",
        "candidate_button_pressed_back_color" to "candidate_pressed_back_color",
        "candidate_button_pressed_text_color" to "candidate_pressed_text_color",
        "label_pressed_text_color" to "candidate_pressed_text_color",
        "comment_pressed_text_color" to "comment_text_color",
        "key_pressed_back_color" to "candidate_pressed_back_color",
        "key_pressed_border_color" to "key_border_color",
        "key_pressed_text_color" to "candidate_pressed_text_color",
        "key_pressed_symbol_color" to "comment_pressed_text_color",
        "key_back_color" to "keyboard_back_color",
        "key_border_color" to "border_back_color",
        "key_text_color" to "candidate_text_color",
        "key_symbol_color" to "comment_text_color",
        "key_shadow_color" to "border_back_color",
        "label_color" to "candidate_text_color",
        "function_key_back_color" to "key_back_color",
        "function_key_border_color" to "key_border_color",
        "function_key_text_color" to "key_text_color",
        "function_key_symbol_color" to "key_symbol_color",
        "function_key_pressed_back_color" to "key_pressed_back_color",
        "function_key_pressed_border_color" to "key_pressed_border_color",
        "function_key_pressed_text_color" to "key_pressed_text_color",
        "function_key_pressed_symbol_color" to "key_pressed_symbol_color",
        "modifier_key_back_color" to "key_back_color",
        "modifier_key_border_color" to "key_border_color",
        "modifier_key_text_color" to "key_text_color",
        "modifier_key_symbol_color" to "key_symbol_color",
        "modifier_key_pressed_back_color" to "key_pressed_back_color",
        "modifier_key_pressed_border_color" to "key_pressed_border_color",
        "modifier_key_pressed_text_color" to "key_pressed_text_color",
        "modifier_key_pressed_symbol_color" to "key_pressed_symbol_color",
        "popup_back_color" to "key_back_color",
        "popup_text_color" to "key_text_color",
        "popup_pressed_back_color" to "key_pressed_back_color",
        "popup_pressed_text_color" to "key_pressed_text_color",
        "root_back_color" to "keyboard_back_color",
        "candidate_back_color" to "keyboard_back_color",
        "liquid_keyboard_back_color" to "keyboard_back_color",
        "long_text_color" to "key_text_color",
        "long_text_back_color" to "key_back_color",
        "preview_back_color" to "key_back_color",
        "preview_text_color" to "key_text_color",
    ).also {
        check(it.size == 54) { "fallback table changed: ${it.size}" }
    }
