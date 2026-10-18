/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.KeyLabel
import com.osfans.trime.data.theme.model.KeyStyle
import com.osfans.trime.data.theme.model.TextKeyboard
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * The styles table the resolver matrix resolves against: one node per content
 * type, each carrying the fields its content uses.
 */
private val keyStyleFixtures =
    mapOf(
        "textStyle" to KeyStyle(content = KeyStyle.Content.TEXT, text = "style", textSize = 20f),
        "iconStyle" to KeyStyle(content = KeyStyle.Content.SYSTEM_ICON, systemIcon = "shift"),
        "sizedIconStyle" to KeyStyle(content = KeyStyle.Content.SYSTEM_ICON, systemIcon = "shift", iconSize = 18),
        "assetStyle" to KeyStyle(content = KeyStyle.Content.ASSET_ICON, assetIcon = "voice_icon.png"),
        "patchStyle" to KeyStyle(content = KeyStyle.Content.NINE_PATCH, ninePatch = "key_9p.9.png"),
        "geometryStyle" to
            KeyStyle(
                content = KeyStyle.Content.GEOMETRY,
                cornerRadius = 6f,
                borderWidth = 2,
                borderColor = "key_border_color",
            ),
    )

/**
 * The `styles:` table and the priority matrix a key resolves with:
 * placement > style > built-in default (doc/Theme.md §4.1,
 * doc/Keyboard.md §2.1). Pure data, no Android.
 */
class KeyStyleResolverTest :
    BehaviorSpec({
        fun key(
            style: String = "",
            label: KeyLabel = KeyLabel.None,
            textSize: Float = 0f,
            backColor: String = "",
            borderColor: String = "",
            cornerRadius: Float = -1f,
            border: Int = -1,
        ) = TextKeyboard.TextKey(
            style = style,
            label = label,
            keyTextSize = textSize,
            keyBackColor = backColor,
            keyBorderColor = borderColor,
            roundCorner = cornerRadius,
            keyBorder = border,
        )

        fun resolve(
            placement: TextKeyboard.TextKey?,
            from: Map<String, KeyStyle> = keyStyleFixtures,
        ) = KeyStyleResolver.resolve(placement, from)

        Given("a theme's styles table") {
            val theme =
                ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(
                    ThemeTestSupport.yaml.parseToYamlNode(
                        """
                        name: t
                        styles:
                          spaceKeyTextStyle: {content: text, text: 空格, text_size: 20}
                          shiftButtonForegroundStyle: {content: system_icon, system_icon: shift}
                          spaceKeyIconStyle: {content: asset_icon, asset_icon: voice_icon.png}
                          spaceKeyNinePatchStyle: {content: nine_patch, nine_patch: key_9p.9.png}
                          letterButtonBackgroundStyle: {content: geometry, corner_radius: 6, border_width: 2, border_color: key_border_color}
                        """.trimIndent(),
                    ),
                )

            Then("every node decodes with its snake_case content and its fields") {
                theme.styles.getValue("spaceKeyTextStyle").let {
                    it.content shouldBe KeyStyle.Content.TEXT
                    it.text shouldBe "空格"
                    it.textSize shouldBe 20f
                }
                theme.styles.getValue("shiftButtonForegroundStyle").systemIcon shouldBe "shift"
                theme.styles.getValue("spaceKeyIconStyle").assetIcon shouldBe "voice_icon.png"
                theme.styles.getValue("spaceKeyNinePatchStyle").ninePatch shouldBe "key_9p.9.png"
                theme.styles.getValue("letterButtonBackgroundStyle").let {
                    it.content shouldBe KeyStyle.Content.GEOMETRY
                    it.cornerRadius shouldBe 6f
                    it.borderWidth shouldBe 2
                    it.borderColor shouldBe "key_border_color"
                }
            }

            Then("content decides the layer: text and icons fill the foreground") {
                theme.styles.getValue("spaceKeyTextStyle").layer shouldBe KeyStyle.Layer.FOREGROUND
                theme.styles.getValue("shiftButtonForegroundStyle").layer shouldBe KeyStyle.Layer.FOREGROUND
                theme.styles.getValue("spaceKeyIconStyle").layer shouldBe KeyStyle.Layer.FOREGROUND
            }

            Then("content decides the layer: nine_patch and geometry fill the background") {
                theme.styles.getValue("spaceKeyNinePatchStyle").layer shouldBe KeyStyle.Layer.BACKGROUND
                theme.styles.getValue("letterButtonBackgroundStyle").layer shouldBe KeyStyle.Layer.BACKGROUND
            }

            Then("the fields a node sets are split by layer") {
                theme.styles.getValue("spaceKeyTextStyle").foregroundFields shouldBe listOf("text", "text_size")
                theme.styles.getValue("spaceKeyTextStyle").backgroundFields shouldBe emptyList()
                theme.styles.getValue("spaceKeyNinePatchStyle").backgroundFields shouldBe listOf("nine_patch")
                theme.styles.getValue("letterButtonBackgroundStyle").backgroundFields shouldBe
                    listOf("corner_radius", "border_width", "border_color")
            }
        }

        Given("a content spelling") {
            Then("both the contract's snakes and the Kotlin spelling resolve") {
                KeyStyle.Content.of("text") shouldBe KeyStyle.Content.TEXT
                KeyStyle.Content.of("system_icon") shouldBe KeyStyle.Content.SYSTEM_ICON
                KeyStyle.Content.of("SYSTEM_ICON") shouldBe KeyStyle.Content.SYSTEM_ICON
                KeyStyle.Content.of("nine_patch") shouldBe KeyStyle.Content.NINE_PATCH
                KeyStyle.Content.of(" asset_icon ") shouldBe KeyStyle.Content.ASSET_ICON
            }

            Then("a spelling that names no content resolves to nothing") {
                KeyStyle.Content.of("nope") shouldBe null
            }

            Then("an unknown content fails the parse instead of defaulting") {
                runCatching {
                    ThemeTestSupport.yaml.decodeFromYamlNode<KeyStyle>(
                        ThemeTestSupport.yaml.parseToYamlNode("content: nope"),
                    )
                }.isFailure shouldBe true
            }
        }

        Given("a placement that sets nothing") {
            Then("the referenced style supplies text and size") {
                resolve(key(style = "textStyle")).let {
                    it.styleName shouldBe "textStyle"
                    it.content shouldBe KeyStyle.Content.TEXT
                    it.layer shouldBe KeyStyle.Layer.FOREGROUND
                    it.text shouldBe "style"
                    it.textSize shouldBe 20f
                }
            }
        }

        Given("a placement that also sets what the style sets") {
            Then("the placement wins for text and size") {
                resolve(key(style = "textStyle", label = KeyLabel.Text("placement"), textSize = 30f)).let {
                    it.text shouldBe "placement"
                    it.textSize shouldBe 30f
                }
            }
        }

        Given("a placement without a style") {
            Then("only its own fields and the defaults apply") {
                resolve(key(label = KeyLabel.Text("A"))).let {
                    it.styleName shouldBe ""
                    it.content shouldBe KeyStyle.Content.TEXT
                    it.text shouldBe "A"
                    it.textSize shouldBe 0f
                    it.cornerRadius shouldBe -1f
                    it.borderWidth shouldBe -1
                    it.borderColor shouldBe ""
                    it.background shouldBe KeyBackground.Scheme
                }
            }

            Then("an undefined style name behaves like no style, it is the linter's job") {
                resolve(key(style = "missing", label = KeyLabel.Text("A"))).let {
                    it.styleName shouldBe "missing"
                    it.content shouldBe KeyStyle.Content.TEXT
                    it.text shouldBe "A"
                }
            }
        }

        Given("a system_icon style") {
            Then("the icon is the foreground when the placement has no label") {
                resolve(key(style = "iconStyle")).let {
                    it.layer shouldBe KeyStyle.Layer.FOREGROUND
                    it.systemIcon shouldBe "shift"
                    it.assetIcon shouldBe ""
                }
            }

            Then("a placement label takes the slot instead") {
                resolve(key(style = "iconStyle", label = KeyLabel.Text("A"))).let {
                    it.systemIcon shouldBe ""
                    it.text shouldBe "A"
                }
            }
        }

        Given("an asset_icon style") {
            Then("the package image is the foreground") {
                resolve(key(style = "assetStyle")).let {
                    it.assetIcon shouldBe "voice_icon.png"
                    it.systemIcon shouldBe ""
                }
            }

            Then("a placement label takes the slot instead") {
                resolve(key(style = "assetStyle", label = KeyLabel.Text("A"))).let {
                    it.assetIcon shouldBe ""
                    it.text shouldBe "A"
                }
            }
        }

        Given("a nine_patch style") {
            Then("the package image supplies the background") {
                resolve(key(style = "patchStyle")).let {
                    it.layer shouldBe KeyStyle.Layer.BACKGROUND
                    it.background shouldBe KeyBackground.NinePatch("key_9p.9.png")
                }
            }

            Then("a placement's own key_back_color takes the slot back") {
                resolve(key(style = "patchStyle", backColor = "#ff0000")).background shouldBe
                    KeyBackground.Scheme
            }

            Then("a nine_patch style without a value falls back to the scheme") {
                resolve(key(style = "patchStyle"), mapOf("patchStyle" to KeyStyle(content = KeyStyle.Content.NINE_PATCH)))
                    .background shouldBe KeyBackground.Scheme
            }
        }

        Given("a geometry style") {
            Then("it supplies the shape without replacing the background") {
                resolve(key(style = "geometryStyle")).let {
                    it.layer shouldBe KeyStyle.Layer.BACKGROUND
                    it.background shouldBe KeyBackground.Scheme
                    it.cornerRadius shouldBe 6f
                    it.borderWidth shouldBe 2
                    it.borderColor shouldBe "key_border_color"
                }
            }

            Then("the placement's own shape fields win") {
                resolve(
                    key(style = "geometryStyle", cornerRadius = 10f, border = 5, borderColor = "#000000"),
                ).let {
                    it.cornerRadius shouldBe 10f
                    it.borderWidth shouldBe 5
                    it.borderColor shouldBe "#000000"
                }
            }
        }

        Given("a text style") {
            Then("its shape fields stay unset, so the key keeps the keyboard's") {
                resolve(key(style = "textStyle")).let {
                    it.cornerRadius shouldBe -1f
                    it.borderWidth shouldBe -1
                }
            }
        }

        Given("the resolved label") {
            Then("a static placement label wins over the style's text") {
                resolve(key(style = "textStyle", label = KeyLabel.Text("placement"))).let {
                    it.label shouldBe KeyLabel.Text("placement")
                    it.text shouldBe "placement"
                }
            }

            Then("without one, the style's text becomes a static label") {
                resolve(key(style = "textStyle")).let {
                    it.label shouldBe KeyLabel.Text("style")
                    it.text shouldBe "style"
                }
            }

            Then("with neither, there is no label at all") {
                resolve(key()).let {
                    it.label shouldBe KeyLabel.None
                    it.text shouldBe ""
                }
            }

            Then("a dynamic placement label is carried unresolved, hiding the style's text") {
                // The entry declared a label, so it takes the slot even though a
                // dynamic source has no static text (placement > style).
                resolve(key(style = "textStyle", label = KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_NAME))).let {
                    it.label shouldBe KeyLabel.Dynamic(KeyLabel.Dynamic.Source.SCHEMA_NAME)
                    it.text shouldBe ""
                }
            }

            Then("a blank static label declares nothing, so the style's text applies") {
                resolve(key(style = "textStyle", label = KeyLabel.Text(""))).label shouldBe KeyLabel.Text("style")
            }

            Then("a dynamic placement label also takes the slot from the style's icon") {
                resolve(key(style = "iconStyle", label = KeyLabel.Dynamic(KeyLabel.Dynamic.Source.ENTER_LABEL))).let {
                    it.systemIcon shouldBe ""
                    it.label shouldBe KeyLabel.Dynamic(KeyLabel.Dynamic.Source.ENTER_LABEL)
                }
            }
        }

        Given("a style that sizes its icon") {
            Then("the icon size is carried") {
                resolve(key(style = "sizedIconStyle")).iconSize shouldBe 18
            }

            Then("it stays 0 when unset, so the key's text size decides") {
                // A placement has no icon_size of its own: the chain is the
                // style's value, else 0.
                resolve(key(style = "iconStyle")).iconSize shouldBe 0
                resolve(key()).iconSize shouldBe 0
            }
        }
    })
