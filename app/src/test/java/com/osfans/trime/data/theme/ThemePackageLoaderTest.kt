/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.ime.keyboard.KeyCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

/**
 * The v3 theme package format: `<id>/{theme.yaml, day.yaml, night.yaml}`.
 * Covers discovery, the required/optional parts and the day/night split; the
 * shipped theme package is pinned by [ThemeGoldenTest] and what the loader does
 * with a decoded theme by [ThemeLoaderTest].
 */
class ThemePackageLoaderTest :
    BehaviorSpec({
        // The Android key-name lookup is a platform call that throws when not
        // mocked; loading a theme lints it, so it must be pinned down.
        beforeTest { KeyCode.androidKeyNameToCode = { 0 } }

        val body = "config_version: \"3.0\"\nname: My Theme\nstyle: {}"
        val day = "back_color: \"#000000\"\ntext_color: \"#ffffff\""
        val night = "back_color: \"#1c1c1e\"\ntext_color: \"#f0f0f0\""

        fun tempDir(): File = File.createTempFile("theme-package", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        /** A package directory named [themeId], with only the parts that are not null. */
        fun packageDir(
            themeId: String,
            themeYaml: String?,
            dayYaml: String?,
            nightYaml: String?,
            images: List<String> = emptyList(),
        ): File {
            val dir = File(tempDir(), themeId).apply { mkdirs() }
            themeYaml?.let { File(dir, ThemeLoader.THEME_FILE).writeText(it) }
            dayYaml?.let { File(dir, ThemeLoader.DAY_FILE).writeText(it) }
            nightYaml?.let { File(dir, ThemeLoader.NIGHT_FILE).writeText(it) }
            images.forEach { relative ->
                File(dir, relative).apply { parentFile?.mkdirs() }.writeText("dummy")
            }
            return dir
        }

        Given("a theme package with theme.yaml and day.yaml") {
            val dir = packageDir("mytheme", body, day, null)
            val result = ThemeLoader.loadThemePackage("mytheme", dir)

            Then("it loads, carrying the day table and its own package directory") {
                val success = result.shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>()
                success.theme.name shouldBe "My Theme"
                success.theme.dayColors shouldBe mapOf("back_color" to "#000000", "text_color" to "#ffffff")
                success.theme.nightColors shouldBe null
                success.theme.packageDir shouldBe dir
            }
        }

        Given("a theme package that also declares night.yaml") {
            val dir = packageDir("nightly", body, day, night)
            val result = ThemeLoader.loadThemePackage("nightly", dir)

            Then("the night table lands on the theme") {
                val success = result.shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>()
                success.theme.dayColors shouldBe mapOf("back_color" to "#000000", "text_color" to "#ffffff")
                success.theme.nightColors shouldBe mapOf("back_color" to "#1c1c1e", "text_color" to "#f0f0f0")
            }
        }

        Given("a theme package whose color table names its own image") {
            When("the image is in the package's images/") {
                val dir =
                    packageDir(
                        "imaged",
                        body,
                        "back_color: \"#000000\"\nkey_back_color: \"splash.9.png\"",
                        null,
                        images = listOf("images/splash.9.png"),
                    )

                Then("the theme loads") {
                    val success =
                        ThemeLoader.loadThemePackage("imaged", dir)
                            .shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>()
                    success.theme.packageDir shouldBe dir
                }
            }

            When("the image is missing from the package") {
                val dir = packageDir("no-image", body, "back_color: \"#000000\"\nkey_back_color: \"splash.9.png\"", null)

                Then("the theme is refused, naming the missing resource") {
                    // doc/Theme.md §3: both lookups missing is a hard error.
                    val failure =
                        ThemeLoader.loadThemePackage("no-image", dir)
                            .shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Failure>()
                    val error =
                        failure.error
                            .shouldBeInstanceOf<ThemeLoader.ThemeLoadError.DiagnosticErrors>()
                    error.findings.map { it.code } shouldBe
                        listOf(ThemeDiagnostics.Code.MISSING_THEME_RESOURCE)
                    error.findings.single().path shouldBe "day.yaml/key_back_color"
                }
            }
        }

        Given("a package whose required parts are missing") {
            When("theme.yaml is missing") {
                val dir = packageDir("no-body", null, day, null)

                Then("the load reports the missing part") {
                    val failure =
                        ThemeLoader.loadThemePackage("no-body", dir)
                            .shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Failure>()
                    failure.error.shouldBeInstanceOf<ThemeLoader.ThemeLoadError.MissingPart>()
                        .part shouldBe ThemeLoader.THEME_FILE
                }
            }

            When("day.yaml is missing") {
                val dir = packageDir("no-day", body, null, null)

                Then("the load reports the missing part") {
                    val failure =
                        ThemeLoader.loadThemePackage("no-day", dir)
                            .shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Failure>()
                    failure.error.shouldBeInstanceOf<ThemeLoader.ThemeLoadError.MissingPart>()
                        .part shouldBe ThemeLoader.DAY_FILE
                }
            }
        }

        Given("a package whose day.yaml is empty") {
            val dir = packageDir("empty-day", body, "{}", null)
            val result = ThemeLoader.loadThemePackage("empty-day", dir)

            Then("the theme is refused, because nothing could be rendered") {
                val failure = result.shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Failure>()
                failure.error.shouldBeInstanceOf<ThemeLoader.ThemeLoadError.NoDayColors>()
            }
        }

        Given("a package whose color table carries a structural key") {
            val dir = packageDir("structural", body, "back_color: \"#000000\"\nstyle: {}", null)

            Then("the theme is refused, carrying the error-level findings") {
                // doc/Theme.md §5: a color table only carries color tokens, so a
                // structural key there is a hard error, not a warning.
                val failure =
                    ThemeLoader.loadThemePackage("structural", dir)
                        .shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Failure>()
                val error =
                    failure.error
                        .shouldBeInstanceOf<ThemeLoader.ThemeLoadError.DiagnosticErrors>()
                error.findings.map { it.code } shouldBe
                    listOf(ThemeDiagnostics.Code.STRUCTURAL_KEY_IN_COLORS)
            }
        }

        Given("a package whose day.yaml is not valid YAML") {
            val dir = packageDir("broken-yaml", body, "a: [\n", null)
            val result = ThemeLoader.loadThemePackage("broken-yaml", dir)

            Then("the parse error is reported") {
                val failure = result.shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Failure>()
                failure.error.shouldBeInstanceOf<ThemeLoader.ThemeLoadError.YamlParseError>()
            }
        }

        Given("the package lookup") {
            val root = tempDir()

            fun writePackage(dir: File) {
                dir.mkdirs()
                File(dir, ThemeLoader.THEME_FILE).writeText(body)
                File(dir, ThemeLoader.DAY_FILE).writeText(day)
            }

            When("the directory name carries the recommended .trime suffix") {
                val dir = File(root, "suffixed.trime")
                writePackage(dir)

                Then("the plain id resolves it") {
                    ThemeLoader.findThemePackageDir("suffixed", listOf(root)) shouldBe dir
                }
            }

            When("a name would resolve outside the root") {
                val outside = File(root.parentFile, "outside-package")
                writePackage(outside)

                Then("it is refused") {
                    ThemeLoader.findThemePackageDir("../${outside.name}", listOf(root)) shouldBe null
                }
            }

            When("the theme has no package at all") {
                Then("no directory is returned") {
                    ThemeLoader.findThemePackageDir("missing", listOf(root)) shouldBe null
                }
            }
        }

        Given("the theme list") {
            val root = tempDir()
            val packaged = File(root, "mytheme.trime")
            packaged.mkdirs()
            File(packaged, ThemeLoader.THEME_FILE).writeText("name: Packaged\nstyle: {}")
            File(packaged, ThemeLoader.DAY_FILE).writeText(day)
            // A directory without theme.yaml, and a plain YAML file, are not themes.
            File(root, "not-a-theme").mkdirs()
            File(root, "notes.yaml").writeText("name: Notes\n")

            Then("a package is listed by its directory name, with the suffix stripped") {
                // Only a directory holding a `theme.yaml` is a theme; a plain
                // YAML file in the same directory is not listed.
                ThemeFilesManager.listThemes(root).map { it.configId to it.name } shouldBe
                    listOf("mytheme" to "Packaged")
            }
        }
    })
