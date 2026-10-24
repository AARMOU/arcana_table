package com.arcanatable

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Big permanent developer mascot; compiled into ALL Android debug variants, not release. */
@Composable
internal fun DevBuildGooseBanner() {
    val short = LocalConfiguration.current.screenHeightDp < 520
    val yellow = Color(0xFFFFD232)
    val dark = Color(0xFF16231F)
    Row(
        Modifier.fillMaxWidth().height(if (short) 100.dp else 176.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color(0xFF23342E))
            .border(1.dp, yellow, RoundedCornerShape(18.dp))
            .testTag("dev-goose-banner").padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(if (short) 144.dp else 198.dp)
            .fillMaxHeight().testTag("dev-goose-art")) {
            withTransform({ scale(size.width / 220f, size.height / 170f, Offset.Zero) }) {
                val white = Color(0xFFFFFAEC)
                val line = Color(0xFF444A3B)
                val body = Path().apply {
                    moveTo(39f, 170f)
                    cubicTo(22f, 134f, 51f, 119f, 100f, 125f)
                    cubicTo(145f, 114f, 177f, 145f, 175f, 171f)
                    close()
                }
                drawPath(body, white)
                drawPath(body, line, style = Stroke(3f))
                val wing = Path().apply {
                    moveTo(60f, 154f); cubicTo(7f, 132f, 23f, 101f, 55f, 119f)
                    cubicTo(81f, 134f, 81f, 142f, 99f, 145f); close()
                }
                drawPath(wing, Color(0xFFE4E7D9))
                drawPath(wing, line, style = Stroke(2f))
                val neck = Path().apply {
                    moveTo(91f, 170f)
                    cubicTo(101f, 142f, 93f, 126f, 82f, 103f)
                    cubicTo(58f, 63f, 85f, 42f, 125f, 49f)
                    cubicTo(168f, 51f, 171f, 89f, 153f, 121f)
                    cubicTo(141f, 145f, 146f, 156f, 159f, 171f); close()
                }
                drawPath(neck, white)
                drawPath(neck, line, style = Stroke(3f))
                drawOval(dark, Offset(139f, 78f), Size(11f, 19f))
                drawCircle(Color.White, 2.5f, Offset(143f, 83f))
                val beak = Path().apply {
                    moveTo(144f, 104f)
                    cubicTo(168f, 99f, 192f, 108f, 207f, 115f)
                    cubicTo(177f, 147f, 154f, 130f, 144f, 110f); close()
                }
                drawPath(beak, Color(0xFFFFA327))
                drawPath(beak, line, style = Stroke(2.5f))
                val openMouth = Path().apply {
                    moveTo(155f, 118f); cubicTo(174f, 128f, 194f, 118f, 206f, 115f)
                    cubicTo(189f, 137f, 167f, 137f, 155f, 118f); close()
                }
                drawPath(openMouth, Color(0xFF90312C))
                // The hard hat has its OWN DEV BUILD lettering.
                val helmet = Path().apply {
                    moveTo(65f, 72f)
                    cubicTo(67f, 25f, 92f, 13f, 118f, 14f)
                    cubicTo(149f, 14f, 167f, 40f, 170f, 72f); close()
                }
                drawPath(helmet, yellow)
                drawPath(helmet, line, style = Stroke(3f))
                drawLine(Color(0xFFFFE785), Offset(107f, 17f), Offset(102f, 69f), 7f)
                drawRoundRect(Color(0xFFFFDF5D), Offset(56f, 65f), Size(130f, 19f), CornerRadius(7f))
                drawRoundRect(line, Offset(56f, 65f), Size(130f, 19f), CornerRadius(7f), style = Stroke(2f))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textSize = 15f
                }
                drawContext.canvas.nativeCanvas.drawText("DEV BUILD", 120f, 57f, paint)
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 2.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("DEV BUILD", color = yellow,
                fontSize = if (short) 16.sp else 25.sp,
                lineHeight = if (short) 18.sp else 27.sp,
                fontWeight = FontWeight.Black)
            Text("HONK! HARD HATS ON", color = Color(0xFFFFFAEC),
                fontSize = if (short) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold)
            if (!short) Text("Not the production app", color = Color(0xFFB9CCC2), fontSize = 11.sp)
        }
    }
}
