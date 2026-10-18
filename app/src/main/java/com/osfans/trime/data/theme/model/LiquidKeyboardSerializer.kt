/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.osfans.trime.data.theme.ThemeYaml
import com.osfans.trime.util.pairs
import com.osfans.trime.util.splitWithSurrogates
import com.osfans.trime.util.string
import com.osfans.trime.util.yamlNode
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import timber.log.Timber

/**
 * Decodes the liquid keyboard section, which mixes fixed keys with one dynamic
 * key per keyboard: its metrics and fixed key bar sit next to one section per
 * keyboard id, and only the `keyboards` list names those ids.
 *
 * Only the node tree is split here: the fixed keys are parsed by the generated
 * serializer of [Header], each keyboard by the generated serializer of [Body].
 * The shapes the hand-written decoding used to tolerate keep working — a
 * missing, null or unrelated section gives the defaults, a keyboard that fails
 * to decode is skipped with a warning instead of failing the whole theme.
 */
@OptIn(InternalSerializationApi::class)
internal object LiquidKeyboardSerializer : KSerializer<LiquidKeyboard> {
    /** The fixed keys of the section; every other key of the mapping is a keyboard id. */
    private val fixedKeys =
        setOf(
            "single_width",
            "key_height",
            "margin_x",
            "fixed_key_bar",
            "keyboards",
            "cell_back_color",
            "cell_border",
            "cell_border_color",
            "cell_corner_radius",
            "cell_text_color",
            "cell_divider",
            "cell_divider_color",
            "tab_spacing",
            "tab_divider",
            "tab_divider_color",
            "tab_corner_radius",
            "tab_back_color",
            "tab_active_back_color",
            "tab_text_color",
            "tab_active_text_color",
            "cell_text_size",
            "cell_padding",
            "margin_y",
            "cell_highlight_color",
            "tab_text_size",
            "tab_height",
            "tab_padding",
            "tab_min_width",
            "tab_column_width",
        )

    private val headerSerializer = Header.serializer()
    private val bodySerializer = Body.serializer()

    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("com.osfans.trime.data.theme.model.LiquidKeyboard", SerialKind.CONTEXTUAL)

    override fun deserialize(decoder: Decoder): LiquidKeyboard {
        val root = decoder.yamlNode() as? YamlMap ?: return LiquidKeyboard()
        val header = runCatching {
            ThemeYaml.parser.decodeFromYamlNode(headerSerializer, root.only(fixedKeys))
        }.onFailure {
            Timber.w(it, "Failed to decode LiquidKeyboard itself")
        }.getOrElse { Header() }
        val keyboards = header.keyboards.mapNotNull decode@{ id ->
            val node = root.pairs[id] as? YamlMap ?: return@decode null
            runCatching {
                val body = ThemeYaml.parser.decodeFromYamlNode(bodySerializer, node)
                LiquidKeyboard.Keyboard(
                    id = id,
                    type = body.type,
                    name = body.name ?: id,
                    keys = body.keys.toKeyItems(body.type),
                )
            }.onFailure {
                Timber.w(it, "Failed to decode LiquidKeyboard property 'keyboards'")
            }.getOrNull()
        }
        return LiquidKeyboard(
            singleWidth = header.singleWidth,
            keyHeight = header.keyHeight,
            marginX = header.marginX,
            fixedKeyBar = header.fixedKeyBar,
            keyboards = keyboards,
            cellBackColor = header.cellBackColor,
            cellBorder = header.cellBorder,
            cellBorderColor = header.cellBorderColor,
            cellCornerRadius = header.cellCornerRadius,
            cellTextColor = header.cellTextColor,
            cellDivider = header.cellDivider,
            cellDividerColor = header.cellDividerColor,
            tabSpacing = header.tabSpacing,
            tabDivider = header.tabDivider,
            tabDividerColor = header.tabDividerColor,
            tabCornerRadius = header.tabCornerRadius,
            tabBackColor = header.tabBackColor,
            tabActiveBackColor = header.tabActiveBackColor,
            tabTextColor = header.tabTextColor,
            tabActiveTextColor = header.tabActiveTextColor,
            cellTextSize = header.cellTextSize,
            cellPadding = header.cellPadding,
            marginY = header.marginY,
            cellHighlightColor = header.cellHighlightColor,
            tabTextSize = header.tabTextSize,
            tabHeight = header.tabHeight,
            tabPadding = header.tabPadding,
            tabMinWidth = header.tabMinWidth,
            tabColumnWidth = header.tabColumnWidth,
        )
    }

    override fun serialize(encoder: Encoder, value: LiquidKeyboard) = throw UnsupportedOperationException("Theme configuration is read-only")

    /** The fixed part of the section. */
    @Serializable
    private data class Header(
        val singleWidth: Int = 0,
        val keyHeight: Int = 0,
        val marginX: Float = 0f,
        val fixedKeyBar: LiquidKeyboard.KeyBar = LiquidKeyboard.KeyBar(),
        val keyboards: List<String> = emptyList(),
        val cellBackColor: String = "",
        val cellBorder: Int = -1,
        val cellBorderColor: String = "",
        val cellCornerRadius: Float = -1f,
        val cellTextColor: String = "",
        val cellDivider: Int = 0,
        val cellDividerColor: String = "",
        val tabSpacing: Int = 0,
        val tabDivider: Int = 0,
        val tabDividerColor: String = "",
        val tabCornerRadius: Float = -1f,
        val tabBackColor: String = "",
        val tabActiveBackColor: String = "",
        val tabTextColor: String = "",
        val tabActiveTextColor: String = "",
        val cellTextSize: Float = 0f,
        val cellPadding: Padding = Padding(),
        val marginY: Float = -1f,
        val cellHighlightColor: String = "",
        val tabTextSize: Float = 0f,
        val tabHeight: Int = 0,
        val tabPadding: Padding = Padding(),
        val tabMinWidth: Int = 0,
        val tabColumnWidth: Int = 0,
    )

    /** One keyboard section; its id is the key it is stored under. */
    @Serializable
    private data class Body(
        val type: LiquidKeyboard.DataType,
        val name: String? = null,
        val keys: Keys = Keys(),
    )

    /**
     * The `keys` of a keyboard: either text that is split into keys, or the keys
     * written out. A scalar of a [LiquidKeyboard.DataType.SINGLE] keyboard holds
     * one key per code point, every other scalar one key per line.
     */
    @Serializable(with = KeysSerializer::class)
    private data class Keys(
        val text: String? = null,
        val items: List<LiquidKeyboard.KeyItem> = emptyList(),
    ) {
        fun toKeyItems(type: LiquidKeyboard.DataType): List<LiquidKeyboard.KeyItem> = text?.let {
            if (type == LiquidKeyboard.DataType.SINGLE) {
                it.splitWithSurrogates().map(::keyItem)
            } else {
                it.split("\n+".toRegex()).filter(String::isNotEmpty).map(::keyItem)
            }
        } ?: items
    }

    @OptIn(InternalSerializationApi::class)
    private object KeysSerializer : KSerializer<Keys> {
        override val descriptor: SerialDescriptor =
            buildSerialDescriptor("com.osfans.trime.data.theme.model.LiquidKeyboard.Keys", SerialKind.CONTEXTUAL)

        override fun deserialize(decoder: Decoder): Keys = when (val node = decoder.yamlNode()) {
            is YamlList -> Keys(items = node.items.flatMap(::keyItemsOf))

            // Everything that is not a list used to be read as the text to
            // split, and a missing value then means "no keys".
            else -> Keys(text = node.string ?: "")
        }

        override fun serialize(encoder: Encoder, value: Keys) = throw UnsupportedOperationException("Theme configuration is read-only")
    }

    private fun keyItem(text: String) = LiquidKeyboard.KeyItem(text)

    /** One entry of a `keys` list: a bare key, or a mapping describing keys. */
    private fun keyItemsOf(node: YamlNode): List<LiquidKeyboard.KeyItem> = when (node) {
        is YamlScalar -> listOf(keyItem(node.content))

        is YamlMap -> if ("click" in node.pairs) {
            listOf(
                LiquidKeyboard.KeyItem(
                    text = node.pairs["click"]?.string.orEmpty(),
                    altText = node.pairs["label"]?.string.orEmpty(),
                ),
            )
        } else {
            node.pairs.map { (key, value) -> LiquidKeyboard.KeyItem(key, value.string.orEmpty()) }
        }

        else -> emptyList()
    }
}

/** Returns the entries of this mapping that are stored under [keys]. */
private fun YamlMap.only(keys: Set<String>) = YamlMap(
    pairs.filterKeys { it in keys }.mapKeys { YamlScalar(it.key, path) },
    path,
)
