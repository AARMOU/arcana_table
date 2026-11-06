package com.arcanatable

import android.content.SharedPreferences
import androidx.compose.runtime.*

/** Select a draft without changing the live game's active profile until Apply. */
@Composable
internal fun NativeStudioWorkspace(prefs: SharedPreferences, orientation: String, width: Int, height: Int,
    cards: List<GameCard>, onApplied: () -> Unit, onDismiss: () -> Unit, onViewGame: (() -> Unit)? = null) {
    var editingOrientation by remember {mutableStateOf(orientation)}
    var profile by remember(editingOrientation) {mutableStateOf(StudioProfiles(prefs,editingOrientation).activeName())}
    var revision by remember {mutableIntStateOf(0)}
    val profiles=remember(editingOrientation,revision){StudioProfiles(prefs,editingOrientation)}
    val store=remember(editingOrientation,profile,revision){StudioStore(prefs,editingOrientation,profile)}
    val canvasWidth=if((editingOrientation=="landscape")== (width>height))width else height
    val canvasHeight=if((editingOrientation=="landscape")== (width>height))height else width
    val nativeControls=LocalStudioControls.current
    NativeStudioPage(store,cards,onApplied,onDismiss,
        initialLayout=StudioLayout.responsive(canvasWidth.toFloat(),canvasHeight.toFloat(),profile),
        profileNames=profiles.names(),onProfile={profile=it},
        onOrientation={editingOrientation=it},
        onCopyProfile={name,layout ->profiles.copyProfile(profile,name,layout);profile=name;revision++},
        onResetAll={profiles.resetAll();profile="Default";revision++;onApplied()},
        onViewGame=onViewGame,
        onOtherScreens=nativeControls?.let {controls->({onDismiss();controls.begin()})})
}
