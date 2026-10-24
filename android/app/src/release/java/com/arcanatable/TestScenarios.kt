package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary

/** Release builds: no test scenarios, no scenario boards, no menu entry. */
internal object TestScenarios {
    val available: List<TestScenario> = emptyList()

    fun build(scenario: TestScenario, library: OfflineCardLibrary): TestScenarioGame =
        error("Test scenarios are not part of release builds.")
}
