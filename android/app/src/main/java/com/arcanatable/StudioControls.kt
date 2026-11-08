package com.arcanatable

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import org.json.JSONObject
import org.json.JSONArray

/** Native-flow controls retain their native anchor; offsets are explicit design coordinates. */
internal data class StudioControlStyle(
    val dx:Float=0f,val dy:Float=0f,val width:Float?=null,val height:Float?=null,
    val font:Float?=null,val radius:Float?=null,val fill:String?=null,val ink:String?=null,
    val label:String?=null,val visible:Boolean=true,val binding:String?=null,val icon:String="None",val layer:Float=0f,
    /** Owner note 26: one of [StudioButtonTypes] names, or null for the button's own look. */
    val type:String?=null,
    /** Owner, Nov 7: opacity for every editable item, 0–1 (null = solid). Pass and the way back never go below 0.5. */
    val alpha:Float?=null,
    /** Owner, Nov 8: background opacity, 0–1 (null = solid). Fades only the box behind, never its words or cards. */
    val backdrop:Float?=null,
) {
    fun problems()=buildList {
        if(listOfNotNull(dx,dy,width,height,font,radius,layer).any {!it.isFinite()})add("Use finite dimensions.")
        if(kotlin.math.abs(dx)>2000 || kotlin.math.abs(dy)>4000)add("Offset exceeds the canvas.")
        if(width!=null && width !in 1f..2000f || height!=null && height !in 1f..4000f)add("Width and height must be positive.")
        if(font!=null && font !in 8f..64f || radius!=null && radius !in 0f..100f)add("Invalid font or corner radius.")
        if(fill!=null && parseTableColorHex(fill)==null || ink!=null && parseTableColorHex(ink)==null)add("Use #RRGGBB colors.")
        if(label!=null && label.length>120)add("Label is too long.")
        if(binding!=null && (binding.length>180 || !binding.startsWith("native:") || !StudioSites.contains(binding)))add("Unknown native binding.")
        if(icon !in StudioLayout.icons)add("Unknown icon.")
        if(layer !in -100f..100f)add("Layer must be between -100 and 100.")
        if(type!=null && type !in StudioButtonTypes.names)add("Unknown button type.")
        if(alpha!=null && (!alpha.isFinite() || alpha !in 0f..1f))add("Opacity must be 0–100%.")
        if(backdrop!=null && (!backdrop.isFinite() || backdrop !in 0f..1f))add("Background must be 0–100%.")
    }
    /** Owner note 14: soft advice only. It marks the control red in the editor and never blocks Apply. */
    fun advice():String?=if(width!=null && width<44f || height!=null && height<44f)"Hard to tap: smaller than 44." else null
    fun json()=JSONObject().put("dx",dx).put("dy",dy).put("visible",visible).put("icon",icon).put("layer",layer).apply {
        width?.let{put("width",it)};height?.let{put("height",it)};font?.let{put("font",it)};radius?.let{put("radius",it)}
        fill?.let{put("fill",it)};ink?.let{put("ink",it)};label?.let{put("label",it)};binding?.let{put("binding",it)}
        type?.let{put("type",it)};alpha?.let{put("alpha",it.toDouble())};backdrop?.let{put("backdrop",it.toDouble())}
    }
    companion object {
        fun parse(o:JSONObject):StudioControlStyle {
            fun number(k:String)=if(o.has(k))o.getDouble(k).toFloat() else null
            fun text(k:String)=if(o.has(k))o.getString(k) else null
            return StudioControlStyle(o.optDouble("dx",0.0).toFloat(),o.optDouble("dy",0.0).toFloat(),number("width"),number("height"),
                number("font"),number("radius"),text("fill"),text("ink"),text("label"),o.optBoolean("visible",true),text("binding"),
                o.optString("icon","None"),o.optDouble("layer",0.0).toFloat(),text("type"),number("alpha"),number("backdrop")).also {require(it.problems().isEmpty()){it.problems().joinToString("\n")}}
        }
    }
}

/** Stable control ID: the code site, the screen it is on, and its order among same-site controls on that screen. */
internal fun studioStableId(site:String,screen:String,index:Int)="native:$site/s${Integer.toHexString(screen.hashCode())}:$index"

internal val TURN_LABELS=listOf("both","round","turn","hidden")

/**
 * Owner, Nov 7: "settings that toggle ui elements on the screen", all in the editor. Each entry is a piece of the
 * main table screen that can be switched off; switched-off pieces take no space. More pieces are added as GPT's
 * STUDIO-J13 inventory lands.
 */
internal val SCREEN_PIECES=linkedMapOf(
    "phaseBar" to "Turn / phase bar",
    "playerChips" to "Player chips",
    "tableButton" to "TABLE button",
    "fullTableLabel" to "\"FULL TABLE · players\" label",
    "manaBar" to "Mana pool bar",
    "buttonRow" to "Button row (Pass, Graveyard, Exile…)",
    "commanderChip" to "Commander chip",
    "engineThinking" to "Engine \"thinking\" bar",
    "timer" to "Turn timer")

/**
 * Whether a main-screen piece is shown (Studio ⋮ > Layout > Show on screen). Callers leave the whole piece out, row
 * and all, so the battlefield takes the space (Goose STUDIO-J13: hiding only the children left the rows behind).
 */
@androidx.compose.runtime.Composable
internal fun studioPieceShown(key:String):Boolean =
    !BuildConfig.STUDIO_ENABLED || TutorialCoachState.model!=null || key !in (LocalStudioControls.current?.project()?.hiddenPieces ?: emptySet())

/**
 * Owner, Nov 7: "A full turn is counted after everyone goes." The engine counts every player's turn; a round is one
 * turn for everyone at the table. [turnNumber] is the engine's 1-based count; [seats] the players at the table.
 */
internal fun turnLabelText(mode:String,turnNumber:Int,seats:Int):String? {
    val turn=turnNumber.coerceAtLeast(1);val round=(turn-1)/seats.coerceAtLeast(1)+1
    return when(mode){"round"->"Round $round";"turn"->"Turn $turn";"hidden"->null;else->"R$round · T$turn"}
}

internal data class StudioControlCopy(val id:String,val source:String,val style:StudioControlStyle)
internal data class StudioControlProject(val controls:Map<String,StudioControlStyle> = emptyMap(),
    val copies:List<StudioControlCopy> = emptyList(),val density:String="Balanced",val theme:Map<String,String> = emptyMap(),
    val owners:Map<String,String> = emptyMap(),val screenDensity:Map<String,String> = emptyMap(),
    /** Owner, Nov 7: what the top turn label shows — "both" (R2 · T3), "round", "turn" or "hidden". */
    val turnLabel:String="both",
    /** Owner, Nov 7: main-screen pieces switched off in Studio ⋮ > Layout > Show on screen (see [SCREEN_PIECES]). */
    val hiddenPieces:Set<String> = emptySet(),
    /** Owner notes 16/17: the table's background picture (a name from [StudioBackdrop]), how it fits, and how dimmed. */
    val backdrop:String?=null,val backdropFit:String="Fill",val backdropDim:Float=0.35f) {
    fun colors(base:TableColors)=StudioLayout(emptyList(),theme=theme).colors(base)
    /**
     * Moves old position-based IDs (`native:<site>/<compose key hash>:<n>`) to stable ones (`native:<site>/s<screen>:<n>`).
     * The old hash changed whenever the app's code moved, which detached saved edits between builds. The screen comes
     * from [owners], recorded when the control was edited. IDs without a recorded screen, or two old IDs that would
     * land on the same new ID, are kept as they are and never guessed (they are logged).
     */
    fun withStableIds():StudioControlProject {
        val old=Regex("^native:(.+)/(-?\\d+):(\\d+)$")
        val all=controls.keys+copies.map{it.source}+owners.keys
        val target=all.associateWith {id->old.matchEntire(id)?.let{m->owners[id]?.let{screen->studioStableId(m.groupValues[1],screen,m.groupValues[3].toInt())}}}
        val clashes=target.values.filterNotNull().groupingBy{it}.eachCount().filterValues{it>1}.keys
        val map=target.filterValues{it!=null && it !in clashes}.mapValues{it.value!!}
            .filter{(_,new)->new !in controls.keys && new !in owners.keys}
        target.filter{(id,new)->new==null && old.matches(id) || new in clashes}.keys.forEach{StudioLog.add("Kept an old ID unmatched (no single stable match): $it")}
        if(map.isEmpty())return this
        fun m(id:String)=map[id] ?: id
        return copy(controls=controls.mapKeys{m(it.key)},copies=copies.map{it.copy(source=m(it.source))},owners=owners.mapKeys{m(it.key)})
    }
    fun json()=JSONObject().put("version",1).put("renderer","native-flow").put("density",density).put("theme",JSONObject(theme)).put("owners",JSONObject(owners)).put("screenDensity",JSONObject(screenDensity)).put("turnLabel",turnLabel).put("hiddenPieces",JSONArray(hiddenPieces.sorted())).put("backdropFit",backdropFit).put("backdropDim",backdropDim.toDouble()).apply{backdrop?.let{put("backdrop",it)}}.put("controls",JSONObject().apply {
        controls.forEach {(id,style)->put(id,style.json())}
    }).put("copies",JSONArray().apply {copies.forEach {put(JSONObject().put("id",it.id).put("source",it.source).put("style",it.style.json()))}}).toString()
    companion object {
        fun parse(text:String):StudioControlProject {
            require(text.length in 1..4_000_000){"Project is empty or too large."}
            val root=JSONObject(text)
            require(root.getInt("version")==1 && root.getString("renderer")=="native-flow"){"Unsupported controls project."}
            val controls=root.getJSONObject("controls")
            require(controls.length()<=10000){"Too many controls."}
            val copies=root.optJSONArray("copies") ?: JSONArray()
            require(copies.length()<=100){"Too many duplicate controls."}
            val parsedCopies=(0 until copies.length()).map {i->val c=copies.getJSONObject(i)
                val id=c.getString("id");val source=c.getString("source")
                require(id.startsWith("copy:") && id.length in 6..80 && id.removePrefix("copy:").all{it.isLetterOrDigit() || it=='-'}){"Invalid duplicate ID."}
                require(source.startsWith("native:") && StudioSites.contains(source)){"Unknown duplicate binding."}
                StudioControlCopy(id,source,StudioControlStyle.parse(c.getJSONObject("style")))
            }
            require(parsedCopies.map{it.id}.distinct().size==parsedCopies.size){"Duplicate control IDs."}
            val density=root.optString("density","Balanced");require(density in listOf("Minimal","Balanced","Detailed")){"Unknown information profile."}
            val themeObject=root.optJSONObject("theme") ?: JSONObject()
            require(themeObject.keys().asSequence().all{it in StudioLayout.themeRoles}){"Unknown palette role."}
            val theme=themeObject.keys().asSequence().associateWith{themeObject.getString(it)}
            require(theme.values.all{parseTableColorHex(it)!=null}){"Invalid palette color."}
            fun stringMap(key:String):Map<String,String> {
                val o=root.optJSONObject(key) ?: JSONObject()
                require(o.length()<=10000){"Too many screen settings."}
                return o.keys().asSequence().associateWith{o.getString(it)}
            }
            val owners=stringMap("owners")
            require(owners.all{(id,name)->StudioSites.contains(id) && name.length in 1..100}){"Unknown screen owner."}
            val densities=stringMap("screenDensity")
            require(densities.all{(name,value)->name.length in 1..100 && value in listOf("Minimal","Balanced","Detailed")}){"Invalid screen density."}
            val turnLabel=root.optString("turnLabel","both");require(turnLabel in TURN_LABELS){"Unknown turn label."}
            val piecesArray=root.optJSONArray("hiddenPieces") ?: JSONArray()
            val hiddenPieces=(0 until piecesArray.length()).map{piecesArray.getString(it)}.toSet()
            require(hiddenPieces.all{it in SCREEN_PIECES}){"Unknown screen piece."}
            val backdrop=if(root.has("backdrop"))root.getString("backdrop") else null
            require(backdrop==null || StudioBackdrop.validName(backdrop)){"Unknown background picture."}
            val fit=root.optString("backdropFit","Fill");require(fit in StudioBackdrop.FITS){"Unknown background fit."}
            val dim=root.optDouble("backdropDim",0.35).toFloat();require(dim in 0f..0.9f){"Background dimming out of range."}
            return StudioControlProject(controls.keys().asSequence().associateWith {id->
                require(id.startsWith("native:") && id.length<=180 && StudioSites.contains(id)){"Unknown control ID."}
                StudioControlStyle.parse(controls.getJSONObject(id))
            },parsedCopies,density,theme,owners,densities,turnLabel,hiddenPieces,backdrop,fit,dim)
        }
    }
}

internal class StudioControlStore(private val prefs:SharedPreferences,val orientation:String,val profile:String) {
    private val key="studio_controls_v1_${orientation}_$profile"
    fun load():StudioControlProject=listOf(key,"${key}_backup").firstNotNullOfOrNull {k->prefs.getString(k,null)?.let{raw->
        runCatching{StudioControlProject.parse(raw)}.getOrNull()?.let{keepOriginal(k,raw,it)}}} ?: StudioControlProject(density=profile.takeIf{it in listOf("Minimal","Balanced","Detailed")} ?: "Balanced")
    fun draft()=prefs.getString("${key}_draft",null)?.let{raw->runCatching{StudioControlProject.parse(raw)}.getOrNull()?.let{keepOriginal("${key}_draft",raw,it)}}
    /**
     * Permanent IDs (Nov 6 note 27, Goose STUDIO-J7 migration gate). A project saved with the old position-based IDs
     * is moved to stable IDs as it is read; the untouched original JSON is kept once under `_v1_original` for
     * rollback, so a bad migration can never overwrite the last good copy.
     */
    private fun keepOriginal(k:String,raw:String,parsed:StudioControlProject):StudioControlProject {
        val migrated=parsed.withStableIds()
        if(migrated!=parsed && !prefs.contains("${k}_v1_original"))prefs.edit().putString("${k}_v1_original",raw).apply()
        return migrated
    }
    fun saveDraft(project:StudioControlProject){prefs.edit().putString("${key}_draft",project.json()).apply()}
    fun apply(project:StudioControlProject) {
        check(stage(prefs.edit(),project).commit()){"Could not save controls."}
    }
    fun stage(edit:SharedPreferences.Editor,project:StudioControlProject):SharedPreferences.Editor {
        StudioControlProject.parse(project.json())
        return edit.putString("${key}_backup",load().json()).putString(key,project.json()).putString("${key}_draft",project.json())
    }
    fun reset(){prefs.edit().remove(key).remove("${key}_backup").remove("${key}_draft").commit()}
}

internal data class StudioLiveControl(val id:String,val screen:String,val kind:String,val title:String,val enabled:Boolean,
    val bounds:Rect=Rect.Zero,val window:Int=0,val protectedLabel:Boolean=false,val viewportWidth:Float=0f,val viewportHeight:Float=0f,
    val captionEditable:Boolean=true)
internal val StudioLiveControl.isAction get()=kind.endsWith("action")
// Owner, Nov 8: an opponent's board only repeats your own Pass/Confirm, so those copies may be hidden (IDs tied to an
// opponent carry "@opp", see LocalStudioSeat). Yours, the main bar and every way back stay protected.
internal val StudioLiveControl.requiredNativeAction get()=id.startsWith("native:") && isAction && kind!="card-action" &&
    (!id.contains("@opp") || listOf("BACK","CLOSE","APP MENU","ARCANA STUDIO","RESUME","RECONNECT").any{title.uppercase().startsWith(it)}) &&
    (StudioSites.site(id)=="TableMainLayout.IconButton.2" || listOf("PASS","DECLARE","CONFIRM","KEEP","CANCEL","CLOSE","RESUME","RECONNECT","BACK","ARCANA STUDIO","APP MENU").any{title.uppercase().startsWith(it)})

/** Only current, composed native callbacks can be invoked. No callbacks are serialized. */
internal class StudioControlsRuntime(val prefs:SharedPreferences,val orientation:String) {
    private data class Revision(val controls:StudioControlProject,val hand:StudioLayout?)
    val controls=mutableStateMapOf<String,StudioLiveControl>()
    private val callbacks=mutableMapOf<String,()->Unit>()
    private val captions=mutableMapOf<String,String>()
    private val liveText=mutableStateMapOf<String,String>()
    fun text(id:String)=liveText[id] ?: controls[id]?.title.orEmpty()
    fun text(id:String,value:String){if(liveText[id]!=value)liveText[id]=value}
    private val claims=mutableMapOf<String,LinkedHashMap<Any,Int>>()
    fun claim(base:String,token:Any):String {
        val owners=claims.getOrPut(base){linkedMapOf()}
        val index=owners[token] ?: generateSequence(0){it+1}.first{it !in owners.values}.also{owners[token]=it}
        return "$base:$index"
    }
    /** Player-tied ID → the screen-order ID it had before (see [LocalStudioSeat]). */
    private val legacy=mutableMapOf<String,String>()
    fun alias(id:String,old:String){if(id!=old)legacy[id]=old}
    /** The saved style for [id], or for its old screen-order ID until it is edited under its own. */
    private fun saved(project:StudioControlProject,id:String)=project.controls[id] ?: legacy[id]?.let{project.controls[it]}
    fun release(base:String,token:Any){claims[base]?.let{it.remove(token);if(it.isEmpty())claims.remove(base)}}
    var editing by mutableStateOf(false);private set
    /** Owner note 29: "Fill it up" shows a busy table while editing. Never saved; off whenever Studio closes. */
    var fillItUp by mutableStateOf(false)
    private var capturing by mutableStateOf(false)
    var panelOpen by mutableStateOf(false)
    private var lead by mutableStateOf<String?>(null)
    /** Setting the selection directly (a tap, a list row, Close) always goes back to one item. */
    var selected:String? get()=lead;set(v){lead=v;group=emptySet();if(v!=null)tablePicked=false}
    /** Owner notes 16/17: the table itself is picked (empty table space was tapped); its panel sets the background. */
    var tablePicked by mutableStateOf(false)
    /** Multi-select (owner, Nov 7): other controls edited together with [selected]. Empty = one item selected. */
    var group by mutableStateOf<Set<String>>(emptySet())
    fun members()=listOfNotNull(selected)+(group-setOfNotNull(selected))
    var revision by mutableIntStateOf(0);private set
    var profile by mutableStateOf(StudioProfiles(prefs,orientation).activeName());private set
    private var applied by mutableStateOf(store().load())
    var draft by mutableStateOf(applied);private set
    val active get()=editing || capturing || applied.controls.isNotEmpty() || applied.copies.isNotEmpty() || (applied.density=="Minimal" || applied.screenDensity.values.any{it=="Minimal"})
    fun enableCatalog(){capturing=true}
    private var pendingHand by mutableStateOf<StudioLayout?>(null)
    private val pendingHandKey get()="studio_controls_pending_hand_${orientation}_$profile"
    private val undo=ArrayDeque<Revision>()
    private val redo=ArrayDeque<Revision>()
    private val recovered=mutableStateMapOf<String,String>()
    val canUndo get()=undo.isNotEmpty()
    val canRedo get()=redo.isNotEmpty()
    fun store()=StudioControlStore(prefs,orientation,profile)
    fun record(info:StudioLiveControl,callback:(()->Unit)?) {
        val previous=controls[info.id]
        val next=info.copy(title=StudioNames.of(info.id) ?: StudioNames.orFallback(info.id,if(info.kind=="container-action" && info.title=="Control")previous?.title ?: info.title else info.title),
            bounds=previous?.bounds ?: info.bounds,window=info.window.takeIf{it!=0} ?: previous?.window ?: 0,
            viewportWidth=previous?.viewportWidth ?: info.viewportWidth,viewportHeight=previous?.viewportHeight ?: info.viewportHeight)
        if(previous!=next)controls[info.id]=next
        if(callback!=null)callbacks[info.id]=callback else callbacks.remove(info.id)
        captions.filterValues{it==info.id}.keys.forEach {id->controls[id]?.let{val t=StudioNames.of(id) ?: info.title;if(it.title!=t)controls[id]=it.copy(title=t)}}
    }
    fun remove(id:String){controls.remove(id);callbacks.remove(id);captions.remove(id);liveText.remove(id)}
    fun bounds(id:String,rect:Rect,window:Int,width:Float=0f,height:Float=0f){controls[id]?.let {
        val child=caption(id,it,rect,window)
        val next=it.copy(title=StudioNames.of(id) ?: child?.title ?: StudioNames.orFallback(id,it.title),bounds=rect,window=window,viewportWidth=width,viewportHeight=height)
        if(next!=it)controls[id]=next
        // A word inside a panel reports its new place after the panel does, so the panel's name is picked again here
        // (local check, Nov 7: after a move the YOU chip was named "40", the only word whose old spot was still inside).
        if(!next.isAction)controls.values.filter{c->c.kind=="container-action" && !c.captionEditable && c.window==window && c.bounds.contains(rect.center)}
            .forEach{c->val pick=caption(c.id,c,c.bounds,window)?.title;val t=StudioNames.of(c.id) ?: pick ?: c.title;if(t!=c.title)controls[c.id]=c.copy(title=t)}
    }}
    /**
     * The words that name a panel: the top-left words inside it. Owner's Studio log, Nov 7: "Open hand tray" grew to 648
     * tall and was then named "40" (what it grew over), so a resized panel keeps the caption it had.
     */
    private fun caption(id:String,control:StudioLiveControl,rect:Rect,window:Int):StudioLiveControl? {
        if(control.kind!="container-action" || control.captionEditable)return null
        val resized=saved(project(),id)?.let{s->s.width!=null || s.height!=null}==true
        val kept=if(!resized)null else captions[id]?.let{c->controls[c]}?.takeIf{c->c.window==window && c.bounds.width>0}
        return (kept ?: controls.values.filter{c->c.id!=id && !c.isAction && c.window==window && c.bounds.width>0 && rect.contains(c.bounds.center)}
            .minWithOrNull(compareBy<StudioLiveControl>{c->kotlin.math.round(c.bounds.top/8f)}.thenBy{c->c.bounds.left}))
            ?.also{c->captions[id]=c.id}
    }
    fun project()=if(editing)draft else applied
    fun style(id:String,screen:String?=null,kind:String?=null):StudioControlStyle {
        if(!editing && id in recovered)return StudioControlStyle()
        val project=project()
        // Owner, Nov 7: Minimal hides live info on the table, but never the game log's text (the log is only its text).
        val onScreen=screen ?: controls[id]?.screen
        // Owner, Nov 7: game log entries are all the same kind of item, so resizing one resizes them all. An entry with
        // no edit of its own follows an edited entry built from the same piece of code.
        // Only the game log: "Dialogs" also contains "log", so every dialog's items used to copy each other's edits.
        val shared=if(project.controls[id]==null && StudioNames.isGameLog(onScreen)) {
            val site=id.substringAfter("native:").substringBefore("/")
            project.controls.entries.firstOrNull{(k,_)->k!=id && k.substringAfter("native:").substringBefore("/")==site}?.value
        } else null
        val style=saved(project,id) ?: shared ?: project.copies.firstOrNull{it.id==id}?.style ?: StudioControlStyle(
            visible=!((project.screenDensity[onScreen] ?: project.density)=="Minimal" && (kind ?: controls[id]?.kind)=="data" &&
                onScreen?.contains("log",ignoreCase=true)!=true))
        // Owner note 33: a running tutorial needs what it points at, so hidden items show again until it ends. Moved and
        // resized items need nothing: the coach follows where each item really is.
        if(!editing && !style.visible && TutorialCoachState.model!=null)return style.copy(visible=true)
        if(!editing && !style.visible) {
            val replacements=project.copies.filter{it.style.visible && (it.style.binding ?: it.source)==id}
            if(replacements.isNotEmpty() && replacements.all{it.id in recovered})return StudioControlStyle()
        }
        if(!editing && controls[id]?.requiredNativeAction==true && (!style.visible || style.binding!=null && style.binding!=id) &&
            !hasReplacement(project,id))return StudioControlStyle()
        return style
    }
    fun recover(id:String,reason:String){if(!editing && id !in recovered)recovered[id]=reason}
    private fun hasReplacement(project:StudioControlProject,id:String)=
        project.copies.any{it.style.visible && !isRecovered(it.id) && (it.style.binding ?: it.source)==id} ||
            project.controls.any{it.key!=id && it.value.visible && it.value.binding==id && !isRecovered(it.key)}
    fun recoveryReasons()=recovered.values.distinct()+
        (if(!editing && applied.controls.any{!it.value.visible || it.value.binding!=null})applied.controls.filter{(id,style)->
            controls[id]?.requiredNativeAction==true && (!style.visible || style.binding!=null && style.binding!=id) && !hasReplacement(applied,id)
        }.keys.map{"A required native action was restored; update its saved layout."} else emptyList())
    fun isRecovered(id:String)=!editing && id in recovered
    /** Names of controls restored to their native layout because the saved one didn't fit (for the notice). */
    fun recoveredTitles():List<String> = recovered.keys.mapNotNull{controls[it]?.title?.takeIf(String::isNotBlank)}.distinct().take(3)
    fun available(id:String):Boolean=controls[id]?.enabled==true && id in callbacks
    fun canInvoke(source:String,target:String):Boolean {
        val from=controls[source] ?: return false
        val to=controls[target] ?: return false
        return available(source) && available(target) && from.window==to.window && from.screen==to.screen
    }
    fun canDisplay(source:String,target:String):Boolean {
        val from=controls[source] ?: return false;val to=controls[target] ?: return false
        return from.kind in listOf("data","label") && to.kind in listOf("data","label") && from.screen==to.screen && from.window==to.window
    }
    fun invokeFrom(source:String,target:String):Boolean=if(canInvoke(source,target))invoke(target) else false
    fun invoke(id:String):Boolean {if(!available(id))return false;callbacks[id]?.invoke() ?: return false;return true}
    /** Edits made before Home/Decks/Setup/Lobby/Settings left Studio (owner D33) are dropped, never applied again. */
    private fun withoutExcluded(p:StudioControlProject)=p.copy(
        controls=p.controls.filterKeys{!StudioScope.excluded(p.owners[it] ?: StudioSites.screen(it))},
        copies=p.copies.filterNot{StudioScope.excluded(p.owners[it.source] ?: StudioSites.screen(it.source))},
        owners=p.owners.filterValues{!StudioScope.excluded(it)},
        screenDensity=p.screenDensity.filterKeys{!StudioScope.excluded(it)})
    fun begin(id:String?=null){StudioLog.add("Opened Studio · profile $profile${id?.let{" · selected ${title(it)}"}.orEmpty()}");capturing=true;draft=withoutExcluded(store().draft() ?: applied);pendingHand=if(prefs.getBoolean(pendingHandKey,false))StudioStore(prefs,orientation,profile).draft() else null;undo.clear();redo.clear();editing=true;selected=id;tablePicked=false;panelOpen=true}
    fun select(id:String){if(!editing)begin(id) else {selected=id;panelOpen=true}}
    /**
     * Hold on a control: select it and every other control of the same kind on the same screen (all player chips, all
     * log entries). Holding the lead again goes back to one item.
     */
    fun holdSelect(id:String) {
        val c=controls[id] ?: return
        if(selected==id && group.isNotEmpty()){group=emptySet();return}
        val site=StudioEditorGeometry.siteOf(id)
        lead=id;panelOpen=true
        val shown=controls.values.filter{it.id!=id && it.window==c.window && it.bounds.width>0f && style(it.id).visible}
        // Owner, Nov 7: "hold does nothing on some tabs". Items that look alike there are often built by different code,
        // so "same code on the same screen" found nothing. Hold now widens step by step until it finds company:
        // same code on this screen → same code anywhere in this window → the same kind of item side by side in the
        // same box (a row of buttons, a list of entries).
        val sameSite=shown.filter{StudioEditorGeometry.siteOf(it.id)==site}
        val parent=StudioEditorGeometry.parentOf(controls.values,c)?.id
        group=(sameSite.filter{it.screen==c.screen}.ifEmpty{sameSite}.ifEmpty{
            shown.filter{it.kind==c.kind && it.screen==c.screen && StudioEditorGeometry.parentOf(controls.values,it)?.id==parent}
        }).map{it.id}.toSet()
        StudioLog.add("Selected ${members().size} like ${title(id)}")
    }
    /** While several are selected, a tap adds or removes one item instead of replacing the selection. */
    fun toggleMember(id:String) {
        when {
            id==lead->{lead=group.firstOrNull();group=group.drop(1).toSet()}
            id in group->group=group-id
            lead==null->lead=id
            else->group=group+id
        }
    }
    private fun withStyle(p:StudioControlProject,id:String,style:StudioControlStyle)=
        if(id.startsWith("copy:"))p.copy(copies=p.copies.map{if(it.id==id)it.copy(style=style) else it})
        else legacy[id].let{old->
            // An edit to a player-tied item moves its old screen-order edit to the player's own ID.
            val c=if(old!=null && id !in p.controls)p.controls-old else p.controls
            val o=if(old!=null && id !in p.controls)p.owners-old else p.owners
            p.copy(controls=c+(id to style),owners=controls[id]?.screen?.let{o+(id to it)} ?: o)
        }
    /** Edits every selected control in one step (one Undo), as the lead's change applied to each. */
    fun editGroup(next:StudioControlStyle,density:Float) {
        val first=selected ?: return
        val leadStyle=style(first);val leadBox=controls[first]?.bounds
        var p=withStyle(draft,first,next)
        group.forEach {id->
            val box=controls[id]?.bounds ?: return@forEach
            p=withStyle(p,id,StudioEditorGeometry.groupEdit(leadStyle,next,style(id),(leadBox?.width ?: 0f)/density,(leadBox?.height ?: 0f)/density,
                box.width/density,box.height/density))
        }
        change(p)
    }
    /** Lines up the selected controls against the one picked first (one Undo). */
    fun align(mode:StudioEditorGeometry.Align,density:Float) {
        val first=selected ?: return
        val rects=members().mapNotNull{id->controls[id]?.bounds?.takeIf{it.width>0f}?.let{id to it}}.toMap()
        var p=draft
        StudioEditorGeometry.align(mode,rects,first,density).forEach {(id,m)->
            val s=style(id)
            p=withStyle(p,id,s.copy(dx=(s.dx+m.ddx).coerceIn(-2000f,2000f),dy=(s.dy+m.ddy).coerceIn(-4000f,4000f),
                width=m.width?.coerceIn(StudioEditorGeometry.FLOOR_DP,2000f) ?: s.width,height=m.height?.coerceIn(StudioEditorGeometry.FLOOR_DP,4000f) ?: s.height))
        }
        StudioLog.add("Align ${mode.label}: ${rects.size} items")
        change(p)
    }
    private fun title(id:String)=controls[id]?.title?.takeIf{it.isNotBlank()} ?: id
    /** Studio log (owner, Nov 7): one line per edited control or hand change, "title: field before→after". */
    private fun logChange(before:StudioControlProject,after:StudioControlProject,handBefore:StudioLayout?,handAfter:StudioLayout?) {
        if(quiet)return
        (before.controls.keys+after.controls.keys).forEach {id->
            val a=before.controls[id] ?: StudioControlStyle();val b=after.controls[id] ?: StudioControlStyle()
            if(a!=b)StudioLog.add("Edit ${title(id)} [${StudioNames.screen(controls[id]?.screen ?: before.owners[id] ?: after.owners[id] ?: "?")}]: ${StudioLog.diff(a,b)}")
        }
        if(before.hiddenPieces!=after.hiddenPieces)StudioLog.add("Show on screen: hidden ${after.hiddenPieces.sorted().ifEmpty{listOf("nothing")}.joinToString()}")
        if(before.backdrop!=after.backdrop || before.backdropFit!=after.backdropFit || before.backdropDim!=after.backdropDim)
            StudioLog.add("Background: ${after.backdrop?.let{"picture, ${after.backdropFit}, dim ${(after.backdropDim*100).toInt()}%"} ?: "none"}")
        if(before.turnLabel!=after.turnLabel)StudioLog.add("Turn label ${before.turnLabel}→${after.turnLabel}")
        val oldCopies=before.copies.associateBy{it.id};val newCopies=after.copies.associateBy{it.id}
        (newCopies.keys-oldCopies.keys).forEach{StudioLog.add("Duplicate of ${title(newCopies[it]!!.source)} added")}
        (oldCopies.keys-newCopies.keys).forEach{StudioLog.add("Duplicate of ${title(oldCopies[it]!!.source)} removed")}
        newCopies.forEach{(id,c)->oldCopies[id]?.takeIf{it.style!=c.style}?.let{StudioLog.add("Edit copy of ${title(c.source)}: ${StudioLog.diff(it.style,c.style)}")}}
        if(handAfter!=handBefore && handAfter!=null)StudioLog.add(StudioLog.handDiff(handBefore ?: handDraftStored(),handAfter))
    }
    private fun handDraftStored()=StudioStore(prefs,orientation,profile).let{it.draft() ?: it.load()}
    fun inspectNativeScreen(){editing=true;panelOpen=false}
    private fun saveDraft() {
        store().saveDraft(draft)
        // Undoing back past the first hand edit leaves no pending hand: the stored draft must go too, or the hand
        // kept showing the undone edit (found by the hand multi-select test: Undo didn't restore the widths).
        StudioStore(prefs,orientation,profile).let{store->pendingHand?.let{store.saveDraft(it)} ?: store.clearDraft()}
        prefs.edit().putBoolean(pendingHandKey,pendingHand!=null).apply()
    }
    /**
     * Owner rule: Pass and the way back (App menu, Back, Close…) can't be hidden, and Studio never blocks with a refused
     * Apply (owner's Studio log, Nov 7: "Apply refused: Keep required action App menu reachable"). So hiding one simply
     * doesn't happen: it stays visible, its Visible switch is off-limits, and the inspector says why in red.
     */
    fun mustStayVisible(id:String,project:StudioControlProject=draft)=controls[id]?.requiredNativeAction==true && !hasReplacement(project,id)
    private fun keepRequiredVisible(project:StudioControlProject):StudioControlProject {
        val forced=project.controls.filter{(id,style)->!style.visible && mustStayVisible(id,project)}
        if(forced.isEmpty())return project
        forced.keys.forEach{StudioLog.add("Kept ${title(it)} visible: it can't be hidden")}
        return project.copy(controls=project.controls+forced.mapValues{(_,style)->style.copy(visible=true)})
    }
    fun change(proposed:StudioControlProject,hand:StudioLayout?=pendingHand){val project=keepRequiredVisible(proposed);if(project!=draft || hand!=pendingHand){
        // During a slider drag only the first step makes an Undo point; the log gets one line when the drag ends.
        if(gesture && gestureStart!=null){draft=project;pendingHand=hand;revision++;return}
        if(gesture)gestureStart=Revision(draft,pendingHand) else logChange(draft,project,pendingHand,hand)
        undo.addLast(Revision(draft,pendingHand));if(undo.size>100)undo.removeFirst();redo.clear();draft=project;pendingHand=hand;saveDraft();revision++
    }}
    private var gesture=false
    private var gestureStart:Revision?=null
    /** Owner, Nov 7: hold +/− for a slider. A whole drag is one Undo step and one log line. */
    fun beginGesture(){gesture=true;gestureStart=null}
    fun endGesture(){
        gesture=false
        gestureStart?.let{start->logChange(start.controls,draft,start.hand,pendingHand);saveDraft()}
        gestureStart=null
    }
    fun edit(id:String,style:StudioControlStyle)=change(withStyle(draft,id,style))
    fun duplicate(id:String,density:Float) {
        val control=controls[id] ?: error("Select a native button first.")
        require(control.isAction || control.kind in listOf("data","label")){"Select a button or information module."}
        val source=draft.copies.firstOrNull{it.id==id}?.source ?: id
        require(if(control.isAction)available(source) else canDisplay(id,source)){"This source is not currently available."}
        val copy=StudioControlCopy("copy:"+java.util.UUID.randomUUID().toString(),source,style(id).copy(
            visible=true,label=if(control.isAction)style(id).label else null,dx=control.bounds.left/density,dy=control.bounds.bottom/density+8f,
            width=(control.bounds.width/density).coerceAtLeast(44f),height=(control.bounds.height/density).coerceAtLeast(44f)))
        change(draft.copy(copies=draft.copies+copy,owners=draft.owners+(source to control.screen)));selected=copy.id
    }
    fun removeCopy(id:String){change(draft.copy(copies=draft.copies.filterNot{it.id==id}));selected=null}
    /** The hand layout being edited: this session's pending draft, else the saved draft or applied hand. */
    fun handDraft():StudioLayout?=pendingHand ?: StudioStore(prefs,orientation,profile).let{it.draft() ?: it.load()}
    fun hasPendingHand()=pendingHand!=null
    fun changeHand(hand:StudioLayout)=change(draft,hand)
    fun undo(){if(undo.isNotEmpty()){StudioLog.add("Undo");redo.addLast(Revision(draft,pendingHand));val previous=undo.removeLast();draft=previous.controls;pendingHand=previous.hand;saveDraft();revision++}}
    fun redo(){if(redo.isNotEmpty()){StudioLog.add("Redo");undo.addLast(Revision(draft,pendingHand));val next=redo.removeLast();draft=next.controls;pendingHand=next.hand;saveDraft();revision++}}
    /**
     * Leave the editor and KEEP the draft (owner feedback bugs 18, 20, 21). Leaving used to drop the pending-hand flag
     * while the stored hand draft still showed on reopen, so Apply silently skipped it; button edits were kept but hand
     * edits were not. Now both stay as a draft until Apply or [discard].
     */
    fun cancel(){if(editing)StudioLog.add("Left Studio · draft kept");pendingHand=null;profile=StudioProfiles(prefs,orientation).activeName();applied=store().load();draft=applied;editing=false;capturing=false;panelOpen=false;revision++}
    /** Throw the draft away (buttons and hand) and go back to the applied layout. */
    fun discard(){StudioLog.add("Discarded draft");store().saveDraft(applied);StudioStore(prefs,orientation,profile).clearDraft()
        prefs.edit().remove(pendingHandKey).apply();cancel()}
    fun hasDraft()=store().draft()?.let{it!=applied}==true || prefs.getBoolean(pendingHandKey,false)
    fun apply() {
        val errors=problems()
        if(errors.isNotEmpty())StudioLog.add("Apply refused: ${errors.joinToString(" | ")}")
        require(errors.isEmpty()){errors.joinToString("\n")}
        advice().forEach{(id,why)->StudioLog.add("Applied with red mark · ${if(id.startsWith("hand:"))id.removePrefix("hand:") else title(id)}: $why")}
        val edit=store().stage(prefs.edit(),draft)
        pendingHand?.let{StudioStore(prefs,orientation,profile).stage(edit,it)}
        StudioProfiles(prefs,orientation).stageActive(edit,profile)
        check(edit.remove(pendingHandKey).commit()){"Could not save project."}
        StudioLog.add("Applied · profile $profile · ${draft.controls.size} edited controls, ${draft.copies.size} duplicates${if(pendingHand!=null)", hand design" else ""}")
        pendingHand=null
        applied=draft;editing=false;capturing=false;panelOpen=false;recovered.clear();revision++
    }
    fun chooseProfile(name:String){StudioLog.add("Profile → $name");require(name in StudioProfiles(prefs,orientation).names());profile=name;applied=store().load();draft=store().draft() ?: applied
        pendingHand=if(prefs.getBoolean(pendingHandKey,false))StudioStore(prefs,orientation,profile).draft() else null
        editing=true;undo.clear();redo.clear();revision++}
    fun copyProfile(name:String) {
        require(problems().isEmpty()){problems().joinToString("\n")}
        StudioControlProject.parse(draft.json())
        StudioProfiles(prefs,orientation).copyProfile(profile,name,pendingHand)
        StudioControlStore(prefs,orientation,name).apply(draft)
        if(pendingHand!=null)prefs.edit().putBoolean("studio_controls_pending_hand_${orientation}_$name",true).commit()
        chooseProfile(name)
    }
    private fun owner(id:String)=draft.owners[id] ?: controls[id]?.screen ?: StudioSites.screen(id)
    fun density(screen:String?,value:String) {
        require(value in listOf("Minimal","Balanced","Detailed"))
        val matching=draft.controls.mapValues{(id,style)->if(value=="Detailed" && (screen==null || owner(id)==screen))style.copy(visible=true) else style}
        change(if(screen==null)draft.copy(density=value,controls=matching,screenDensity=emptyMap())
            else draft.copy(screenDensity=draft.screenDensity+(screen to value),controls=matching))
    }
    fun resetScreen(screen:String){change(draft.copy(controls=draft.controls.filterKeys{owner(it)!=screen},
        copies=draft.copies.filterNot{owner(it.source)==screen},owners=draft.owners.filterValues{it!=screen},
        screenDensity=draft.screenDensity+(screen to "Balanced")))}
    fun resetProfile(){StudioLog.add("Reset profile $profile");store().reset();StudioStore(prefs,orientation,profile).reset();prefs.edit().remove(pendingHandKey).commit();pendingHand=null;applied=StudioControlProject();draft=applied;recovered.clear();revision++}
    fun exportProject():String=JSONObject().put("version",3).put("renderer","arcana-native").put("orientation",orientation)
        .put("controls",JSONObject(draft.json())).apply {
            (pendingHand ?: StudioStore(prefs,orientation,profile).load())?.let{put("hand",JSONObject(it.json()))}
        }.toString()
    fun importProject(text:String) {
        StudioLog.add("Import started")
        require(text.length in 1..5_000_000){"Project is empty or too large."}
        val root=JSONObject(text)
        if(root.optString("renderer")=="native-flow"){val p=StudioControlProject.parse(text).withStableIds();quietly{change(p)}
            StudioLog.add("Imported into profile $profile: ${p.controls.size} edited items, ${p.copies.size} duplicates");return}
        require(root.getInt("version")==3 && root.getString("renderer")=="arcana-native"){"Unsupported project."}
        // Owner, Nov 7: "trouble getting profiles imported". The first try was refused with "Select the project's
        // orientation", which the owner can't do: Studio keeps one layout for every orientation and fold state
        // (STUDIO_LAYOUT_KEY), so a file saved in landscape or portrait imports the same way.
        val controls=StudioControlProject.parse(root.getJSONObject("controls").toString()).withStableIds()
        val hand=root.optJSONObject("hand")?.let{StudioLayout.parse(it.toString())}
        quietly{change(controls,hand)}
        StudioLog.add("Imported into profile $profile: ${controls.controls.size} edited items, ${controls.copies.size} duplicates${if(hand!=null)", hand design" else ""}")
    }
    /** One import used to write a log line per item (hundreds), pushing everything else out of the shared log. */
    private var quiet=false
    private fun quietly(block:()->Unit){quiet=true;try{block()}finally{quiet=false}}
    fun problems():List<String> = buildList {
        // There is no floating Studio button in play (owner D33), so no window may lose its last visible button:
        // a pop-up that can't be dismissed would otherwise become a dead end.
        controls.values.filter{it.isAction && it.window!=0}.groupBy{it.window to it.screen}.values.forEach {group->
            if(group.none{this@StudioControlsRuntime.style(it.id).visible} && group.any{saved(draft,it.id)?.visible==false})
                add("Keep at least one button visible on ${group.first().screen}.")
        }
        if(draft.theme.keys.any{it !in StudioLayout.themeRoles} || draft.theme.values.any{parseTableColorHex(it)==null})add("Invalid palette roles or colors.")
        pendingHand?.let{addAll(it.problems())}
        (draft.controls+draft.copies.associate{it.id to it.style}).forEach {(id,style)->
            addAll(style.problems().map{"${controls[id]?.title ?: "Control"}: $it"})
            val control=controls[id]
            if(control?.requiredNativeAction==true && (!style.visible || style.binding!=null && style.binding!=id) && !hasReplacement(draft,id))
                add("Keep required action ${control.title} reachable.")
            // A rebinding to an action on another screen/window, or one that isn't available, can never run: refused.
            if(style.binding!=null && control!=null && !(if(control.isAction)canInvoke(id,style.binding) else canDisplay(id,style.binding)))
                add("${control.title}: choose an available ${if(control.isAction)"action" else "information source"} on the same screen.")
            if(control?.protectedLabel==true && style.label!=null)add("Dynamic game data cannot be replaced by a fixed label.")
            if(control?.kind=="container-action" && !control.captionEditable && style.label!=null)add("Edit this control's text layer to rename it.")
            if(control?.kind=="container-action" && !control.captionEditable && style.binding!=null)add("Duplicate this compound control as a button to change its action.")
        }
    }
    /**
     * Owner note 14: everything that isn't a lock-out is advice. Each entry is control id → one plain-language line;
     * the editor marks those controls red. Apply never looks at this.
     */
    fun advice():Map<String,String> = buildMap {
        (draft.controls+draft.copies.associate{it.id to it.style}).forEach {(id,style)->
            style.advice()?.let{put(id,it)}
            val control=controls[id] ?: return@forEach
            if(control.isAction && style.visible && control.bounds.width>0 && control.viewportWidth>0) {
                val r=control.bounds
                if(r.left< -1 || r.top< -1 || r.right>control.viewportWidth+1 || r.bottom>control.viewportHeight+1)
                    put(id,"Partly off the screen.")
                // Only real collisions: same screen, and never a container against something drawn inside it.
                controls.values.filter{it.id!=id && it.isAction && it.window==control.window && it.screen==control.screen &&
                    this@StudioControlsRuntime.style(it.id).visible}.forEach {other->
                    val b=other.bounds
                    val contains=r.left<=b.left && r.top<=b.top && r.right>=b.right && r.bottom>=b.bottom
                    val contained=b.left<=r.left && b.top<=r.top && b.right>=r.right && b.bottom>=r.bottom
                    val nested=(control.kind=="container-action" && r.contains(b.center)) || (other.kind=="container-action" && b.contains(r.center))
                    if(b.width>0 && r.overlaps(b) && !contains && !contained && !nested)put(id,"Overlaps ${other.title}.")
                }
            }
        }
        pendingHand?.advice()?.forEach{(id,why)->put("hand:$id",why)}
    }
}

internal val LocalStudioControls=compositionLocalOf<StudioControlsRuntime?>{null}
internal data class StudioControlParent(val id:String,val style:StudioControlStyle,val report:(String,String)->Unit,val proxy:Boolean=false,
    val boundCaption:String?=null)
internal val LocalStudioControlParent=compositionLocalOf<StudioControlParent?>{null}

/**
 * Owner note 12: plain names for panels that otherwise took their name from the first thing inside them (the opponent
 * panel was called "40", its life total). Keyed by code site; everything else keeps its own label.
 */
internal object StudioNames {
    private val names=mapOf(
        "TableBoardViews.combinedClickable.2" to "Player panel",
        "TableBoardViews.combinedClickable.3" to "Commander picture",
        "TableBoardViews.combinedClickable.4" to "Commander picture",
        "TableBoardViews.combinedClickable.5" to "Turn timer",
        "TableBoardViews.combinedClickable.7" to "Mana pool",
        "TableBoardViews.combinedClickable.8" to "Commander",
        "TableMainLayout.combinedClickable.1" to "Turn label",
    )
    fun of(id:String):String?=names[StudioEditorGeometry.siteOf(id)]

    /**
     * Owner's Studio log, Nov 7: dozens of items were just "Control" (tappable areas with no words of their own, such as
     * cards, seat tabs and stack tiles). These names are used only when an item would otherwise be called "Control";
     * an item with its own words keeps them.
     */
    private val fallbacks=mapOf(
        "TableBoardViews.combinedClickable.6" to "Trackers",
        "TableBoardViews.combinedClickable.9" to "Card",
        "TableBoardViews.combinedClickable.10" to "Card preview backdrop",
        "TableBoardViews.combinedClickable.11" to "Card preview",
        "ArgentumUi.clickable.1" to "Seat tab",
        "ArgentumUi.clickable.2" to "Game feed",
        "ArgentumUi.clickable.3" to "Needs your answer",
        "ArgentumUi.clickable.5" to "Action options",
        "ArgentumUi.clickable.6" to "Action options",
        "ArgentumCombatBoard.combinedClickable.1" to "Blocker",
        "CombatStackBoard.clickable.1" to "Combat step",
        "CombatStackBoard.clickable.2" to "Count",
        "CombatStackBoard.clickable.3" to "Stack pile",
        "CombatStackBoard.combinedClickable.1" to "Stack item",
        "ArgentumCardPick.combinedClickable.1" to "Card choice",
        "ArgentumCardPick.clickable.1" to "Card zoom",
        "TableHelpers.combinedClickable.1" to "Hand bar",
        "TableHelpers.combinedClickable.3" to "Player view tab",
        "BattlefieldLayout.clickable.1" to "Attached cards",
        "BattlefieldLayout.clickable.2" to "Attached cards",
        "PlayFeed.combinedClickable.1" to "Played cards tab",
        "PlayFeed.combinedClickable.2" to "Hide played cards",
        "PlayFeed.combinedClickable.3" to "Played card",
        "PlayFeed.combinedClickable.4" to "Ability banner",
        "PlayFeed.combinedClickable.5" to "Turn recap",
        "TableOverlaysB.clickable.1" to "Chat panel",
        "TurnSignals.clickable.1" to "Turn banner",
        "TableDialogs.combinedClickable.1" to "Card choice",
        "TableDialogs.clickable.2" to "Game log entry",
    )
    /**
     * Plain names for screens whose code name means nothing to the owner (log: "Table Dialogs 22"). Display only: the
     * code name stays inside item IDs, so saved edits keep working.
     */
    const val GAME_LOG_SCREEN="Table Dialogs 22"
    private val screens=mapOf(GAME_LOG_SCREEN to "Game log")
    fun screen(name:String):String=screens[name] ?: name
    fun isGameLog(screen:String?)=screen==GAME_LOG_SCREEN
    /** [title] unless it is the bare "Control" placeholder and this site has a plain name. */
    fun orFallback(id:String,title:String):String=if(title=="Control")fallbacks[StudioEditorGeometry.siteOf(id)] ?: title else title
}

/** Opacity steps of 10% from 0% (invisible but still there and tappable) to solid. Solid is stored as null so untouched items stay exactly as they were. */
internal object StudioOpacity {
    fun step(current:Float?,dir:Int):Float? {
        val pct=(((current ?: 1f)*100).let{kotlin.math.round(it/10f)*10f}+dir*10f).coerceIn(0f,100f)
        return if(pct>=100f)null else pct/100f
    }
}
