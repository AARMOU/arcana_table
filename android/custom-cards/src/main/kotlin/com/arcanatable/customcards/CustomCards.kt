package com.arcanatable.customcards

import com.arcanatable.customcards.cards.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.core.DeckFormat

/**
 * Arcana's custom cards: cards Argentum doesn't have yet, written in Argentum's own card language and
 * shipped to the app as data (custom-cards.jsonl). Kept apart from Argentum's library so an Argentum
 * update never overwrites them; when Argentum gains one of these cards, its own version wins (the app
 * only uses a custom card when the library has no card of that name).
 *
 * [setCode] is the card's real printing, so the app finds the right art.
 */
object CustomCards {
    data class Entry(val card: CardDefinition, val setCode: String, val deck: String)

    private const val TOKENS_WIN = "Tokens Win 9/26"
    private const val ARTIFICER = "Artificer v4"

    val entries: List<Entry> = listOf(
        // Tokens Win 9/26 (Rocco, Cabaretti Caterer)
        Entry(RoccoCabarettiCaterer, "SNC", TOKENS_WIN),
        Entry(AncientCopperDragon, "CLB", TOKENS_WIN),
        Entry(DisplacedDinosaurs, "WHO", TOKENS_WIN),
        Entry(JaheiraFriendOfTheForest, "CLB", TOKENS_WIN),
        Entry(JawsRelentlessPredator, "SLD", TOKENS_WIN),
        Entry(MotherOfRunes, "ULG", TOKENS_WIN),
        Entry(TataruTaru, "FIC", TOKENS_WIN),
        Entry(TheCabbageMerchant, "TLE", TOKENS_WIN),
        Entry(BagEndBanquet, "HOC", TOKENS_WIN),
        Entry(DefenseOfTheHeart, "ULG", TOKENS_WIN),
        Entry(FoodChain, "MMQ", TOKENS_WIN),
        Entry(NinjaPizza, "TMC", TOKENS_WIN),
        Entry(QuestForRenewal, "WWK", TOKENS_WIN),
        Entry(SneakAttack, "USG", TOKENS_WIN),
        Entry(ReflectingPool, "TMP", TOKENS_WIN),
        // Artificer v4 (Miles "Tails" Prower). Reflecting Pool is shared with Tokens Win.
        Entry(MilesTailsPrower, "SLD", ARTIFICER),
        Entry(CyberdriveAwakener, "NEC", ARTIFICER),
        Entry(TheFantasticar, "MSC", ARTIFICER),
        Entry(ContinueQuestion, "TMC", ARTIFICER),
        Entry(SmugglersCopter, "KLD", ARTIFICER),
        Entry(ShorikaiGenesisEngine, "NEO", ARTIFICER),
        Entry(SaiMasterThopterist, "M19", ARTIFICER),
        Entry(CelestialColonnade, "WWK", ARTIFICER),
        Entry(LotusField, "M20", ARTIFICER),
        Entry(PowerDepot, "MH2", ARTIFICER),
        Entry(IronManBleedingEdge, "MSC", ARTIFICER),
        Entry(IrmaPartTimeMutant, "TMC", ARTIFICER),
        Entry(FlashPhotography, "FIC", ARTIFICER),
        Entry(SakashimaOfAThousandFaces, "CMR", ARTIFICER),
        Entry(UrzasSaga, "MH2", ARTIFICER),
    )

    private val COMMANDER_LEGAL = setOf(DeckFormat.COMMANDER, DeckFormat.LEGACY, DeckFormat.VINTAGE)

    /** Every custom card as the app receives it: printing set and Commander legality stamped. */
    val all: List<CardDefinition> by lazy {
        entries.map { (card, set, _) ->
            card.copy(
                setCode = card.setCode ?: set,
                legalFormats = card.legalFormats.ifEmpty { COMMANDER_LEGAL },
            )
        }
    }
}
