/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.core.RimeSchema
import com.osfans.trime.data.theme.model.Clipboard
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.ItemStyle
import com.osfans.trime.data.theme.model.OptionPanel
import com.osfans.trime.data.theme.model.Padding
import com.osfans.trime.data.theme.model.PanelStyle
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.switches.SwitchOptionEntry
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

private inline fun <reified T> decodeYaml(yaml: String): T = ThemeTestSupport.yaml.decodeFromYamlNode<T>(ThemeTestSupport.yaml.parseToYamlNode(yaml))

/**
 * The panel and list styles of doc/Theme.md §8: their two sections, the three
 * families of one `styles:` table, the item slot mapping, and the built-in
 * defaults that keep a theme without any style node at today's appearance.
 */
class PanelStyleTest :
    BehaviorSpec({
        /** A theme whose keyboard sizes are known, so the defaults are visible. */
        val base =
            Theme(
                name = "t",
                style = GeneralStyle(roundCorner = 8f, keyBorder = 2, candidateViewHeight = 30, commentHeight = 6, candidateTextSize = 18f),
            )

        Given("a padding value") {
            Then("a number pads every side") {
                decodeYaml<Padding>("4") shouldBe Padding(4, 4)
            }

            Then("a two-element list is [vertical, horizontal]") {
                decodeYaml<Padding>("[4, 8]") shouldBe Padding(4, 8)
            }

            Then("an absent value stays unset, so the built-in default applies") {
                decodeYaml<Padding>("~") shouldBe Padding()
                decodeYaml<Padding>("~").isSet shouldBe false
            }

            Then("anything else is a parse error") {
                Padding(0, 0).isSet shouldBe true
                runCatching { decodeYaml<Padding>("[1, 2, 3]") }.isFailure shouldBe true
                runCatching { decodeYaml<Padding>("abc") }.isFailure shouldBe true
            }
        }

        Given("a panel style node") {
            Then("its fields decode") {
                val style =
                    decodeYaml<PanelStyle>(
                        "{background: clipboard.9.png, corner_radius: 8, padding: [2, 3], height: 40, width: -1, " +
                            "max_height: 100, side_margin: 5, item_spacing: 6, action_button_size: 30}",
                    )
                style.background shouldBe "clipboard.9.png"
                style.cornerRadius shouldBe 8f
                style.padding shouldBe Padding(2, 3)
                style.height shouldBe 40
                style.width shouldBe -1
                style.maxHeight shouldBe 100
                style.sideMargin shouldBe 5
                style.itemSpacing shouldBe 6
                style.actionButtonSize shouldBe 30
            }

            Then("an empty node leaves every field unset") {
                val style = decodeYaml<PanelStyle>("{}")
                style.background shouldBe ""
                style.cornerRadius shouldBe -1f
                style.padding.isSet shouldBe false
                style.height shouldBe null
                style.width shouldBe null
                style.maxHeight shouldBe 0
                style.sideMargin shouldBe 0
                style.itemSpacing shouldBe -1
                style.actionButtonSize shouldBe 0
            }
        }

        Given("an item style node") {
            Then("its fields decode, including the ellipsis mode") {
                val style =
                    decodeYaml<ItemStyle>(
                        "{height: 40, min_height: 24, corner_radius: 6, border_width: 1, border_color: hilit, " +
                            "background: back, background_on: on, background_highlight: pressed, text_color: text, " +
                            "label_size: 13, max_lines: 2, ellipsize: end, icon_size: 30, icon_color: sym, " +
                            "pin_size: 10, pin_color: pin, pin_inset: 3, padding: 5}",
                    )
                style.height shouldBe 40
                style.minHeight shouldBe 24
                style.cornerRadius shouldBe 6f
                style.borderWidth shouldBe 1
                style.borderColor shouldBe "hilit"
                style.background shouldBe "back"
                style.backgroundOn shouldBe "on"
                style.backgroundHighlight shouldBe "pressed"
                style.textColor shouldBe "text"
                style.labelSize shouldBe 13f
                style.maxLines shouldBe 2
                style.ellipsize shouldBe ItemStyle.Ellipsize.END
                style.iconSize shouldBe 30
                style.iconColor shouldBe "sym"
                style.pinSize shouldBe 10
                style.pinColor shouldBe "pin"
                style.pinInset shouldBe 3
                style.padding shouldBe Padding(5, 5)
            }

            Then("an unknown ellipsis mode is a parse error") {
                runCatching { decodeYaml<ItemStyle>("{ellipsize: sideways}") }.isFailure shouldBe true
            }
        }

        Given("the two sections") {
            Then("each slot names a style") {
                val panel =
                    decodeYaml<OptionPanel>(
                        "{panel_style: p, action_item_style: a, switch_item_style: s, title_item_style: t}",
                    )
                panel.panelStyle shouldBe "p"
                panel.actionItemStyle shouldBe "a"
                panel.switchItemStyle shouldBe "s"
                panel.titleItemStyle shouldBe "t"

                val clipboard =
                    decodeYaml<Clipboard>(
                        "{panel_style: p, item_style: i, pinned_item_style: n, title_item_style: t}",
                    )
                clipboard.panelStyle shouldBe "p"
                clipboard.itemStyle shouldBe "i"
                clipboard.pinnedItemStyle shouldBe "n"
                clipboard.titleItemStyle shouldBe "t"
            }

            Then("an empty section leaves every slot unnamed") {
                decodeYaml<OptionPanel>("{}").actionItemStyle shouldBe ""
                decodeYaml<Clipboard>("{}").itemStyle shouldBe ""
            }
        }

        Given("an engine item kind") {
            Then("the option panel maps Static to action and Custom to switch") {
                val static = SwitchOptionEntry.Static("主题", 0, SwitchOptionEntry.Static.Type.ThemeList)
                static.itemKind shouldBe OptionPanelItemKind.ACTION
                static.isOn shouldBe false
                val custom = SwitchOptionEntry.Custom(RimeSchema.Switch(name = "ascii_mode"), true, "西文", 0)
                custom.itemKind shouldBe OptionPanelItemKind.SWITCH
                custom.isOn shouldBe true
            }

            Then("the clipboard maps pinned to its own slot") {
                ClipboardItemKind.of(pinned = false) shouldBe ClipboardItemKind.ITEM
                ClipboardItemKind.of(pinned = true) shouldBe ClipboardItemKind.PINNED
            }

            Then("each slot resolves through its section's name") {
                val panel = OptionPanel(panelStyle = "p", actionItemStyle = "a", switchItemStyle = "s", titleItemStyle = "t")
                ItemStyleResolver.slotName(panel, OptionPanelItemKind.ACTION) shouldBe "a"
                ItemStyleResolver.slotName(panel, OptionPanelItemKind.SWITCH) shouldBe "s"
                ItemStyleResolver.slotName(panel, OptionPanelItemKind.TITLE) shouldBe "t"

                val clipboard = Clipboard(panelStyle = "p", itemStyle = "i", pinnedItemStyle = "n", titleItemStyle = "t")
                ItemStyleResolver.slotName(clipboard, ClipboardItemKind.ITEM) shouldBe "i"
                ItemStyleResolver.slotName(clipboard, ClipboardItemKind.PINNED) shouldBe "n"
                ItemStyleResolver.slotName(clipboard, ClipboardItemKind.TITLE) shouldBe "t"
            }
        }

        Given("a panel size value") {
            Then("0 sizes to the content, -1 fills, a positive value is dp") {
                PanelSize.of(0) shouldBe PanelSize.Auto
                PanelSize.of(-1) shouldBe PanelSize.Fill
                PanelSize.of(48) shouldBe PanelSize.Fixed(48)
            }

            Then("it carries the layout-params value the views apply") {
                // WRAP_CONTENT and MATCH_PARENT as ViewGroup.LayoutParams spells them.
                PanelSize.Auto.layoutValue shouldBe -2
                PanelSize.Fill.layoutValue shouldBe -1
                PanelSize.Fixed(48).layoutValue shouldBe 48
            }

            Then("any other value a theme writes falls back to content sizing") {
                PanelSize.of(-5) shouldBe PanelSize.Auto
            }
        }

        Given("a theme that declares no panel or item style") {
            Then("the option panel keeps today's appearance") {
                val panel = PanelStyleResolver.panelStyle(base, PanelPage.OPTION_PANEL)
                panel.background shouldBe "option_panel_back_color"
                panel.cornerRadius shouldBe 0f
                panel.padding shouldBe Padding(0, 0)
                // An undeclared size keeps the host's own params, and the chrome
                // button today's candidate bar plus its comment line.
                panel.height shouldBe null
                panel.width shouldBe null
                panel.maxHeight shouldBe 0
                panel.sideMargin shouldBe 0
                panel.itemSpacing shouldBe 0
                panel.actionButtonSize shouldBe 36

                ItemStyleResolver.optionItemStyle(base, OptionPanelItemKind.ACTION).let {
                    it.height shouldBe 96
                    it.iconSize shouldBe 48
                    it.labelSize shouldBe 12f
                    it.cornerRadius shouldBe 8f
                    it.borderWidth shouldBe 2
                    it.borderColor shouldBe "key_border_color"
                    it.background shouldBe "key_back_color"
                    it.textColor shouldBe "key_text_color"
                    it.padding shouldBe Padding(4, 6)
                }
            }

            Then("the clipboard keeps today's appearance") {
                val panel = PanelStyleResolver.panelStyle(base, PanelPage.CLIPBOARD)
                panel.background shouldBe "clipboard_back_color"
                panel.itemSpacing shouldBe 4
                panel.height shouldBe null
                panel.actionButtonSize shouldBe 36

                ItemStyleResolver.clipboardItemStyle(base, ClipboardItemKind.ITEM).let {
                    it.height shouldBe 0
                    it.minHeight shouldBe 30
                    it.labelSize shouldBe 14f
                    it.maxLines shouldBe 4
                    it.ellipsize shouldBe ItemStyle.Ellipsize.END
                    it.pinSize shouldBe 12
                    it.pinInset shouldBe 2
                    it.padding shouldBe Padding(4, 8)
                    // The list paints one shared surface, so a row is transparent.
                    it.background shouldBe ""
                    it.textColor shouldBe "key_text_color"
                    it.pinColor shouldBe "key_symbol_color"
                }

                ItemStyleResolver.clipboardItemStyle(base, ClipboardItemKind.PINNED).minHeight shouldBe 30

                // The title row used to borrow the candidate bar's height.
                ItemStyleResolver.clipboardItemStyle(base, ClipboardItemKind.TITLE).let {
                    it.height shouldBe 36
                    it.labelSize shouldBe 18f
                    it.background shouldBe ""
                    it.ellipsize shouldBe null
                }
            }
        }

        Given("a theme that declares the styles") {
            val theme =
                base.copy(
                    optionPanel = OptionPanel(panelStyle = "p", actionItemStyle = "a", switchItemStyle = "s", titleItemStyle = "t"),
                    clipboard = Clipboard(panelStyle = "p2", itemStyle = "i", pinnedItemStyle = "n", titleItemStyle = "t2"),
                    panelStyles =
                    mapOf(
                        "p" to
                            PanelStyle(
                                background = "panel.png",
                                cornerRadius = 12f,
                                padding = Padding(6, 8),
                                height = 40,
                                width = -1,
                                maxHeight = 120,
                                sideMargin = 3,
                                itemSpacing = 5,
                                actionButtonSize = 30,
                            ),
                        "p2" to PanelStyle(background = "clip_token", height = 0),
                    ),
                    itemStyles =
                    mapOf(
                        "a" to ItemStyle(height = 60, iconSize = 30, labelSize = 11f, cornerRadius = 4f, borderWidth = 1, padding = Padding(2, 3)),
                        "s" to ItemStyle(background = "off", backgroundOn = "on"),
                        "i" to ItemStyle(minHeight = 20, backgroundHighlight = "pressed"),
                    ),
                )

            Then("the panel style overrides the defaults field by field") {
                PanelStyleResolver.panelStyle(theme, PanelPage.OPTION_PANEL).let {
                    it.background shouldBe "panel.png"
                    it.cornerRadius shouldBe 12f
                    it.padding shouldBe Padding(6, 8)
                    it.height shouldBe PanelSize.Fixed(40)
                    it.width shouldBe PanelSize.Fill
                    it.maxHeight shouldBe 120
                    it.sideMargin shouldBe 3
                    it.itemSpacing shouldBe 5
                    it.actionButtonSize shouldBe 30
                }
                PanelStyleResolver.panelStyle(theme, PanelPage.CLIPBOARD).let {
                    it.background shouldBe "clip_token"
                    it.height shouldBe PanelSize.Auto
                    it.width shouldBe null
                    it.itemSpacing shouldBe 4
                    it.actionButtonSize shouldBe 36
                }
            }

            Then("an item style overrides only what it sets") {
                ItemStyleResolver.optionItemStyle(theme, OptionPanelItemKind.ACTION).let {
                    it.height shouldBe 60
                    it.iconSize shouldBe 30
                    it.labelSize shouldBe 11f
                    it.cornerRadius shouldBe 4f
                    it.borderWidth shouldBe 1
                    it.padding shouldBe Padding(2, 3)
                    // Not set, so the defaults stay.
                    it.background shouldBe "key_back_color"
                    it.textColor shouldBe "key_text_color"
                    it.minHeight shouldBe 0
                }
                ItemStyleResolver.optionItemStyle(theme, OptionPanelItemKind.SWITCH).let {
                    it.backgroundFor(on = false) shouldBe "off"
                    it.backgroundFor(on = true) shouldBe "on"
                }
                ItemStyleResolver.clipboardItemStyle(theme, ClipboardItemKind.ITEM).backgroundHighlight shouldBe "pressed"
            }

            Then("a switch without an on-background falls back to the normal one") {
                ItemStyleResolver.optionItemStyle(theme, OptionPanelItemKind.ACTION).backgroundFor(on = true) shouldBe "key_back_color"
            }
        }

        Given("the one styles table") {
            val theme =
                base.copy(
                    presetKeyboards =
                    mapOf(
                        "kb" to
                            TextKeyboard(
                                keys =
                                listOf(
                                    TextKeyboard.TextKey(style = "keyStyle"),
                                    TextKeyboard.TextKey(style = "shared"),
                                ),
                            ),
                    ),
                    optionPanel = OptionPanel(panelStyle = "panelStyle", actionItemStyle = "shared"),
                    clipboard = Clipboard(panelStyle = "shared", itemStyle = "itemStyle", pinnedItemStyle = "shared"),
                )

            Then("each name's family is derived from the reference sites") {
                val families = StyleFamilyResolver.references(theme)
                families["keyStyle"] shouldBe setOf(StyleFamily.KEY)
                families["panelStyle"] shouldBe setOf(StyleFamily.PANEL)
                families["itemStyle"] shouldBe setOf(StyleFamily.ITEM)
                families["shared"] shouldBe setOf(StyleFamily.KEY, StyleFamily.PANEL, StyleFamily.ITEM)
                families["unused"] shouldBe null
            }

            Then("a node whose fields say nothing is left to its references") {
                StyleFamilyResolver.declaresKeyFamily(setOf("content", "text")) shouldBe true
                StyleFamilyResolver.declaresKeyFamily(setOf("nine_patch")) shouldBe true
                StyleFamilyResolver.declaresKeyFamily(setOf("background", "corner_radius", "padding")) shouldBe false
                StyleFamilyResolver.declaresKeyFamily(setOf("icon_size")) shouldBe false
            }

            Then("allowed fields follow the families, and are unrestricted when nothing references the node") {
                StyleFamilyResolver.allowedFields(setOf(StyleFamily.PANEL)) shouldBe StyleFamilyResolver.PANEL_FIELDS
                StyleFamilyResolver.allowedFields(setOf(StyleFamily.KEY, StyleFamily.ITEM)) shouldBe StyleFamilyResolver.KEY_FIELDS + StyleFamilyResolver.ITEM_FIELDS
                StyleFamilyResolver.allowedFields(emptySet()) shouldBe StyleFamilyResolver.ALL_FIELDS
                StyleFamilyResolver.ALL_FIELDS.contains("max_height") shouldBe true
            }
        }

        Given("the panel color tokens") {
            Then("they are typed color keys like any other") {
                // Before this slice a theme could not define them; the tables
                // resolve them through ColorKey now.
                ColorKey.from("option_panel_back_color") shouldBe ColorKey.OPTION_PANEL_BACK_COLOR
                ColorKey.from("clipboard_back_color") shouldBe ColorKey.CLIPBOARD_BACK_COLOR
                ColorKey.OPTION_PANEL_BACK_COLOR.key shouldBe "option_panel_back_color"
            }
        }
    })
