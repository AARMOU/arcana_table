package com.arcanatable

import org.json.JSONObject
import java.io.File

/**
 * Example Studio projects the owner can import (Studio ⋮ > Project > Restore) to see what Studio can do (owner, Nov 7).
 * The committed files live in docs/studio-ux/templates/; [StudioTemplatesTest] checks they still import and still
 * match this source. Regenerate with `main` after changing a template.
 */
internal object StudioTemplates {
    private const val TABLE="Table" // MainActivity provides this screen to everything on the table
    private fun id(site:String,index:Int=0)=studioStableId(site,TABLE,index)
    private val pass=id("TurnSignals.combinedClickable.1")
    private val appMenu=id("TableMainLayout.IconButton.2")
    /** The top button row changes with the game (DRAW, STACK, COMBAT…), so every slot gets the same look. */
    private val headerButtons=(0..7).map{id("TableBoardViews.HeaderButton",it)}
    private val stripButtons=(0..9).map{id("ArgentumUi.StripButton",it)}

    private fun button(id:String,label:String,x:Float,y:Float,w:Float,h:Float,fs:Float=13f,r:Float=12f,strong:Boolean=false,icon:String="None",visible:Boolean=true)=
        StudioElement(id,"button",label,x,y,w,h,fs,r,strong=strong,icon=icon,visible=visible)
    private fun metric(i:Int,x:Float,y:Float,w:Float,h:Float,fs:Float=10f,r:Float=8f,visible:Boolean=true)=
        StudioElement("metric$i","metric",listOf("STACK","LIBRARY","GRAVEYARD","EXILE")[i],x,y,w,h,fs,r,visible=visible,
            icon=listOf("Stack","Library","Zone","Zone")[i])
    private fun card(i:Int,x:Float,y:Float,w:Float,h:Float)=StudioElement("card$i","card","Card ${i+1}",x,y,w,h,r=6f)

    /** Dark navy and gold. Big rounded Pass, phase-tab styled top row, round-only turn label. */
    fun midnightArena():String {
        val controls=StudioControlProject(
            controls=mapOf(pass to StudioControlStyle(height=56f,radius=28f,font=16f,type="Action"),
                appMenu to StudioControlStyle(ink="#F2B33D"))+
                headerButtons.associateWith{StudioControlStyle(radius=16f,type="Phase tab")}+
                stripButtons.associateWith{StudioControlStyle(radius=16f,type="Panel")},
            density="Balanced",
            theme=mapOf("background" to "#0B1220","panel" to "#131C2E","raised" to "#1C2840","accent" to "#F2B33D",
                "text" to "#F4EEDF","muted" to "#8A93A6","gold" to "#F2B33D"),
            turnLabel="round",hiddenPieces=setOf("engineThinking","fullTableLabel"))
        val hand=StudioLayout(listOf(
            button("pass","PASS",16f,344f,380f,56f,fs=16f,r=28f,strong=true,icon="Play"),
            button("undo","UNDO",16f,408f,120f,44f,icon="Undo"),
            button("actions","ACTIONS",144f,408f,124f,44f,icon="Menu"),
            button("cast","CAST",276f,408f,120f,44f),
        )+(0..3).map{metric(it,16f+it*96f,460f,92f,40f)}
            +(0..6).map{card(it,16f+(it%4)*96f,508f+(it/4)*140f,92f,132f)},
            theme=mapOf("panel" to "#131C2E","accent" to "#F2B33D","gold" to "#F2B33D"))
        return bundle(controls,hand)
    }

    /** Light paper-and-teal palette, roomy Detailed information, large cards on top and buttons below them. */
    fun daylightTable():String {
        val controls=StudioControlProject(
            controls=mapOf(pass to StudioControlStyle(radius=12f,type="Action"),
                appMenu to StudioControlStyle(fill="#FFFFFF",ink="#2E7D6B",radius=12f))+
                headerButtons.associateWith{StudioControlStyle(radius=12f,type="Panel")}+
                stripButtons.associateWith{StudioControlStyle(radius=12f,type="Counter")},
            density="Detailed",
            theme=mapOf("background" to "#F4EFE6","panel" to "#FFFFFF","raised" to "#E8E1D4","accent" to "#2E7D6B",
                "text" to "#1D2420","muted" to "#6B7570","gold" to "#B8862B"),
            turnLabel="both")
        val hand=StudioLayout((0..5).map{card(it,16f+(it%3)*128f,344f+(it/3)*180f,120f,172f)}+listOf(
            button("pass","PASS",16f,712f,184f,56f,fs=15f,strong=true,icon="Play"),
            button("undo","UNDO",208f,712f,88f,56f,icon="Undo"),
            button("cast","CAST",304f,712f,92f,56f),
            button("actions","LEGAL ACTIONS",16f,776f,380f,48f,icon="Menu"),
        )+(0..3).map{metric(it,16f+it*96f,832f,90f,48f,fs=11f)},
            theme=mapOf("panel" to "#FFFFFF","raised" to "#E8E1D4","accent" to "#2E7D6B","text" to "#1D2420"))
        return bundle(controls,hand)
    }

    /** High-contrast black and mint for one-handed play: Minimal information, fewer pieces, a huge Pass under the thumb. */
    fun oneThumb():String {
        val controls=StudioControlProject(
            controls=mapOf(pass to StudioControlStyle(width=160f,height=64f,radius=32f,font=18f,type="Action"))+
                headerButtons.associateWith{StudioControlStyle(radius=8f,font=12f,type="Counter")}+
                stripButtons.associateWith{StudioControlStyle(radius=32f,type="Action")},
            density="Minimal",
            theme=mapOf("background" to "#000000","panel" to "#0E1412","raised" to "#18211E","accent" to "#3EE6B0",
                "text" to "#FFFFFF","muted" to "#7F8C87","gold" to "#FFD166"),
            turnLabel="turn",hiddenPieces=setOf("fullTableLabel","timer","commanderChip","engineThinking"))
        val hand=StudioLayout((0..4).map{card(it,16f+it*80f,344f,72f,104f)}+listOf(
            button("actions","ACTIONS",16f,460f,120f,64f,icon="Menu"),
            button("undo","UNDO",16f,532f,56f,64f,icon="Undo"),
            button("cast","CAST",80f,532f,56f,64f),
            button("pass","PASS",144f,460f,252f,136f,fs=22f,r=32f,strong=true,icon="Play"),
            metric(0,16f,604f,120f,44f),
        )+(1..3).map{metric(it,144f+(it-1)*86f,604f,80f,44f,visible=false)},
            theme=mapOf("accent" to "#3EE6B0","panel" to "#0E1412"))
        return bundle(controls,hand)
    }

    val all get()=linkedMapOf("Studio-template-Midnight-Arena.json" to midnightArena(),
        "Studio-template-Daylight-Table.json" to daylightTable(),"Studio-template-One-Thumb.json" to oneThumb())

    private fun bundle(controls:StudioControlProject,hand:StudioLayout):String {
        require(controls.controls.values.all{it.problems().isEmpty()}){"Template control problem"}
        require(hand.problems().isEmpty()){hand.problems().joinToString()}
        return JSONObject().put("version",3).put("renderer","arcana-native").put("orientation",STUDIO_LAYOUT_KEY)
            .put("controls",JSONObject(controls.json())).put("hand",JSONObject(hand.json())).toString(2)+"\n"
    }

    @JvmStatic fun main(args:Array<String>) {
        val dir=File(args.firstOrNull() ?: "docs/studio-ux/templates").also{it.mkdirs()}
        all.forEach{(name,text)->
            val root=JSONObject(text)
            val p=StudioControlProject.parse(root.getJSONObject("controls").toString());StudioLayout.parse(root.getJSONObject("hand").toString())
            File(dir,name).writeText(text);println("wrote $name (${p.controls.size} control edits, ${p.theme.size} colours)")
        }
    }
}
