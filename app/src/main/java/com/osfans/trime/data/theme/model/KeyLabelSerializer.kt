/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
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
 * Reads the polymorphic `label` of doc/Keyboard.md §2.2:
 *
 * ```yaml
 * label: 空格                          # a scalar is a static text
 * label: {text: 空格}                  # the explicit static form
 * label: {source: schemaName}          # dynamic, resolved when drawn
 * label: {source: custom, text: "…"}   # a fixed template
 * label:                               # no value, like a missing key
 * ```
 *
 * A mapping that names neither a source nor a text decodes to
 * [KeyLabel.None], and a `source` that names nothing stays a parse error, like
 * every other malformed enum value in a theme.
 */
@OptIn(InternalSerializationApi::class)
object KeyLabelSerializer : KSerializer<KeyLabel> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("KeyLabel", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: KeyLabel): Unit = throw UnsupportedOperationException("A key label is only read from theme files")

    override fun deserialize(decoder: Decoder): KeyLabel = when (val node = decoder.yamlNode().untagged) {
        is YamlNull -> KeyLabel.None

        is YamlScalar -> node.content.takeIf { it.isNotEmpty() }?.let { KeyLabel.Text(it) } ?: KeyLabel.None

        is YamlMap -> {
            val entries = node.pairs
            val text = entries["text"]?.string.orEmpty()
            val source = entries["source"]?.string
            if (source.isNullOrBlank()) {
                if (text.isEmpty()) KeyLabel.None else KeyLabel.Text(text)
            } else {
                KeyLabel.Dynamic(
                    source =
                    KeyLabel.Dynamic.Source.of(source)
                        ?: throw SerializationException("Unknown label source '$source'"),
                    template = text,
                )
            }
        }

        else -> throw SerializationException("Expect a text, a map or no value for 'label'")
    }
}
