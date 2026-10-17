package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Argentum's engine and AI play a full game inside Arcana's own build, on card data alone. */
class EmbeddedEngineTrialTest {
    private val cards = File("src/main/assets/argentum/por-cards.jsonl")

    @Test fun portalCardDataLoadsIntoArgentum() {
        val loaded = cards.bufferedReader().useLines { EmbeddedEngineTrial.loadCards(it) }
        assertEquals(cards.readLines().count { it.isNotBlank() }, loaded.size)
        assertTrue(loaded.any { it.name == "Forest" })
    }

    @Test fun aiPlaysAWholeGameInProcess() {
        var result: EmbeddedEngineTrial.Result? = null
        val worker = Thread(null, { result = EmbeddedEngineTrial.run({ cards.inputStream() }) }, "trial", 256L shl 20)
        worker.start(); worker.join()
        val r = result!!
        println("[engine-trial] ${r.summary()}")
        assertNull(r.error)
        assertTrue("game made progress", r.turns >= 4 && r.actions > 20)
    }
}
