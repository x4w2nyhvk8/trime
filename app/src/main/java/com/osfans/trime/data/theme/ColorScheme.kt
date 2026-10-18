/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

/**
 * One flat color table: color key -> color value or image name. It is the
 * content of a theme package's `day.yaml` / `night.yaml`.
 */
typealias ColorScheme = Map<String, String>
