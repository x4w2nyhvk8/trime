/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlNode
import com.osfans.trime.data.theme.ThemeYaml
import com.osfans.trime.util.boolean
import com.osfans.trime.util.float
import com.osfans.trime.util.int
import com.osfans.trime.util.isNull
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.sequence
import com.osfans.trime.util.string
import com.osfans.trime.util.yamlNode
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Decodes `keys:` into the container tree the layout engine walks
 * (doc/Keyboard.md §2.6):
 *
 * ```yaml
 * keys:                        # the root stack, top to bottom
 *   - row: [ {key: qButton, width: 10}, {key: wButton, width: 10} ]
 *   - flow: [ {key: xButton, width: 20}, {key: yButton, width: 20} ]
 *   - column: [ {key: aButton, width: 100}, {key: bButton, width: 100} ]
 *   - collection: {source: punct, width: 20, scroll: vertical, cell_height: 56}
 * ```
 *
 * A container is a mapping whose first key names it (`row`, `flow`, `column`,
 * `collection`); placement attributes sit beside that key, so a container may
 * carry `width`, `height`, `height_percent` and `row_break` next to its list:
 *
 * ```yaml
 * - row:
 *     - {key: shiftButton, width: 15}
 *     - column:                       # a column inside the row
 *         - {key: zButton, width: 100}
 *         - {key: xButton, width: 100}
 *       width: 20
 * ```
 *
 * The flat view ([TextKeys.flat]) is derived from the tree by
 * [flattenKeys]; every consumer that only needs the keys keeps reading it as a
 * list. A root list that is not made of containers at all is the pre-container
 * form of `keys:` and is read as one wrapping [KeyboardNode.Flow], so themes
 * written before §2.6 keep their keys.
 */
@OptIn(InternalSerializationApi::class)
internal object TextKeysSerializer : KSerializer<TextKeys> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("com.osfans.trime.data.theme.model.TextKeys", SerialKind.CONTEXTUAL)

    private const val KEY_ROW = "row"
    private const val KEY_FLOW = "flow"
    private const val KEY_COLUMN = "column"
    private const val KEY_COLLECTION = "collection"

    override fun deserialize(decoder: Decoder): TextKeys {
        val root = decoder.yamlNode().sequence ?: return TextKeys.EMPTY
        val nodes = root.items.map { parseNode(it) }
        if (nodes.isEmpty()) return TextKeys.EMPTY

        // `keys: [{click: a}, …]` — the pre-container form. It wrapped by width,
        // which is exactly what a flow does, so it keeps its layout.
        val tree = if (nodes.all { it is KeyboardNode.Key }) {
            listOf(KeyboardNode.Flow(children = nodes))
        } else {
            nodes
        }
        return TextKeys(tree.flattenKeys(), tree)
    }

    /** One entry of a `keys:` list: a container or a key. */
    private fun parseNode(node: YamlNode): KeyboardNode {
        val map = node.mapping ?: throw SerializationException(
            "keys 的条目必须是按键或容器（row / column / flow / collection），收到：$node",
        )
        val pairs = map.pairs
        return when {
            KEY_ROW in pairs -> KeyboardNode.Row(
                children = childrenOf(pairs[KEY_ROW], KEY_ROW),
                width = pairs.floatOrZero("width"),
                height = pairs.floatOrZero("height"),
                heightPercent = pairs.floatOrZero("height_percent"),
                rowBreak = pairs.booleanOrFalse("row_break"),
            )

            KEY_COLUMN in pairs -> KeyboardNode.Column(
                children = childrenOf(pairs[KEY_COLUMN], KEY_COLUMN),
                width = pairs.floatOrZero("width"),
                height = pairs.floatOrZero("height"),
                heightPercent = pairs.floatOrZero("height_percent"),
                rowBreak = pairs.booleanOrFalse("row_break"),
            )

            KEY_FLOW in pairs -> KeyboardNode.Flow(
                children = childrenOf(pairs[KEY_FLOW], KEY_FLOW),
                width = pairs.floatOrZero("width"),
                height = pairs.floatOrZero("height"),
                heightPercent = pairs.floatOrZero("height_percent"),
                rowBreak = pairs.booleanOrFalse("row_break"),
            )

            KEY_COLLECTION in pairs -> parseCollection(pairs[KEY_COLLECTION], pairs)

            else -> KeyboardNode.Key(decodeKey(node))
        }
    }

    /** The children of a `row:` / `flow:` / `column:`: containers or keys, any depth. */
    private fun childrenOf(
        node: YamlNode?,
        kind: String,
    ): List<KeyboardNode> {
        if (node == null || node.isNull) return emptyList()
        val list = node.sequence
            ?: throw SerializationException("'$kind' 必须是容器/按键的列表，收到：$node")
        return list.items.map { parseNode(it) }
    }

    /**
     * A collection: its parameters live inside the `collection:` mapping (the
     * natural home for source/scroll/cell sizes), with the placement attributes
     * read from there first and from the container itself as a fallback.
     * `- collection: punct` is accepted as a shorthand for the source alone.
     */
    private fun parseCollection(
        node: YamlNode?,
        parent: Map<String, YamlNode>,
    ): KeyboardNode.Collection {
        val fields = node?.mapping?.pairs.orEmpty()

        fun field(name: String): YamlNode? = fields[name] ?: parent[name]

        val source = node?.string ?: field("source")?.string.orEmpty()
        val scroll = when (field("scroll")?.string?.lowercase()) {
            "horizontal" -> ScrollDirection.HORIZONTAL
            else -> ScrollDirection.VERTICAL
        }
        return KeyboardNode.Collection(
            spec = KeyboardCollection(
                source = source,
                scroll = scroll,
                cellHeight = field("cell_height")?.float ?: 0f,
                cellWidth = field("cell_width")?.float ?: 0f,
                columns = field("columns")?.int ?: 0,
            ),
            width = field("width")?.float ?: 0f,
            height = field("height")?.float ?: 0f,
            heightPercent = field("height_percent")?.float ?: 0f,
            rowBreak = field("row_break")?.boolean ?: false,
        )
    }

    private fun decodeKey(node: YamlNode): TextKeyboard.TextKey = ThemeYaml.parser.decodeFromYamlNode(TextKeyboard.TextKey.serializer(), node)

    override fun serialize(
        encoder: Encoder,
        value: TextKeys,
    ) = throw SerializationException("Theme configuration is read-only")
}

private fun Map<String, YamlNode>.floatOrZero(name: String): Float = this[name]?.float ?: 0f

private fun Map<String, YamlNode>.booleanOrFalse(name: String): Boolean = this[name]?.boolean ?: false
