package com.persephone.tv.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.persephone.tv.data.Episode
import com.persephone.tv.data.PlayTarget
import com.persephone.tv.data.VodItem
import com.persephone.tv.ui.components.FocusCard
import com.persephone.tv.ui.components.TvButton
import com.persephone.tv.ui.theme.Accent
import com.persephone.tv.ui.theme.AccentAmber
import com.persephone.tv.ui.theme.Bg
import com.persephone.tv.ui.theme.Surface1
import com.persephone.tv.ui.theme.Surface2
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextSecondary
import com.persephone.tv.ui.theme.TextTertiary
import com.persephone.tv.ui.theme.TvType

@Composable
fun DetailScreen(
    item: VodItem,
    onPlay: (PlayTarget) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(item.title) { runCatching { playRequester.requestFocus() } }

    val play: (Episode) -> Unit = { ep ->
        onPlay(
            PlayTarget(
                title = item.title,
                subtitle = if (item.episodes.size > 1) ep.name else item.category,
                url = ep.url,
                isLive = false
            )
        )
    }

    Box(modifier.fillMaxSize().background(Bg)) {
        // —— 背景：海报铺满 + 压暗 ——
        if (!item.poster.isNullOrBlank()) {
            AsyncImage(
                model = item.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Bg,
                        0.45f to Bg.copy(alpha = 0.94f),
                        0.75f to Bg.copy(alpha = 0.72f),
                        1f to Bg.copy(alpha = 0.55f)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Bg.copy(alpha = 0.55f),
                        1f to Bg
                    )
                )
        )

        // —— 内容 ——
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 64.dp, end = 64.dp, top = 54.dp, bottom = 40.dp)
        ) {
            Text(
                text = item.category,
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.title,
                style = TvType.Display,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.rating.isNullOrBlank()) {
                    Text(
                        text = item.rating,
                        color = AccentAmber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(16.dp))
                }
                if (!item.year.isNullOrBlank()) {
                    Text(item.year, color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    text = if (item.episodes.size > 1) "共 ${item.episodes.size} 集" else "单片",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            Spacer(Modifier.height(18.dp))
            if (!item.desc.isNullOrBlank()) {
                Text(
                    text = item.desc,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.62f)
                )
            }

            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val first = item.episodes.firstOrNull()
                TvButton(
                    text = "播放",
                    onClick = { first?.let(play) },
                    modifier = Modifier.focusRequester(playRequester)
                )
                Spacer(Modifier.width(14.dp))
                TvButton(text = "返回", onClick = onBack, primary = false)
            }

            Spacer(Modifier.weight(1f))

            if (item.episodes.size > 1) {
                Text(
                    text = "选集",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(item.episodes, key = { it.name + it.url }) { ep ->
                        EpisodeChip(ep = ep, onClick = { play(ep) })
                    }
                }
            }
        }

        // 右侧竖版海报，填补留白
        if (!item.poster.isNullOrBlank()) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 72.dp)
                    .width(268.dp)
                    .height(386.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun EpisodeChip(ep: Episode, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier
            .width(104.dp)
            .height(46.dp),
        shape = RoundedCornerShape(8.dp),
        scaleOnFocus = 1.06f,
        focusedBorder = BorderStroke(2.dp, Accent)
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(if (focused) Surface2 else Surface1),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ep.name,
                color = if (focused) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
