/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.osfans.trime.util.pairs
import com.osfans.trime.util.scalar
import com.osfans.trime.util.untagged
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
 * One named set of cells an embedded collection can scroll through
 * (doc/Keyboard.md §2.6.3). A theme declares them in the top level
 * `collections:` map; a keyboard layout only references the id.
 *
 * Both spellings are accepted:
 *
 * ```yaml
 * collections:
 *   math: ["+", "−", "×"]          # a plain list
 *   punct:                          # or an object with its own metrics
 *     columns: 1
 *     cell_height: 56
 *     items: ["，", "。", "？"]
 * ```
 */
@kotlinx.serialization.Serializable(with = ThemeCollectionSerializer::class)
data class ThemeCollection(
    val items: List<String> = emptyList(),
    /** Cells per line of a vertical collection; 0 lets the view fit square cells. */
    val columns: Int = 0,
    /** Cell height in dp; 0 uses the keyboard's key height. */
    val cellHeight: Float = 0f,
    /** Cell width in dp; 0 uses the cell height (square cells). */
    val cellWidth: Float = 0f,
)

@OptIn(InternalSerializationApi::class)
internal object ThemeCollectionSerializer : KSerializer<ThemeCollection> {
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("com.osfans.trime.data.theme.model.ThemeCollection", SerialKind.CONTEXTUAL)

    override fun deserialize(decoder: Decoder): ThemeCollection = when (val node = decoder.yamlNode().untagged) {
        is YamlList -> ThemeCollection(items = node.items.mapNotNull { it.scalar?.content })

        is YamlMap ->
            ThemeCollection(
                items = (node.pairs["items"] as? YamlList)?.items?.mapNotNull { it.scalar?.content }.orEmpty(),
                columns = node.pairs["columns"]?.scalar?.content?.toIntOrNull() ?: 0,
                cellHeight = node.pairs["cell_height"]?.scalar?.content?.toFloatOrNull() ?: 0f,
                cellWidth = node.pairs["cell_width"]?.scalar?.content?.toFloatOrNull() ?: 0f,
            )

        else -> ThemeCollection()
    }

    override fun serialize(encoder: Encoder, value: ThemeCollection) = throw SerializationException("Theme configuration is read-only")
}
