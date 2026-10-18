/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.model.KeyboardCollection
import com.osfans.trime.data.theme.model.KeyboardNode
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.orAbsent
import kotlin.math.abs
import kotlin.math.max

/**
 * The layout math of one keyboard, in pixels, with no Android dependency
 * (doc/Keyboard.md §2.6). [Keyboard] builds `Key` objects from the [Result] and
 * the tests run this object directly.
 *
 * The input is the container tree the theme declared
 * ([com.osfans.trime.data.theme.model.TextKeys.tree]). At the root the nodes are
 * stacked top to bottom into [Config.height]; a container then arranges its
 * children the same way its own parent did:
 *
 * - a [KeyboardNode.Row] places children left to right on one line (no wrapping);
 * - a [KeyboardNode.Column] stacks children top to bottom, one line each;
 * - a [KeyboardNode.Flow] places children left to right and wraps on overflow;
 * - a [KeyboardNode.Collection] gets its rectangle and no keys;
 * - a [KeyboardNode.Key] is a leaf.
 *
 * Widths inside a horizontal parent are percentages of that parent's width:
 * `-1` takes what the siblings leave over, `0` is the keyboard default width
 * ([Config.defaultKeyWidth]). Heights inside a vertical parent are proportional:
 * `height_percent` is a percentage of the parent height, a positive `height` is
 * dp, `-1` takes the rest and `0` is the node's intrinsic height. A vertical
 * parent always stretches its children to its own height, so heights behave as
 * weights unless a fill key absorbs the remainder.
 */
object KeyboardLayoutEngine {
    /** The metrics a layout is computed with, all in pixels except the widths. */
    data class Config(
        /** Usable keyboard width. */
        val width: Int,
        /** Keyboard height the root stack is stretched to. */
        val height: Int,
        /** Default key width, as a percentage of the row (`preset_keyboards.<id>.width`). */
        val defaultKeyWidth: Float = 0f,
        /** Default row height in pixels (`style/key_height`). */
        val defaultRowHeight: Int = 0,
        /** Maximum number of clickable keys per flow line. */
        val maxColumns: Int = Int.MAX_VALUE,
        /** Split-keyboard gap ratio, 0 for a single keyboard (`landscape_split_percent` / 100). */
        val splitRatio: Float = 0f,
    ) {
        val isSplit: Boolean get() = splitRatio > 0f
    }

    /** One key with its final rectangle. */
    data class PlacedKey(
        val key: TextKeyboard.TextKey,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val row: Int,
        val column: Int,
    )

    /** One embedded collection with its final rectangle. */
    data class PlacedCollection(
        val spec: KeyboardCollection,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * A rectangle something occupied without becoming a key: a decorative entry
     * that carries no action, or the split keyboard's middle gap. `Keyboard`
     * turns these into "expand keypress area" spacers.
     */
    data class Placeholder(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val row: Int,
    )

    data class Result(
        val keys: List<PlacedKey>,
        val collections: List<PlacedCollection>,
        val placeholders: List<Placeholder>,
        /** Right edge the content reaches, the keyboard's minimum width. */
        val width: Int,
        /** Total height; the root stack always fills [Config.height]. */
        val height: Int,
        /** Last line index that holds a key, -1 when there is none. */
        val lastRow: Int,
    )

    /** Number of cells a collection shows when the theme gives it no height. */
    private const val AUTO_COLLECTION_ROWS = 3

    /** A key wider than this weight keeps its width and grows by the split gap. */
    private const val WIDE_KEY_WEIGHT = 20f

    private const val MAX_WEIGHT = 100f

    fun layout(
        nodes: List<KeyboardNode>,
        config: Config,
    ): Result {
        val sink = Sink()
        placeColumn(nodes, Frame(0, 0, config.width, config.height), config, sink, 0)
        return Result(
            keys = sink.keys,
            collections = sink.collections,
            placeholders = sink.placeholders,
            width = sink.right,
            height = config.height,
            lastRow = sink.keys.maxOfOrNull { it.row } ?: -1,
        )
    }

    /** Mutable collectors, so the recursion does not thread a return value. */
    private class Sink {
        val keys = mutableListOf<PlacedKey>()
        val collections = mutableListOf<PlacedCollection>()
        val placeholders = mutableListOf<Placeholder>()
        var right = 0
        private var line = -1

        fun newLine(): Int {
            line++
            return line
        }
    }

    private data class Frame(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * A vertical stack: resolve every child's height, then place it. The stack
     * fills [frame]'s height, so `height_percent` and dp heights are weights
     * relative to their siblings unless a `-1` child takes the remainder.
     */
    private fun placeColumn(
        nodes: List<KeyboardNode>,
        frame: Frame,
        config: Config,
        sink: Sink,
        depth: Int,
    ) {
        if (nodes.isEmpty()) return
        val heights = resolveHeights(nodes, frame, config)
        var y = frame.y
        nodes.forEachIndexed { index, node ->
            val height = heights[index]
            // A nested column stacks its own lines, so it does not consume one.
            val line = if (node is KeyboardNode.Column) -1 else sink.newLine()
            placeNode(node, Frame(frame.x, y, frame.width, height), config, sink, depth, line)
            y += height
        }
    }

    /** Places one node into [frame]; [line] is the line index its keys report. */
    private fun placeNode(
        node: KeyboardNode,
        frame: Frame,
        config: Config,
        sink: Sink,
        depth: Int,
        line: Int,
    ) {
        sink.right = max(sink.right, frame.x + frame.width)
        when (node) {
            is KeyboardNode.Key -> {
                if (node.isClickable()) {
                    sink.keys += PlacedKey(node.key, frame.x, frame.y, frame.width, frame.height, line, 0)
                } else {
                    sink.placeholders += Placeholder(frame.x, frame.y, frame.width, frame.height, line)
                }
            }

            is KeyboardNode.Row -> placeRow(node.children, frame, config, sink, depth, line)

            is KeyboardNode.Column -> placeColumn(node.children, frame, config, sink, depth + 1)

            is KeyboardNode.Flow -> placeFlow(node.children, frame, config, sink, depth)

            is KeyboardNode.Collection -> {
                sink.collections += PlacedCollection(node.spec, frame.x, frame.y, frame.width, frame.height)
            }
        }
    }

    /** A horizontal line: widths are percentages of [frame]'s width. */
    private fun placeRow(
        children: List<KeyboardNode>,
        frame: Frame,
        config: Config,
        sink: Sink,
        depth: Int,
        line: Int,
    ) {
        if (children.isEmpty()) return
        val unit = frame.width.toDouble() / (MAX_WEIGHT * (1.0 + config.splitRatio))
        val weights = FloatArray(children.size)
        var fixedTotal = 0f
        var fills = 0
        children.forEachIndexed { index, child ->
            when {
                child.width < 0f -> fills++

                else -> {
                    val weight = weightOf(child, config)
                    weights[index] = weight
                    fixedTotal += weight
                }
            }
        }
        val fillWeight = if (fills > 0) max(0f, MAX_WEIGHT - fixedTotal) / fills else 0f

        val split = depth == 0 && config.isSplit
        var x = 0
        var accumulated = 0f
        var splitInserted = false
        var column = 0

        children.forEachIndexed { index, child ->
            val weight = if (child.width < 0f) fillWeight else weights[index]
            var width = (weight * unit).toInt().coerceAtLeast(0)
            accumulated += weight

            // The split keyboard puts its gap at the row's middle: a wide key
            // grows by it, a narrow one shifts right of it (doc/Keyboard.md §5).
            if (split && !splitInserted && fixedTotal > 0f && accumulated > fixedTotal * 0.5f) {
                splitInserted = true
                val gap = (fixedTotal * config.splitRatio * unit).toInt().coerceAtLeast(0)
                if (gap > 0) {
                    if (weight > WIDE_KEY_WEIGHT) {
                        width += gap
                    } else {
                        sink.placeholders += Placeholder(frame.x + x, frame.y, gap, frame.height, line)
                        x += gap
                    }
                }
            }

            if (child is KeyboardNode.Key && child.isClickable()) {
                // Snap the row's last key to the right edge, correcting the
                // rounding error of summing truncated percentages.
                val rightGap = abs(frame.width - x - width)
                if (rightGap <= frame.width / 100) {
                    width = (frame.width - x).coerceAtLeast(0)
                }
                sink.keys +=
                    PlacedKey(child.key, frame.x + x, frame.y, width, frame.height, line, column)
                column++
            } else if (child is KeyboardNode.Key) {
                sink.placeholders += Placeholder(frame.x + x, frame.y, width, frame.height, line)
            } else {
                placeNode(child, Frame(frame.x + x, frame.y, width, frame.height), config, sink, depth + 1, line)
            }
            x += width
        }
    }

    /** A wrapping line container: split the children into lines, then place each like a row. */
    private fun placeFlow(
        children: List<KeyboardNode>,
        frame: Frame,
        config: Config,
        sink: Sink,
        depth: Int,
    ) {
        val lines = splitFlowLines(children, frame.width, config)
        if (lines.isEmpty()) return
        val lineHeight = frame.height / lines.size
        var y = frame.y
        lines.forEachIndexed { index, lineChildren ->
            val height = if (index == lines.size - 1) frame.y + frame.height - y else lineHeight
            placeRow(lineChildren, Frame(frame.x, y, frame.width, height), config, sink, depth, sink.newLine())
            y += height
        }
    }

    /** Breaks flow children into lines by width and by [Config.maxColumns] clickable keys. */
    private fun splitFlowLines(
        children: List<KeyboardNode>,
        width: Int,
        config: Config,
    ): List<List<KeyboardNode>> {
        if (children.isEmpty()) return emptyList()
        val unit = width.toDouble() / (MAX_WEIGHT * (1.0 + config.splitRatio))
        val lines = mutableListOf<List<KeyboardNode>>()
        var current = mutableListOf<KeyboardNode>()
        var used = 0.0
        var columns = 0
        children.forEach { child ->
            val px = (weightOf(child, config) * unit).toInt()
            val forced = child.rowBreak && current.isNotEmpty()
            val overflow = current.isNotEmpty() && used + px > width
            val limit = current.isNotEmpty() && columns >= config.maxColumns
            if (forced || overflow || limit) {
                lines += current
                current = mutableListOf()
                used = 0.0
                columns = 0
            }
            current += child
            used += px
            if (child is KeyboardNode.Key && child.isClickable()) columns++
        }
        lines += current
        return lines
    }

    /** Resolves the heights of a vertical stack, in pixels, filling [frame]'s height. */
    private fun resolveHeights(
        nodes: List<KeyboardNode>,
        frame: Frame,
        config: Config,
    ): IntArray {
        val heights = IntArray(nodes.size)
        val raw = DoubleArray(nodes.size)
        var fixedSum = 0.0
        var fills = 0
        nodes.forEachIndexed { index, node ->
            val height = when {
                node.heightPercent > 0f -> frame.height * node.heightPercent / 100.0

                node.height > 0f -> node.height.toDouble()

                node.height < 0f -> {
                    fills++
                    -1.0
                }

                else -> intrinsicHeight(node, frame.width, config).toDouble()
            }
            raw[index] = height
            if (height >= 0.0) fixedSum += height
        }
        if (fixedSum <= 0.0) {
            val each = frame.height / nodes.size
            nodes.indices.forEach { heights[it] = each }
            heights[nodes.size - 1] = frame.height - each * (nodes.size - 1)
            return heights
        }

        val target = if (fills > 0) {
            val share = max(0.0, frame.height - fixedSum) / fills
            DoubleArray(nodes.size) { index -> if (raw[index] < 0.0) share else raw[index] }
        } else {
            val scale = frame.height / fixedSum
            DoubleArray(nodes.size) { index -> raw[index] * scale }
        }

        var used = 0
        for (index in 0 until nodes.size - 1) {
            heights[index] = target[index].toInt().coerceAtLeast(0)
            used += heights[index]
        }
        heights[nodes.size - 1] = max(0, frame.height - used)
        return heights
    }

    /** The height a node asks for when it declares none. */
    private fun intrinsicHeight(
        node: KeyboardNode,
        width: Int,
        config: Config,
    ): Int = when (node) {
        is KeyboardNode.Key -> if (node.key.height > 0f) node.key.height.toInt() else config.defaultRowHeight

        is KeyboardNode.Row -> node.children.maxOfOrNull { intrinsicHeight(it, width, config) } ?: config.defaultRowHeight

        is KeyboardNode.Column -> node.children.sumOf { intrinsicHeight(it, width, config) }

        is KeyboardNode.Flow -> max(1, splitFlowLines(node.children, width, config).size) * config.defaultRowHeight

        is KeyboardNode.Collection -> {
            val cell = if (node.spec.cellHeight > 0f) node.spec.cellHeight.toInt() else config.defaultRowHeight
            cell * AUTO_COLLECTION_ROWS
        }
    }

    /**
     * The width weight of a node in a horizontal parent. A fill key (`-1`)
     * reserves nothing, which is also how it is skipped while a flow wraps.
     */
    private fun weightOf(
        node: KeyboardNode,
        config: Config,
    ): Float = when {
        node.width > 0f -> node.width
        node.width < 0f -> 0f
        node is KeyboardNode.Key -> if (node.isClickable()) config.defaultKeyWidth else 0f
        else -> config.defaultKeyWidth
    }

    private fun KeyboardNode.Key.isClickable(): Boolean = key.click.orAbsent != null
}
