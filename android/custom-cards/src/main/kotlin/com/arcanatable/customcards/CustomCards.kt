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
    private const val AYULA = "Ayula Bears"
    private const val HAZEL = "Hazel Squirrels"
    private const val SLIVERS = "Sliver Overlord"
    private const val POOL = "Card pool 10/26"

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
        // Ayula, Queen Among Bears deck.
        Entry(MotherBear, "MH1", AYULA),
        Entry(Werebear, "ODY", AYULA),
        Entry(BoscoJustABear, "TLE", AYULA),
        Entry(WilsonRefinedGrizzly, "CLB", AYULA),
        Entry(DruidsDeliverance, "RTR", AYULA),
        Entry(TraverseTheOutlands, "C17", AYULA),
        Entry(Bearscape, "ODY", AYULA),
        // Hazel of the Rootbloom deck. Tippy-Toe is omitted: its copy-token replacement
        // behavior is not represented by the pinned engine's current building blocks.
        Entry(FrogSquirrels, "TLE", HAZEL),
        Entry(ScurryOak, "MH2", HAZEL),
        Entry(NutCollector, "DMR", HAZEL),
        Entry(CompySwarm, "REX", HAZEL),
        Entry(PrimalAmulet, "XLN", HAZEL),
        Entry(ImmoralBargain, "SOC", HAZEL),
        Entry(AcornHarvest, "TOR", HAZEL),
        Entry(CloakwoodHermit, "CLB", HAZEL),
        Entry(SpringleafParade, "ECC", HAZEL),
        Entry(BlackMarketConnections, "MSC", HAZEL),
        // Sliver Overlord deck. Hatchery Sliver (Replicate) and Pulmonic Sliver's
        // optional replacement effect remain unsupported by this engine version.
        Entry(AcidicSliver, "TPR", SLIVERS),
        Entry(CloudshredderSliver, "CMM", SLIVERS),
        Entry(CrystallineSliver, "CMM", SLIVERS),
        Entry(DiffusionSliver, "CMM", SLIVERS),
        Entry(DregscapeSliver, "MH1", SLIVERS),
        Entry(FirstSliversChosen, "MH1", SLIVERS),
        Entry(FreneticSliver, "PLC", SLIVERS),
        Entry(FungusSliver, "TSR", SLIVERS),
        Entry(HarmonicSliver, "CMM", SLIVERS),
        Entry(HibernationSliver, "CMM", SLIVERS),
        Entry(HomingSliver, "TSR", SLIVERS),
        Entry(LavabellySliver, "CMM", SLIVERS),
        Entry(LeechingSliver, "M15", SLIVERS),
        Entry(SliverQueen, "STH", SLIVERS),
        Entry(SliverGravemother, "CMM", SLIVERS),
        // Other cards from the submitted collection. Unsupported mechanics remain listed in
        // android/docs/CUSTOM_CARDS_HANDOFF.md rather than being approximated silently.
        Entry(ConstrictingSliver, "CMM", POOL),
        Entry(KodamaOfTheWestTree, "NEO", POOL),
        Entry(AllosaurusShepherd, "2X2", POOL),
        Entry(BarretAvalancheLeader, "FIC", POOL),
        Entry(KratosGodOfWar, "SLD", POOL),
        Entry(BlightsteelColossus, "MBS", POOL),
        Entry(NekusarTheMindrazer, "CMM", POOL),
        Entry(MichelangeloTheHeart, "TMC", POOL),
        Entry(VenomEddieBrock, "SPE", POOL),
        Entry(PurphorosGodOfTheForge, "CMM", POOL),
        Entry(HornOfGreed, "CN2", POOL),
        Entry(Silence, "M14", POOL),
        Entry(SwordOfBodyAndMind, "2XM", POOL),
        Entry(SwordOfFeastAndFamine, "2XM", POOL),
        Entry(SwordOfFireAndIce, "2XM", POOL),
        Entry(SwordOfLightAndShadow, "2XM", POOL),
        Entry(SwordOfWarAndPeace, "2XM", POOL),
        Entry(SigardasAid, "CMR", POOL),
        Entry(YavimayaHollow, "VMA", POOL),
        Entry(ViolentOutburst, "ARB", POOL),
        Entry(EleshNornGrandCenobite, "NPH", POOL),
        Entry(RampagingFerocidon, "XLN", POOL),
        Entry(SpittingDilophosaurus, "REX", POOL),
        Entry(DescendantsPath, "TLE", POOL),
        Entry(RaffineSchemingSeer, "SNC", POOL),
        Entry(CloudExSoldier, "FIC", POOL),
        Entry(LeonardoTheBalance, "TMC", POOL),
        Entry(WakkaDevotedGuardian, "FIC", POOL),
        Entry(CaptainJamesTKirk, "TRK", POOL),
        Entry(RaphaelTheMuscle, "TMC", POOL),
        // Missing cards from the three decks reviewed for Build 108.
        Entry(CuriousAltisaur, "LCC", "Velociramptor Beefcake 9/26"),
        Entry(KinjallisSunwing, "LCC", "Velociramptor Beefcake 9/26"),
        Entry(MigrationPath, "TDC", "Velociramptor Beefcake 9/26"),
        Entry(QuicksilverAmulet, "M12", "Velociramptor Beefcake 9/26"),
        Entry(RampagingBrontodon, "LCC", "Velociramptor Beefcake 9/26"),
        Entry(Polyraptor, "RIX", "Velociramptor Beefcake 9/26"),
        Entry(UnclaimedTerritory, "XLN", "Velociramptor Beefcake 9/26"),
        Entry(Rewind, "M21", "Velociramptor Beefcake 9/26"),
        Entry(SunkenRuins, "SHM", "Velociramptor Beefcake 9/26"),
        Entry(WaywardSwordtooth, "RIX", "Velociramptor Beefcake 9/26"),
        Entry(WakeningSunsAvatar, "XLN", "Velociramptor Beefcake 9/26"),
        // Requested follow-up cards: dinosaur deck and Mabel deck.
        Entry(PantlazaSunFavored, "LCC", "Velociramptor Beefcake 9/26"),
        Entry(AkromasWill, "CMR", "Velociramptor Beefcake 9/26"),
        Entry(ChandrasIgnition, "ORI", "Velociramptor Beefcake 9/26"),
        Entry(SavageOrder, "REX", "Velociramptor Beefcake 9/26"),
        Entry(BronzebeakForagers, "LCC", "Velociramptor Beefcake 9/26"),
        Entry(DismantlingWave, "C20", "Mabel deck"),
        Entry(FatalPush, "AER", POOL),
        Entry(DinosaurEgg, "LCC", "Velociramptor Beefcake 9/26"),
        Entry(ZacamaPrimalCalamity, "RIX", "Velociramptor Beefcake 9/26"),
        Entry(BrigoneSoldierOfMeletis, "J25", "Mabel deck"),
        Entry(SavaiThundermane, "IKO", "Mabel deck"),
        // Six official Warhammer 40K and Eldrazi Commander precons (import batch 1).
        // These card definitions use existing Argentum primitives; deck publication waits
        // until the entire precon passes catalog and behavior validation.
        Entry(SpatialContortion, "OGW", "Six WotC precons"),
        Entry(SkitteringInvasion, "ROE", "Six WotC precons"),
        Entry(BlightGrenade, "40K", "Six WotC precons"),
        Entry(DenyReality, "ARB", "Six WotC precons"),
        Entry(BituminousBlast, "ARB", "Six WotC precons"),
        Entry(AwakeningZone, "ROE", "Six WotC precons"),
        Entry(CrashingDrawbridge, "ELD", "Six WotC precons"),
        Entry(AllIsDust, "ROE", "Six WotC precons"),
        Entry(ForTheEmperor, "40K", "Six WotC precons"),
        Entry(KnightPaladin, "40K", "Six WotC precons"),
        Entry(BloodcrusherOfKhorne, "40K", "Six WotC precons"),
        Entry(TyranidInvasion, "40K", "Six WotC precons"),
        Entry(SanguinaryPriest, "40K", "Six WotC precons"),
        Entry(Venomthrope, "40K", "Six WotC precons"),
        Entry(TyranidPrime, "40K", "Six WotC precons"),
        Entry(SpaceMarineScout, "40K", "Six WotC precons"),
        Entry(Defile, "MH1", "Six WotC precons"),
        Entry(Mutilate, "TOR", "Six WotC precons"),
        Entry(TheirNameIsDeath, "40K", "Six WotC precons"),
        Entry(Plasmancer, "40K", "Six WotC precons"),
        Entry(UlamogsCrusher, "ROE", "Six WotC precons"),
        Entry(AssaultIntercessor, "40K", "Six WotC precons"),
        Entry(HeraldOfSlaanesh, "40K", "Six WotC precons"),
        Entry(NoiseMarine, "40K", "Six WotC precons"),
        Entry(ChaosTerminatorLord, "40K", "Six WotC precons"),
        Entry(SisterHospitaller, "40K", "Six WotC precons"),
        Entry(DesertOfTheGlorified, "HOU", "Six WotC precons"),
        Entry(GuildlessCommons, "CMR", "Six WotC precons"),
        Entry(TendoIceBridge, "BOK", "Six WotC precons"),
        Entry(HullBreach, "PLS", "Six WotC precons"),
        Entry(AtalanJackal, "40K", "Six WotC precons"),
        Entry(WingedHiveTyrant, "40K", "Six WotC precons"),
        Entry(TallymanOfNurgle, "40K", "Six WotC precons"),
        Entry(NecronDeathmark, "40K", "Six WotC precons"),
        Entry(SeaGateWreckage, "CMM", "Six WotC precons"),
        Entry(BeaconOfUnrest, "40K", "Six WotC precons"),
        Entry(GeierReachSanitarium, "CMM", "Six WotC precons"),
        Entry(DeployToTheFront, "40K", "Six WotC precons"),
        Entry(SpawningBed, "M3C", "Six WotC precons"),
        Entry(MageRingNetwork, "CMM", "Six WotC precons"),
        Entry(UginsInsight, "BFZ", "Six WotC precons"),
        Entry(GargoyleFlock, "40K", "Six WotC precons"),
        Entry(SifterOfSkulls, "M3C", "Six WotC precons"),
        Entry(DrownerOfHope, "M3C", "Six WotC precons"),
        Entry(MoltenSlagheap, "40K", "Six WotC precons"),
        Entry(RuinsOfOranRief, "CMM", "Six WotC precons"),
        Entry(ShrineOfTheForsakenGods, "CMM", "Six WotC precons"),
        Entry(BileBlight, "BNG", "Six WotC precons"),
        Entry(Exterminatus, "40K", "Six WotC precons"),
        Entry(EndlessAtlas, "CMM", "Six WotC precons"),
        Entry(Cryptothrall, "40K", "Six WotC precons"),
        Entry(NightScythe, "40K", "Six WotC precons"),
        Entry(SautekhImmortal, "40K", "Six WotC precons"),
        Entry(SkorpekhLord, "40K", "Six WotC precons"),
        Entry(Lictor, "40K", "Six WotC precons"),
        Entry(BredForTheHunt, "40K", "Six WotC precons"),
        Entry(DeathleaperTerrorWeapon, "40K", "Six WotC precons"),
        Entry(GenestealerLocus, "40K", "Six WotC precons"),
        Entry(PrimarisChaplain, "40K", "Six WotC precons"),
        Entry(SisterOfSilence, "40K", "Six WotC precons"),
        Entry(SpaceMarineDevastator, "40K", "Six WotC precons"),
        Entry(GreyKnightParagon, "40K", "Six WotC precons"),
        Entry(PlagueDrone, "40K", "Six WotC precons"),
        Entry(HeraldsOfTzeentch, "40K", "Six WotC precons"),
        Entry(LordOfChange, "40K", "Six WotC precons"),
        Entry(Venomcrawler, "40K", "Six WotC precons"),
        Entry(DeepfathomSkulker, "OGW", "Six WotC precons"),
        Entry(SkitteringCicada, "CMM", "Six WotC precons"),
        Entry(BaneOfBalaGed, "CMM", "Six WotC precons"),
        Entry(SoulOfNewPhyrexia, "CMM", "Six WotC precons"),
        Entry(InquisitorGreyfax, "40K", "Six WotC precons"),
        Entry(SzarekhTheSilentKing, "40K", "Six WotC precons"),
        Entry(BastionProtector, "40K", "Six WotC precons"),
        Entry(Reverberate, "40K", "Six WotC precons"),
        Entry(WarpingWail, "CMM", "Six WotC precons"),
        Entry(StonecoilSerpent, "CMM", "Six WotC precons"),
        Entry(ReturnOfTheWildspeaker, "M3C", "Six WotC precons"),
        Entry(ThunderwolfCavalry, "40K", "Six WotC precons"),
        Entry(VanguardSuppressor, "40K", "Six WotC precons"),
        Entry(CompanyCommander, "40K", "Six WotC precons"),
        Entry(FellTheMighty, "40K", "Six WotC precons"),
        Entry(SkorpekhDestroyer, "40K", "Six WotC precons"),
        Entry(TyrantGuard, "40K", "Six WotC precons"),
        Entry(FlayedOne, "40K", "Six WotC precons"),
        Entry(SicarianInfiltrator, "40K", "Six WotC precons"),
        Entry(Aberrant, "40K", "Six WotC precons"),
        Entry(MandateOfAbaddon, "40K", "Six WotC precons"),
        Entry(PrimarisEliminator, "40K", "Six WotC precons"),
        Entry(HexmarkDestroyer, "40K", "Six WotC precons"),
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
