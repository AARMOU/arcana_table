package com.arcanatable.customcards

// Trusted-main, real-Argentum acceptance scenarios for Copper Gnomes.
// Queue #508; Copilot candidate #511. Written from the printed Oracle, not the implementation:
// "{4}, Sacrifice this creature: You may put an artifact card from your hand onto the battlefield."
// Sacrificing is a cost (it happens even when the optional effect is declined); only an artifact
// card from its controller's hand qualifies; {4} must be paid; only the controller may activate.
import com.arcanatable.customcards.cards.CopperGnomes
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CopperGnomesIndependentCertificationTest {
    @Test fun sacrificeAndPutAnArtifactFromHandOntoTheBattlefield() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 4)
        assertNull(d.activate(p, gnomes).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertTrue("Sol Ring entered under its owner's control", d.permanentsNamed(p, "Sol Ring").isNotEmpty())
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertTrue("Gnomes was sacrificed", d.getGraveyardCardNames(p).contains("Copper Gnomes"))
        assertTrue(d.permanentsNamed(p, "Copper Gnomes").isEmpty())
    }

    @Test fun decliningTheOptionalEffectStillSacrificesAndKeepsTheArtifactInHand() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 4)
        assertNull(d.activate(p, gnomes).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.permanentsNamed(p, "Sol Ring").isEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
        assertTrue("sacrifice is a cost", d.getGraveyardCardNames(p).contains("Copper Gnomes"))
    }

    @Test fun aNonartifactCardInHandCannotBePutOntoTheBattlefield() {
        val d = customScenario(CopperGnomes, "Grizzly Bears")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        d.putCardInHand(p, "Grizzly Bears")
        d.giveColorlessMana(p, 4)
        assertNull(d.activate(p, gnomes).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears")))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Grizzly Bears"))
        assertTrue(d.getGraveyardCardNames(p).contains("Copper Gnomes"))
    }

    @Test fun threeManaIsNotEnoughToActivate() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val p = d.player1
        val gnomes = d.putCreatureOnBattlefield(p, "Copper Gnomes")
        d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 3)
        assertNotNull(d.activate(p, gnomes).error)

        assertTrue("not sacrificed", d.permanentsNamed(p, "Copper Gnomes").isNotEmpty())
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
    }

    @Test fun onlyTheControllerCanActivate() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val gnomes = d.putCreatureOnBattlefield(d.player1, "Copper Gnomes")
        d.putCardInHand(d.player2, "Sol Ring")
        d.giveColorlessMana(d.player2, 4)
        assertNotNull(d.activate(d.player2, gnomes).error)

        assertTrue(d.permanentsNamed(d.player1, "Copper Gnomes").isNotEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Sol Ring").isEmpty())
    }
}
