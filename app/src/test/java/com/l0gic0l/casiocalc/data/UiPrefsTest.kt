package com.l0gic0l.casiocalc.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiPrefsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    /** Before the split, one Particles switch covered both; switching it off must still mean no sparkles. */
    @Test fun answerSparklesFollowTheOldParticlesSwitch() {
        val old = json.decodeFromString<UiPrefs>("""{"particles":false}""")
        assertFalse(old.showHomeParticles)
        assertFalse(old.showResultParticles)
        assertTrue(UiPrefs().showResultParticles)
        // Once set, the two are independent, and survive a save.
        val split = json.decodeFromString<UiPrefs>(json.encodeToString(UiPrefs.serializer(), old.copy(resultParticles = true)))
        assertFalse(split.showHomeParticles)
        assertTrue(split.showResultParticles)
        assertFalse(split.copy(reduceMotion = true).showResultParticles)
    }
}
