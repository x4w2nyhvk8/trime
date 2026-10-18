/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlScalar
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
 * Reads a style node's `content` from its YAML spelling.
 *
 * The contract spells the values snake_case (`system_icon`), which is not how
 * the Kotlin enum entry is named, and the theme parser's case-insensitive enum
 * decoding does not fold underscores — no other theme enum has an underscore in
 * its YAML value, so that has never had to work. The scalar is therefore matched
 * explicitly, through [KeyStyle.Content.of], which accepts both spellings. A
 * value that names no entry stays a parse error, like every other malformed enum
 * value in a theme.
 */
@OptIn(InternalSerializationApi::class)
object KeyStyleContentSerializer : KSerializer<KeyStyle.Content> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("KeyStyleContent", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: KeyStyle.Content): Unit = throw UnsupportedOperationException("A style content is only read from theme files")

    override fun deserialize(decoder: Decoder): KeyStyle.Content {
        val raw = (decoder.yamlNode() as? YamlScalar)?.content
            ?: throw SerializationException("Expect a scalar for 'content'")
        return KeyStyle.Content.of(raw)
            ?: throw SerializationException("Unknown style content '$raw'")
    }
}
