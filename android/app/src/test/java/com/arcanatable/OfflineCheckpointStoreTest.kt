package com.arcanatable

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.arcanatable.engine.LocalSeat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineCheckpointStoreTest {
    @Test fun privateSnapshotRoundTripIncludesUnmaskedStateAndOriginalDecks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OfflineCheckpointStore.clear(context)
        val you = LocalSeat("You", listOf("Plains", "Forest"), "Commander")
        val ai = LocalSeat("AI", listOf("Island"), null)
        val input = JSONObject().put("schema", 1).put("mode", "local-ai")
            .put("seats", JSONArray().put(OfflineCheckpointStore.seatToJson(you))
                .put(OfflineCheckpointStore.seatToJson(ai)))
            .put("gameState", JSONObject().put("privateOpponentHand", JSONArray().put("Hidden Card"))
                .put("stack", JSONArray().put("spell-id")))
        try {
            assertTrue(OfflineCheckpointStore.save(context, input.toString()))
            assertTrue(OfflineCheckpointStore.available(context))
            val saved = requireNotNull(OfflineCheckpointStore.load(context))
            assertEquals(listOf(you, ai), OfflineCheckpointStore.savedSeats(saved))
            assertEquals("Hidden Card", saved.getJSONObject("gameState")
                .getJSONArray("privateOpponentHand").getString(0))
        } finally { OfflineCheckpointStore.clear(context) }
        assertNull(OfflineCheckpointStore.load(context))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMaskedOrIncompleteSnapshots() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OfflineCheckpointStore.save(context, JSONObject().put("schema", 1)
            .put("mode", "local-ai").put("seats", JSONArray()).toString())
    }
}
