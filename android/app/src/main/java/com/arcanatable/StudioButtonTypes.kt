package com.arcanatable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Owner note 26: a short list of button looks in Arcana's own style (squared, gold on dark, not round), picked from a
 * list in the Style tab. A look sets shape, colours and edge together; a fill, text colour or corner the owner sets by
 * hand still wins over the look. Colours come from the table palette, so a custom palette restyles them too.
 */
internal object StudioButtonTypes {
    data class Look(val shape:Shape,val fill:(TableColors)->Color,val ink:(TableColors)->Color,val edge:((TableColors)->BorderStroke)?)

    // The five approved on #995 (note 26), each copying a button already in the app.
    val looks:Map<String,Look> = linkedMapOf(
        "Action" to Look(RoundedCornerShape(6.dp),{it.accent},{it.background},null),                                      // solid teal, like PASS
        "Panel" to Look(RoundedCornerShape(6.dp),{it.panel},{it.text},{BorderStroke(1.dp,it.accent)}),                     // dark with an outline, like CAST
        "Counter" to Look(RoundedCornerShape(4.dp),{it.raised},{it.text},{BorderStroke(1.dp,it.muted.copy(alpha=0.5f))}),  // like LIBRARY
        "Phase tab" to Look(RoundedCornerShape(4.dp),{it.counter.copy(alpha=0.18f)},{it.counter},{BorderStroke(1.dp,it.counter.copy(alpha=0.6f))}), // like MAIN 1
        "Tray bar" to Look(RoundedCornerShape(topStart=10.dp,topEnd=10.dp,bottomStart=2.dp,bottomEnd=2.dp),{it.panel},{it.counter},{BorderStroke(1.dp,it.counter.copy(alpha=0.3f))}), // like HAND 7
    )
    val names get()=looks.keys

    /** Shape, colours and edge for a button with this [style], falling back to the button's own values. */
    fun apply(style:StudioControlStyle?,palette:TableColors,shape:Shape,colors:ButtonColors,border:BorderStroke?):Triple<Shape,ButtonColors,BorderStroke?> {
        if(style==null)return Triple(shape,colors,border)
        val look=style.type?.let{looks[it]}
        val fill=style.fill?.let(::parseTableColorHex) ?: look?.fill?.invoke(palette) ?: colors.containerColor
        val ink=style.ink?.let(::parseTableColorHex) ?: look?.ink?.invoke(palette) ?: colors.contentColor
        val outShape=style.radius?.let{RoundedCornerShape(it.coerceIn(0f,100f).dp)} ?: look?.shape ?: shape
        val edge=if(look!=null)look.edge?.invoke(palette) else border
        return Triple(outShape,colors.copy(containerColor=fill,contentColor=ink),edge)
    }
}
