package com.arcanatable

/**
 * Owner, Nov 6 (batch 4): layout presets that make really noticeable changes but still look good, with Keep / Back.
 *
 * A preset only uses settings that already work everywhere: card size, board spacing, button size and hand card size
 * (the old Adjust UI values), information density, which screen pieces show, the turn label, and optionally a palette.
 * It never moves individual buttons, so it can't hide Pass or the way back and fits every screen size.
 */
internal data class StudioLook(val cardSize:Float,val spacing:Float,val buttons:Float,val handCards:Float)

internal data class StudioPreset(
    val name:String,
    val description:String,
    val look:StudioLook,
    val density:String,
    val hiddenPieces:Set<String>,
    val turnLabel:String,
    /** A palette name from [tablePalettePresets], "Light", "Table default", or null to leave the colours alone. */
    val palette:String?,
)

/** Reads and writes the live look values (TableController state plus their saved preferences). */
internal class StudioLookHost(val read:()->StudioLook,val write:(StudioLook)->Unit)

internal object StudioPresets {
    val CLASSIC_LOOK=StudioLook(1f,1f,1f,1f)
    val all=listOf(
        StudioPreset("Classic","Arcana as it ships",CLASSIC_LOOK,"Balanced",emptySet(),"both","Table default"),
        StudioPreset("Big Table","Large cards, little else on screen",StudioLook(1.3f,1.15f,1f,1.15f),"Minimal",
            setOf("fullTableLabel","engineThinking","timer","commanderChip"),"round",null),
        StudioPreset("Big & Clear","Big buttons and cards, high contrast",StudioLook(1.2f,1.1f,1.3f,1.3f),"Balanced",
            setOf("fullTableLabel","timer"),"turn","Monochrome"),
        StudioPreset("Command Center","Every number and piece of information",StudioLook(0.85f,0.8f,0.9f,0.9f),"Detailed",
            emptySet(),"both","Slate"),
        StudioPreset("Pocket","Compact, for the folded screen",StudioLook(0.8f,0.6f,0.85f,0.8f),"Minimal",
            setOf("fullTableLabel","engineThinking","timer","manaBar"),"hidden",null),
        StudioPreset("Spread Out","Room to breathe between cards",StudioLook(1.05f,1.9f,1.05f,1.05f),"Balanced",
            setOf("fullTableLabel"),"both","Deep Ocean"),
    )

    fun theme(palette:String?):Map<String,String>? {
        val colors=when(palette) {
            null->return null
            "Table default"->return emptyMap()
            "Light"->lightTableColors
            else->tablePalettePresets.firstOrNull{it.name==palette}?.colors ?: return null
        }
        return StudioLayout.themeRoles.associateWith{role->tableColorHex(colors.colorRole(if(role=="gold")"counter" else role))}
    }

    /** The draft with [preset] applied (one Undo step through [StudioControlsRuntime.change]); [keepColours] skips the palette. */
    fun project(draft:StudioControlProject,preset:StudioPreset,keepColours:Boolean):StudioControlProject {
        val pieces=preset.hiddenPieces.filter{it in SCREEN_PIECES}.toSet()
        return draft.copy(density=preset.density,screenDensity=emptyMap(),hiddenPieces=pieces,turnLabel=preset.turnLabel,
            theme=if(keepColours)draft.theme else theme(preset.palette) ?: draft.theme)
    }
}
