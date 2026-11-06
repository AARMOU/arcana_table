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
) {
    fun problems()=buildList {
        if(listOfNotNull(dx,dy,width,height,font,radius,layer).any {!it.isFinite()})add("Use finite dimensions.")
        if(kotlin.math.abs(dx)>2000 || kotlin.math.abs(dy)>4000)add("Offset exceeds the canvas.")
        if(width!=null && width !in 44f..2000f || height!=null && height !in 44f..4000f)add("Controls need dimensions of at least 44dp.")
        if(font!=null && font !in 8f..64f || radius!=null && radius !in 0f..100f)add("Invalid font or corner radius.")
        if(fill!=null && parseTableColorHex(fill)==null || ink!=null && parseTableColorHex(ink)==null)add("Use #RRGGBB colors.")
        if(label!=null && label.length>120)add("Label is too long.")
        if(binding!=null && (binding.length>180 || !binding.startsWith("native:") || !StudioSites.contains(binding)))add("Unknown native binding.")
        if(icon !in StudioLayout.icons)add("Unknown icon.")
        if(layer !in -100f..100f)add("Layer must be between -100 and 100.")
    }
    fun json()=JSONObject().put("dx",dx).put("dy",dy).put("visible",visible).put("icon",icon).put("layer",layer).apply {
        width?.let{put("width",it)};height?.let{put("height",it)};font?.let{put("font",it)};radius?.let{put("radius",it)}
        fill?.let{put("fill",it)};ink?.let{put("ink",it)};label?.let{put("label",it)};binding?.let{put("binding",it)}
    }
    companion object {
        fun parse(o:JSONObject):StudioControlStyle {
            fun number(k:String)=if(o.has(k))o.getDouble(k).toFloat() else null
            fun text(k:String)=if(o.has(k))o.getString(k) else null
            return StudioControlStyle(o.optDouble("dx",0.0).toFloat(),o.optDouble("dy",0.0).toFloat(),number("width"),number("height"),
                number("font"),number("radius"),text("fill"),text("ink"),text("label"),o.optBoolean("visible",true),text("binding"),
                o.optString("icon","None"),o.optDouble("layer",0.0).toFloat()).also {require(it.problems().isEmpty()){it.problems().joinToString("\n")}}
        }
    }
}

internal data class StudioControlCopy(val id:String,val source:String,val style:StudioControlStyle)
internal data class StudioControlProject(val controls:Map<String,StudioControlStyle> = emptyMap(),
    val copies:List<StudioControlCopy> = emptyList(),val density:String="Balanced",val theme:Map<String,String> = emptyMap(),
    val owners:Map<String,String> = emptyMap(),val screenDensity:Map<String,String> = emptyMap()) {
    fun colors(base:TableColors)=StudioLayout(emptyList(),theme=theme).colors(base)
    fun json()=JSONObject().put("version",1).put("renderer","native-flow").put("density",density).put("theme",JSONObject(theme)).put("owners",JSONObject(owners)).put("screenDensity",JSONObject(screenDensity)).put("controls",JSONObject().apply {
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
            return StudioControlProject(controls.keys().asSequence().associateWith {id->
                require(id.startsWith("native:") && id.length<=180 && StudioSites.contains(id)){"Unknown control ID."}
                StudioControlStyle.parse(controls.getJSONObject(id))
            },parsedCopies,density,theme,owners,densities)
        }
    }
}

internal class StudioControlStore(private val prefs:SharedPreferences,val orientation:String,val profile:String) {
    private val key="studio_controls_v1_${orientation}_$profile"
    fun load():StudioControlProject=listOf(key,"${key}_backup").firstNotNullOfOrNull {k->prefs.getString(k,null)?.let{runCatching{StudioControlProject.parse(it)}.getOrNull()}} ?: StudioControlProject(density=profile.takeIf{it in listOf("Minimal","Balanced","Detailed")} ?: "Balanced")
    fun draft()=prefs.getString("${key}_draft",null)?.let{runCatching{StudioControlProject.parse(it)}.getOrNull()}
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
internal val StudioLiveControl.requiredNativeAction get()=id.startsWith("native:") && isAction && kind!="card-action" &&
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
    fun release(base:String,token:Any){claims[base]?.let{it.remove(token);if(it.isEmpty())claims.remove(base)}}
    var editing by mutableStateOf(false);private set
    private var capturing by mutableStateOf(false)
    var panelOpen by mutableStateOf(false)
    var selected by mutableStateOf<String?>(null)
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
        val next=info.copy(title=if(info.kind=="container-action" && info.title=="Control")previous?.title ?: info.title else info.title,
            bounds=previous?.bounds ?: info.bounds,window=info.window.takeIf{it!=0} ?: previous?.window ?: 0,
            viewportWidth=previous?.viewportWidth ?: info.viewportWidth,viewportHeight=previous?.viewportHeight ?: info.viewportHeight)
        if(previous!=next)controls[info.id]=next
        if(callback!=null)callbacks[info.id]=callback else callbacks.remove(info.id)
        captions.filterValues{it==info.id}.keys.forEach {id->controls[id]?.let{if(it.title!=info.title)controls[id]=it.copy(title=info.title)}}
    }
    fun remove(id:String){controls.remove(id);callbacks.remove(id);captions.remove(id);liveText.remove(id)}
    fun bounds(id:String,rect:Rect,window:Int,width:Float=0f,height:Float=0f){controls[id]?.let {
        val child=if(it.kind=="container-action" && !it.captionEditable) controls.values.filter {c->c.id!=id && !c.isAction && c.window==window && c.bounds.width>0 && rect.contains(c.bounds.center)}.minByOrNull{c->c.bounds.top} else null
        child?.let{c->captions[id]=c.id}
        val next=it.copy(title=child?.title ?: it.title,bounds=rect,window=window,viewportWidth=width,viewportHeight=height)
        if(next!=it)controls[id]=next
    }}
    fun project()=if(editing)draft else applied
    fun style(id:String,screen:String?=null,kind:String?=null):StudioControlStyle {
        if(!editing && id in recovered)return StudioControlStyle()
        val project=project()
        val style=project.controls[id] ?: project.copies.firstOrNull{it.id==id}?.style ?: StudioControlStyle(
            visible=!((project.screenDensity[screen ?: controls[id]?.screen] ?: project.density)=="Minimal" && (kind ?: controls[id]?.kind)=="data"))
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
    fun begin(id:String?=null){capturing=true;draft=store().draft() ?: applied;pendingHand=if(prefs.getBoolean(pendingHandKey,false))StudioStore(prefs,orientation,profile).draft() else null;undo.clear();redo.clear();editing=true;selected=id;panelOpen=true}
    fun select(id:String){if(!editing)begin(id) else {selected=id;panelOpen=true}}
    fun inspectNativeScreen(){editing=true;panelOpen=false}
    private fun saveDraft() {
        store().saveDraft(draft)
        pendingHand?.let{StudioStore(prefs,orientation,profile).saveDraft(it)}
        prefs.edit().putBoolean(pendingHandKey,pendingHand!=null).apply()
    }
    fun change(project:StudioControlProject,hand:StudioLayout?=pendingHand){if(project!=draft || hand!=pendingHand){
        undo.addLast(Revision(draft,pendingHand));if(undo.size>100)undo.removeFirst();redo.clear();draft=project;pendingHand=hand;saveDraft();revision++
    }}
    fun edit(id:String,style:StudioControlStyle) {
        if(id.startsWith("copy:"))change(draft.copy(copies=draft.copies.map{if(it.id==id)it.copy(style=style) else it}))
        else change(draft.copy(controls=draft.controls+(id to style),owners=controls[id]?.screen?.let{draft.owners+(id to it)} ?: draft.owners))
    }
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
    fun undo(){if(undo.isNotEmpty()){redo.addLast(Revision(draft,pendingHand));val previous=undo.removeLast();draft=previous.controls;pendingHand=previous.hand;saveDraft();revision++}}
    fun redo(){if(redo.isNotEmpty()){undo.addLast(Revision(draft,pendingHand));val next=redo.removeLast();draft=next.controls;pendingHand=next.hand;saveDraft();revision++}}
    fun cancel(){prefs.edit().remove(pendingHandKey).apply();pendingHand=null;profile=StudioProfiles(prefs,orientation).activeName();applied=store().load();draft=applied;editing=false;capturing=false;panelOpen=false;revision++}
    fun apply() {
        val errors=problems();require(errors.isEmpty()){errors.joinToString("\n")}
        val edit=store().stage(prefs.edit(),draft)
        pendingHand?.let{StudioStore(prefs,orientation,profile).stage(edit,it)}
        StudioProfiles(prefs,orientation).stageActive(edit,profile)
        check(edit.remove(pendingHandKey).commit()){"Could not save project."}
        pendingHand=null
        applied=draft;editing=false;capturing=false;panelOpen=false;recovered.clear();revision++
    }
    fun chooseProfile(name:String){require(name in StudioProfiles(prefs,orientation).names());profile=name;applied=store().load();draft=store().draft() ?: applied;editing=true;undo.clear();redo.clear();revision++}
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
    fun resetProfile(){store().reset();StudioStore(prefs,orientation,profile).reset();prefs.edit().remove(pendingHandKey).commit();pendingHand=null;applied=StudioControlProject();draft=applied;recovered.clear();revision++}
    fun exportProject():String=JSONObject().put("version",3).put("renderer","arcana-native").put("orientation",orientation)
        .put("controls",JSONObject(draft.json())).apply {
            (pendingHand ?: StudioStore(prefs,orientation,profile).load())?.let{put("hand",JSONObject(it.json()))}
        }.toString()
    fun importProject(text:String) {
        require(text.length in 1..5_000_000){"Project is empty or too large."}
        val root=JSONObject(text)
        if(root.optString("renderer")=="native-flow"){change(StudioControlProject.parse(text));return}
        require(root.getInt("version")==3 && root.getString("renderer")=="arcana-native"){"Unsupported project."}
        require(root.getString("orientation")==orientation){"Select the project's orientation before importing."}
        val controls=StudioControlProject.parse(root.getJSONObject("controls").toString())
        val hand=root.optJSONObject("hand")?.let{StudioLayout.parse(it.toString())}
        change(controls,hand)
    }
    fun problems():List<String> = buildList {
        if(draft.theme.keys.any{it !in StudioLayout.themeRoles} || draft.theme.values.any{parseTableColorHex(it)==null})add("Invalid palette roles or colors.")
        pendingHand?.let{addAll(it.problems())}
        (draft.controls+draft.copies.associate{it.id to it.style}).forEach {(id,style)->
            addAll(style.problems().map{"${controls[id]?.title ?: "Control"}: $it"})
            val control=controls[id]
            if(control?.requiredNativeAction==true && (!style.visible || style.binding!=null && style.binding!=id) && !hasReplacement(draft,id))
                add("Keep required action ${control.title} reachable.")
            if(style.binding!=null && control!=null && !(if(control.isAction)canInvoke(id,style.binding) else canDisplay(id,style.binding)))
                add("${control.title}: choose an available ${if(control.isAction)"action" else "information source"} on the same screen.")
            if(control?.protectedLabel==true && style.label!=null)add("Dynamic game data cannot be replaced by a fixed label.")
            if(control?.kind=="container-action" && !control.captionEditable && style.label!=null)add("Edit this control's text layer to rename it.")
            if(control?.kind=="container-action" && !control.captionEditable && style.binding!=null)add("Duplicate this compound control as a button to change its action.")
            if(control?.isAction==true && style.visible && control.bounds.width>0 && control.viewportWidth>0) {
                val r=control.bounds
                if(r.left< -1 || r.top< -1 || r.right>control.viewportWidth+1 || r.bottom>control.viewportHeight+1)
                    add("${control.title} is outside its native window.")
                controls.values.filter{it.id!=id && it.isAction && it.window==control.window && this@StudioControlsRuntime.style(it.id).visible}.forEach {other->
                    val b=other.bounds
                    val contains=r.left<=b.left && r.top<=b.top && r.right>=b.right && r.bottom>=b.bottom
                    val contained=b.left<=r.left && b.top<=r.top && b.right>=r.right && b.bottom>=r.bottom
                    if(b.width>0 && r.overlaps(b) && !contains && !contained)add("${control.title} overlaps ${other.title}.")
                }
            }
        }
    }
}

internal val LocalStudioControls=compositionLocalOf<StudioControlsRuntime?>{null}
internal data class StudioControlParent(val id:String,val style:StudioControlStyle,val report:(String,String)->Unit,val proxy:Boolean=false,
    val boundCaption:String?=null)
internal val LocalStudioControlParent=compositionLocalOf<StudioControlParent?>{null}
