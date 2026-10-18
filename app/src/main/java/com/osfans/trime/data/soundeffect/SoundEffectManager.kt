/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.soundeffect

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.util.FileUtils
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import java.io.File

object SoundEffectManager {

    private val userDir: File
        get() {
            val dest = File(DataManager.userDataDir, "soundeffect")
            val old = File(DataManager.userDataDir, "sound")
            return FileUtils.rename(old, dest.name).getOrDefault(dest.also { it.mkdirs() })
        }

    /**
     * Descriptors are standalone files with their own key spelling (`inOrder`,
     * `keyset`), so unlike [com.osfans.trime.data.theme.ThemeYaml] no naming
     * strategy applies and unknown keys are ignored.
     */
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
        ),
    )

    /** A theme may ship its own sounds under `<package>/soundeffect/`; those shadow the global ones by name. */
    private val packageDir: File?
        get() = ColorManager.currentScope()?.packageDir?.let { File(it, "soundeffect") }

    /**
     * Where each descriptor was read from, so a theme's own sound files resolve
     * inside its package while the global drop keeps working (doc/Theme.md §3).
     */
    private val descriptorDirs = mutableMapOf<String, File>()

    private fun readDescriptors(dir: File?): List<Pair<SoundEffect, File>> {
        if (dir == null || !dir.isDirectory) return emptyList()
        val files = dir.listFiles { f -> f.name.endsWith("sound.yaml") } ?: return emptyList()
        return files.mapNotNull decode@{ f ->
            try {
                val result = yaml.decodeFromString<SoundEffect>(f.bufferedReader().readText())
                val effect = if (result.name.isEmpty()) result.copy(name = f.name.substringBefore(".")) else result
                effect to dir
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode sound effect descriptor '${f.absolutePath}'")
                null
            }
        }
    }

    private fun listSounds(): MutableList<SoundEffect> {
        val fromPackage = readDescriptors(packageDir)
        val shadowed = fromPackage.mapTo(mutableSetOf()) { it.first.name }
        val fromGlobal = readDescriptors(userDir).filterNot { it.first.name in shadowed }
        val all = fromPackage + fromGlobal
        descriptorDirs.clear()
        all.forEach { (effect, dir) -> descriptorDirs[effect.name] = dir }
        return all.map { it.first }.toMutableList()
    }

    private fun getEffect(name: String) = userEffects.find { it.name == name }

    private val userEffects: MutableList<SoundEffect> get() = listSounds()

    private var soundEffectPref by AppPrefs.defaultInstance().keyboard.customSoundEffect

    fun switchEffect(name: String) {
        val effect = getEffect(name)
        if (effect == null) {
            Timber.w("Unknown sound effect '$name'")
            return
        }
        activeSoundEffect = effect
        soundEffectPref = name
        InputFeedbackManager.reloadSoundEffects()
    }

    fun init() {
        activeSoundEffect = getEffect(soundEffectPref) ?: return
    }

    var activeSoundEffect: SoundEffect? = null
        private set

    val activeAudioPaths: List<String>
        get() {
            return activeSoundEffect?.let { e ->
                val base = descriptorDirs[e.name] ?: userDir
                e.sound.map { base.resolve(e.folder).resolve(it).path }
            } ?: listOf()
        }

    fun getAllSoundEffects(): List<SoundEffect> = userEffects
}
