package com.persephone.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// —— 调色板（电视端：深色底 + 高对比文字）——
val Bg = Color(0xFF0A0C11)
val BgDeep = Color(0xFF06080C)
val Surface1 = Color(0xFF151A22)
val Surface2 = Color(0xFF1F2733)
val Surface3 = Color(0xFF2B3542)
val Accent = Color(0xFFFF4D4F)
val AccentDark = Color(0xFFB32226)
val AccentAmber = Color(0xFFFFB020)
val TextPrimary = Color(0xFFF3F6FA)
val TextSecondary = Color(0xFF9AA6B2)
val TextTertiary = Color(0xFF6C7885)
val Divider = Color(0xFF262F3B)

private val TvColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDark,
    onPrimaryContainer = Color.White,
    secondary = AccentAmber,
    onSecondary = Color(0xFF241800),
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Divider
)

/** 电视端基础字号：比手机大一号，保证 3 米外可读 */
object TvType {
    val Display = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    val Headline = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    val Title = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp)
    val Label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
}

@Composable
fun PersephoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
