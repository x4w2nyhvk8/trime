// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.util.Log
import com.charleskorn.kaml.YamlMap
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.util.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import timber.log.Timber

/**
 * What [ThemeLoader] does with a theme beside reading the package parts: it
 * normalizes key identities and reports what its static checks find.
 *
 * Reading the parts themselves is covered by [ThemePackageLoaderTest], and the
 * packaged themes of the app by [ThemeGoldenTest].
 */
class ThemeLoaderTest :
    BehaviorSpec({
        fun themeBody(yaml: String): YamlMap = ThemeTestSupport.yaml.parseToYamlNode(yaml.trimIndent()).mapping!!

        Given("a theme body whose layout entries name their keys") {
            val result = ThemeLoader.decodeAndReport(
                "fixture",
                themeBody(
                    """
                    config_version: "3.0"
                    name: fixture
                    preset_keys:
                      myButton: {label: 我}
                    preset_keyboards:
                      default:
                        keys:
                        - row:
                          - {key: myButton}
                          - {key: qButton}
                          - {key: myButton, click: explicit}
                    """,
                ),
                ResolvedColors(day = mapOf("back_color" to "#000000")),
            )
            val theme = result.shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>().theme
            val keys = theme.presetKeyboards.getValue("default").keys

            Then("a preset key name becomes a click on that preset") {
                keys[0].click shouldBe KeyActionToken.Plain("myButton")
                keys[0].key shouldBe "myButton"
            }

            Then("a built-in character key name becomes a click on its character") {
                keys[1].click shouldBe KeyActionToken.Plain("q")
            }

            Then("an explicit click wins over the name, which only names the key") {
                keys[2].click shouldBe KeyActionToken.Plain("explicit")
            }
        }

        Given("theme diagnostics") {
            /** Collects the lines the loader logs, one per finding. */
            class CollectingTree(val lines: MutableList<String>) : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    val level =
                        when (priority) {
                            Log.INFO -> "I"
                            Log.WARN -> "W"
                            else -> "?"
                        }
                    lines += "$level $message"
                }
            }

            val lines = mutableListOf<String>()
            val tree = CollectingTree(lines)
            beforeTest { Timber.plant(tree) }
            afterTest { Timber.uproot(tree) }

            Then("decodeAndReport decodes and reports in one step") {
                // The JVM cannot parse colors (android.graphics is not mocked),
                // so only the structural findings are asserted here; the color
                // findings are covered by ThemeDiagnosticsTest.
                val result = ThemeLoader.decodeAndReport(
                    "fixture",
                    themeBody(
                        """
                        config_version: "3.0"
                        name: fixture
                        style: {candidate_texts_size: 12}
                        height: 5
                        """,
                    ),
                    ResolvedColors(day = mapOf("back_color" to "#000000")),
                )
                result.shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>().theme.name shouldBe "fixture"
                lines.filter { "unknown key" in it } shouldBe
                    listOf(
                        "I Theme 'fixture': unknown key 'height' in ''; the runtime ignores it",
                        "W Theme 'fixture': unknown key 'candidate_texts_size' in 'style'; the runtime ignores it",
                    )
            }
        }
    })
