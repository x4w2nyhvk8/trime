/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

/**
 * One node of a keyboard layout tree (doc/Keyboard.md §2.6).
 *
 * A layout is a list of nodes stacked vertically at the root; a node is either
 * a key (a leaf) or a container that arranges its children:
 *
 * - [Row] places its children left to right, one physical line;
 * - [Column] stacks its children top to bottom, one line each;
 * - [Flow] places its children left to right and wraps when they no longer fit;
 * - [Collection] reserves a rectangle for a scrollable set of symbols read from
 *   the theme's `liquid_keyboard` categories.
 *
 * Every node carries the same placement attributes ([width], [height],
 * [heightPercent], [rowBreak]) whose meaning depends on the parent:
 *
 * - [width] is a percentage of the parent row's width in a horizontal parent;
 *   `-1` takes what the row leaves over, `0` uses the keyboard default width;
 * - [height] is an absolute height in dp inside a vertical parent; `-1` takes
 *   what the stack leaves over, `0` uses the node's intrinsic height;
 * - [heightPercent] is a percentage of the parent stack's height and wins over
 *   [height] when positive;
 * - [rowBreak] forces a new line before the node inside a [Flow].
 *
 * Containers nest freely; the depth is not limited.
 */
sealed class KeyboardNode {
    /** Percentage of the parent row's width; `-1` fills the rest, `0` is the default width. */
    abstract val width: Float

    /** Height inside a vertical parent, in dp; `-1` fills the rest, `0` is intrinsic. */
    abstract val height: Float

    /** Percentage of the parent stack's height; 0 means "not set". */
    abstract val heightPercent: Float

    /** Start a new line before this node when it sits inside a [Flow]. */
    abstract val rowBreak: Boolean

    /** A key: the only kind of node that produces a `Key` at runtime. */
    data class Key(
        val key: TextKeyboard.TextKey,
    ) : KeyboardNode() {
        override val width: Float get() = key.width
        override val height: Float get() = key.height
        override val heightPercent: Float get() = 0f
        override val rowBreak: Boolean get() = key.rowBreak
    }

    /** Children placed left to right on one line. */
    data class Row(
        val children: List<KeyboardNode> = emptyList(),
        override val width: Float = 0f,
        override val height: Float = 0f,
        override val heightPercent: Float = 0f,
        override val rowBreak: Boolean = false,
    ) : KeyboardNode()

    /** Children stacked top to bottom, one line each. */
    data class Column(
        val children: List<KeyboardNode> = emptyList(),
        override val width: Float = 0f,
        override val height: Float = 0f,
        override val heightPercent: Float = 0f,
        override val rowBreak: Boolean = false,
    ) : KeyboardNode()

    /** Children placed left to right, wrapping to a new line when they do not fit. */
    data class Flow(
        val children: List<KeyboardNode> = emptyList(),
        override val width: Float = 0f,
        override val height: Float = 0f,
        override val heightPercent: Float = 0f,
        override val rowBreak: Boolean = false,
    ) : KeyboardNode()

    /**
     * A scrollable set of symbols, embedded in the keyboard rather than shown in
     * the separate liquid keyboard window. Its data comes from one category of
     * the theme's `liquid_keyboard` section ([KeyboardCollection.source]).
     */
    data class Collection(
        val spec: KeyboardCollection = KeyboardCollection(),
        override val width: Float = 0f,
        override val height: Float = 0f,
        override val heightPercent: Float = 0f,
        override val rowBreak: Boolean = false,
    ) : KeyboardNode()
}

/** How a [KeyboardNode.Collection] scrolls through its cells. */
enum class ScrollDirection {
    VERTICAL,
    HORIZONTAL,
}

/**
 * The parameters of one embedded collection (doc/Keyboard.md §2.6):
 *
 * ```yaml
 * - collection:
 *     source: punct        # a `liquid_keyboard` category id
 *     width: 20            # percentage of the parent row
 *     scroll: vertical     # vertical | horizontal
 *     cell_height: 56      # dp
 *     cell_width: 48       # dp, optional: the square default of cell_height
 *     columns: 3           # cells per line while scrolling vertically (0 = fit)
 * ```
 */
data class KeyboardCollection(
    /** The `liquid_keyboard` category id whose keys are shown. */
    val source: String = "",
    val scroll: ScrollDirection = ScrollDirection.VERTICAL,
    /** Cell height in dp; 0 uses the keyboard's default row height. */
    val cellHeight: Float = 0f,
    /** Cell width in dp; 0 makes the cells square. */
    val cellWidth: Float = 0f,
    /** Cells per line while scrolling vertically; 0 fits as many as the width allows. */
    val columns: Int = 0,
)

/**
 * The decoded `keys:` of a keyboard: the container [tree] the layouter walks,
 * plus its [flat] view.
 *
 * [flat] is what the rest of the app has always read — the keys in document
 * order, each row's first entry marked with `row_break` — so it stays a plain
 * [List] of [TextKeyboard.TextKey] for the identity resolver, the diagnostics
 * and the shipped-theme tests. The tree carries the nesting that the flat list
 * cannot express.
 */
class TextKeys(
    val flat: List<TextKeyboard.TextKey>,
    val tree: List<KeyboardNode>,
) : List<TextKeyboard.TextKey> by flat {

    /**
     * The root nodes the layout engine starts from. A layout that was built
     * without a tree (programmatic fixtures, or a keyboard whose `keys:` never
     * went through the serializer) falls back to one wrapping flow, which is what
     * the pre-container list meant.
     */
    fun layoutRoots(): List<KeyboardNode> = when {
        tree.isNotEmpty() -> tree
        flat.isEmpty() -> emptyList()
        else -> listOf(KeyboardNode.Flow(children = flat.map { KeyboardNode.Key(it) }))
    }

    /** Applies [transform] to every leaf, keeping the tree and the flat view in sync. */
    fun mapLeaves(transform: (TextKeyboard.TextKey) -> TextKeyboard.TextKey): TextKeys {
        if (flat.isEmpty()) return this
        if (tree.isEmpty()) return TextKeys(flat.map(transform), emptyList())
        val mappedTree = tree.map { it.mapLeaves(transform) }
        return TextKeys(mappedTree.flattenKeys(), mappedTree)
    }

    override fun equals(other: Any?): Boolean = when (other) {
        is TextKeys -> flat == other.flat && tree == other.tree
        is List<*> -> flat == other
        else -> false
    }

    override fun hashCode(): Int = flat.hashCode()

    override fun toString(): String = flat.toString()

    companion object {
        val EMPTY = TextKeys(emptyList(), emptyList())

        /** Wraps a flat key list into the wrapping flow it used to be. */
        fun of(keys: List<TextKeyboard.TextKey>): TextKeys {
            if (keys.isEmpty()) return EMPTY
            val tree = listOf(KeyboardNode.Flow(children = keys.map { KeyboardNode.Key(it) }))
            return TextKeys(tree.flattenKeys(), tree)
        }
    }
}

/** Applies [transform] to every leaf of this node. */
internal fun KeyboardNode.mapLeaves(transform: (TextKeyboard.TextKey) -> TextKeyboard.TextKey): KeyboardNode = when (this) {
    is KeyboardNode.Key -> KeyboardNode.Key(transform(key))
    is KeyboardNode.Row -> copy(children = children.map { it.mapLeaves(transform) })
    is KeyboardNode.Column -> copy(children = children.map { it.mapLeaves(transform) })
    is KeyboardNode.Flow -> copy(children = children.map { it.mapLeaves(transform) })
    is KeyboardNode.Collection -> this
}

/**
 * The flat view of a layout tree: every leaf in document order, with the first
 * entry of each line flagged by `row_break`.
 *
 * Lines are read from the tree the way the engine draws them: a [KeyboardNode.Row]
 * is one line, a [KeyboardNode.Column] starts a line per child, a
 * [KeyboardNode.Flow] keeps its children together until one carries the flag,
 * and a [KeyboardNode.Collection] contributes no leaf.
 */
internal fun List<KeyboardNode>.flattenKeys(): List<TextKeyboard.TextKey> {
    val out = mutableListOf<TextKeyboard.TextKey>()
    forEach { it.flattenInto(out, startsLine = true) }
    return out
}

private fun KeyboardNode.flattenInto(
    out: MutableList<TextKeyboard.TextKey>,
    startsLine: Boolean,
) {
    when (this) {
        is KeyboardNode.Key -> out += key.copy(rowBreak = startsLine || key.rowBreak)

        is KeyboardNode.Row -> children.forEachIndexed { index, child ->
            child.flattenInto(out, startsLine && index == 0)
        }

        is KeyboardNode.Column -> children.forEach { it.flattenInto(out, startsLine = true) }

        is KeyboardNode.Flow -> children.forEachIndexed { index, child ->
            child.flattenInto(out, startsLine = (index == 0 && startsLine) || child.rowBreak)
        }

        is KeyboardNode.Collection -> Unit
    }
}
