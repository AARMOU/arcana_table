package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.TriggeredAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Focused reproducer for the same-event buffered-slot crash in TriggerDetector.
 *
 * This test file is staged in Arcana investigation/ and copied into a temporary
 * Argentum checkout in CI, never into the prebuilt production Android JAR.
 */
class SimultaneousDamageFoldBufferTest : FunSpec({
    val registry = CardRegistry()
    val services = EngineServices(registry)
    val detector = TriggerDetector(
        registry,
        predicateEvaluator = services.predicateEvaluator,
        conditionEvaluator = services.conditionEvaluator
    )

    // The fold is a private implementation detail; keep it private in production.
    // Reflection exercises its exact implementation and preserves the public API.
    @Suppress("UNCHECKED_CAST")
    fun fold(
        event: DamageDealtEvent,
        detected: List<PendingTrigger>,
        triggers: MutableList<PendingTrigger>,
        slots: MutableMap<List<Any?>, Int>
    ): List<PendingTrigger> {
        val method = TriggerDetector::class.java.getDeclaredMethod(
            "foldSimultaneousDamage",
            List::class.java, GameEvent::class.java,
            List::class.java, Map::class.java
        )
        method.isAccessible = true
        return method.invoke(detector, detected, event, triggers, slots) as List<PendingTrigger>
    }

    val source = EntityId("attacker")
    val recipient = EntityId("defender")
    val controller = EntityId("controller")

    val received = card("Test Same Event Received") {
        manaCost = "{1}"
        typeLine = "Creature — Wall"
        power = 0
        toughness = 5
        triggeredAbility {
            trigger = Triggers.self.isDealtDamage()
            effect = Effects.GainLife(DynamicAmounts.triggerDamageAmount())
        }
    }.script.triggeredAbilities.single()

    val dealt = card("Test Same Event Dealt") {
        manaCost = "{1}"
        typeLine = "Creature — Beast"
        power = 4
        toughness = 4
        triggeredAbility {
            trigger = Triggers.self.dealsDamage()
            effect = Effects.GainLife(DynamicAmounts.triggerDamageAmount())
        }
    }.script.triggeredAbilities.single()

    fun pending(ability: TriggeredAbility, event: DamageDealtEvent, observer: EntityId) =
        PendingTrigger(
            ability = ability,
            sourceId = observer,
            sourceName = "Test source",
            controllerId = controller,
            triggerContext = TriggerContext.fromEvent(event)
        )

    test("receives damage: two detections from the same event fold once without indexing an unappended trigger") {
        val event = DamageDealtEvent(sourceId = source, targetId = recipient, amount = 3, isCombatDamage = true)
        val one = pending(received, event, recipient)
        val triggers = mutableListOf<PendingTrigger>()
        val slots = mutableMapOf<List<Any?>, Int>()
        val result = fold(event, listOf(one, one.copy()), triggers, slots)
        result.size shouldBe 1
        result.single().triggerContext.damageAmount shouldBe 3
        triggers.size shouldBe 0
    }

    test("receives damage: a second simultaneous event increases damage only once") {
        val event1 = DamageDealtEvent(sourceId = source, targetId = recipient, amount = 2, isCombatDamage = true)
        val event2 = DamageDealtEvent(sourceId = EntityId("second-attacker"), targetId = recipient, amount = 3, isCombatDamage = true)
        val triggers = mutableListOf<PendingTrigger>()
        val slots = mutableMapOf<List<Any?>, Int>()
        triggers.addAll(fold(event1, listOf(pending(received, event1, recipient)), triggers, slots))
        val kept = fold(event2, listOf(pending(received, event2, recipient)), triggers, slots)
        kept.size shouldBe 0
        triggers.size shouldBe 1
        triggers.single().triggerContext.damageAmount shouldBe 5
    }

    test("deals damage: one event duplicate is not double counted; later recipient is folded") {
        val event1 = DamageDealtEvent(sourceId = source, targetId = recipient, amount = 2, isCombatDamage = true)
        val event2 = DamageDealtEvent(sourceId = source, targetId = EntityId("other-blocker"), amount = 3, isCombatDamage = true)
        val triggers = mutableListOf<PendingTrigger>()
        val slots = mutableMapOf<List<Any?>, Int>()
        val first = fold(event1, listOf(pending(dealt, event1, source), pending(dealt, event1, source)), triggers, slots)
        first.size shouldBe 1
        triggers.addAll(first)
        val next = fold(event2, listOf(pending(dealt, event2, source)), triggers, slots)
        next.size shouldBe 0
        triggers.size shouldBe 1
        triggers.single().triggerContext.damageAmount shouldBe 5
    }

    test("noncombat damage never enters the simultaneous combat fold") {
        val event = DamageDealtEvent(sourceId = source, targetId = recipient, amount = 2, isCombatDamage = false)
        val one = pending(received, event, recipient)
        val kept = fold(event, listOf(one, one.copy()), mutableListOf(), mutableMapOf())
        kept.size shouldBe 2
    }
})
