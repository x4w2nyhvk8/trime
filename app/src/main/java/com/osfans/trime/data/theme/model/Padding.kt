/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlScalar
import com.osfans.trime.util.int
import com.osfans.trime.util.isNull
import com.osfans.trime.util.untagged
import com.osfans.trime.util.yamlNode
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A panel or list item's padding, as doc/Theme.md §8.2 spells it: one number for
 * every side, or `[上下, 左右]` for the two axes.
 *
 * [UNSET] (-1) means "not declared", so the resolver applies its built-in
 * default; an explicit `0` is a real zero.
 */
@Serializable(with = PaddingSerializer::class)
data class Padding(
    val vertical: Int = UNSET,
    val horizontal: Int = UNSET,
) {
    /** Whether the theme declared a padding at all. */
    val isSet: Boolean
        get() = vertical >= 0 || horizontal >= 0

    companion object {
        /** No padding was declared; the resolver's built-in default applies. */
        const val UNSET = -1
    }
}

/**
 * Reads the two spellings of doc/Theme.md §8.2: a number means every side, a
 * two-element list means `[上下, 左右]`, an absent value stays "unset" and
 * anything else is a parse error like every other malformed theme value.
 */
@OptIn(InternalSerializationApi::class)
object PaddingSerializer : KSerializer<Padding> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Padding", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Padding): Unit = throw UnsupportedOperationException("A padding is only read from theme files")

    override fun deserialize(decoder: Decoder): Padding = when (val node = decoder.yamlNode().untagged) {
        is YamlNull -> Padding()

        is YamlScalar -> {
            if (node.isNull || node.content.isEmpty()) {
                Padding()
            } else {
                node.int?.let { Padding(it, it) } ?: invalid(node.content)
            }
        }

        is YamlList -> {
            val values = node.items
            if (values.size != 2) invalid(node.toString())
            Padding(values[0].int ?: invalid(values[0].toString()), values[1].int ?: invalid(values[1].toString()))
        }

        else -> invalid(node.toString())
    }

    private fun invalid(value: String): Nothing = throw SerializationException("Expect a number or [vertical, horizontal] for 'padding', got '$value'")
}
