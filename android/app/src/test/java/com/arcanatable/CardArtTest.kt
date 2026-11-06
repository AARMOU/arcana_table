package com.arcanatable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class CardArtTest {
    @Test fun loadedArtIsLabelledWithTheCardName() {
        // Found on a PC where Scryfall art loads: the art replaced the name text and was labelled
        // only "Magic card art", so screen readers (and tests) could no longer tell cards apart.
        assertEquals("Rampaging Baloths", cardArtDescription(label = null, fallbackName = "Rampaging Baloths"))
        assertEquals("Bello, Bard of the Brambles", cardArtDescription(label = "Bello, Bard of the Brambles", fallbackName = null))
        assertEquals("explicit label wins", "Bello", cardArtDescription(label = "Bello", fallbackName = "Other"))
        assertEquals("never blank", "Magic card art", cardArtDescription(label = " ", fallbackName = null))
        assertEquals("Magic card art", cardArtDescription(label = null, fallbackName = null))
    }

    @Test fun failedImageRetriesAndRecoversWithoutRecreatingTheCard() = runBlocking {
        var attempts = 0
        val pauses = mutableListOf<Long>()
        val result = loadCardArtWithRetry("https://cards.scryfall.io/normal/abc.jpg",
            pause = { pauses.add(it) }) {
            attempts++
            if (attempts == 2) CardArtLoadResult.Loaded("decoded art") else CardArtLoadResult.RetryableFailure
        }
        assertEquals("decoded art", result)
        assertEquals(2, attempts)
        assertEquals(1, pauses.size)
        assertEquals(true, pauses.single() in 2_000L..3_023L)
    }

    @Test fun failedImageHasFiniteAttemptBudgetAndRetainsPlaceholder() = runBlocking {
        var attempts = 0
        val pauses = mutableListOf<Long>()
        val result = loadCardArtWithRetry<String>("https://cards.scryfall.io/normal/missing.jpg",
            pause = { pauses.add(it) }) {
            attempts++
            CardArtLoadResult.RetryableFailure
        }
        assertNull(result)
        assertEquals(3, attempts)
        assertEquals(2, pauses.size)
        assertEquals(true, pauses[0] in 2_000L..3_023L)
        assertEquals(true, pauses[1] in 8_000L..9_023L)
    }

    @Test fun imageAlreadyAvailableIsNotRetried() = runBlocking {
        var attempts = 0
        val result = loadCardArtWithRetry("https://cards.scryfall.io/normal/ready.jpg",
            pause = { fail("successful image must not wait") }) {
            attempts++
            CardArtLoadResult.Loaded("ready")
        }
        assertEquals("ready", result)
        assertEquals(1, attempts)
    }

    @Test fun cancellationIsNeverConvertedIntoAnotherNetworkAttempt() = runBlocking {
        var attempts = 0
        try {
            loadCardArtWithRetry<String>("https://cards.scryfall.io/normal/cancel.jpg",
                pause = { fail("cancelled image must not retry") }) {
                attempts++
                throw CancellationException("off-screen or changed card")
            }
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, attempts)
        }
    }
    @Test fun permanentLoadFailureStopsImmediately() = runBlocking {
        var attempts = 0
        val result = loadCardArtWithRetry<String>(
            "https://cards.scryfall.io/normal/not-found.jpg",
            pause = { fail("permanent failure must not wait") }
        ) {
            attempts++
            CardArtLoadResult.PermanentFailure
        }
        assertNull(result)
        assertEquals(1, attempts)
    }

    @Test fun permanentHttpFailuresDoNotTriggerOuterRetryButTransientStatusesDo() {
        assertEquals(false, isRetryableCardArtStatusCode(404))
        assertEquals(false, isRetryableCardArtStatusCode(429))
        assertEquals(true, isRetryableCardArtStatusCode(408))
        assertEquals(true, isRetryableCardArtStatusCode(503))
    }

    @Test fun cancellationDuringRetryPauseStopsFurtherLoads() = runBlocking {
        var attempts = 0
        val pauseEntered = CompletableDeferred<Unit>()
        val pauseForever = CompletableDeferred<Unit>()
        val job = launch {
            loadCardArtWithRetry<String>(
                "https://cards.scryfall.io/normal/changed.jpg",
                pause = { pauseEntered.complete(Unit); pauseForever.await() }
            ) {
                attempts++
                CardArtLoadResult.RetryableFailure
            }
        }
        pauseEntered.await()
        job.cancel()
        job.join()
        assertEquals(1, attempts)
    }

}
