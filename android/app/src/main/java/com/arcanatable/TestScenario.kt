package com.arcanatable

import com.arcanatable.engine.LocalSeat
import com.wingedsheep.engine.registry.CardRegistry
import org.json.JSONObject

/**
 * A debug-only test scenario: a ready board for checking one interaction on a device.
 * The scenarios themselves exist only in the debug source set (src/debug/.../TestScenarios.kt);
 * release builds compile an empty list (src/release), so neither the menu nor any board ships.
 */
data class TestScenario(val id: String, val title: String, val description: String)

/** What a scenario builds: the seats, the registry they need, and a resumable offline checkpoint. */
class TestScenarioGame(val seats: List<LocalSeat>, val registry: CardRegistry, val checkpoint: JSONObject)
