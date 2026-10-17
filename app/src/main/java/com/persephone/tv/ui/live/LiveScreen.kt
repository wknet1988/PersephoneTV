package com.persephone.tv.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persephone.tv.data.Channel
import com.persephone.tv.data.ContentRepository
import com.persephone.tv.data.LoadState
import com.persephone.tv.data.M3uParser
import com.persephone.tv.data.PlayTarget
import com.persephone.tv.ui.components.FocusCard
import com.persephone.tv.ui.components.LoadingPane
import com.persephone.tv.ui.components.MessagePane
import com.persephone.tv.ui.components.TvButton
import com.persephone.tv.ui.theme.Accent
import com.persephone.tv.ui.theme.AccentAmber
import com.persephone.tv.ui.theme.BgDeep
import com.persephone.tv.ui.theme.Surface1
import com.persephone.tv.ui.theme.Surface2
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextSecondary
import com.persephone.tv.ui.theme.TextTertiary
import com.persephone.tv.ui.theme.TvType

/** 直播页的一行：分组标题或频道 */
private sealed interface LiveRow {
    data class Header(val name: String) : LiveRow
    data class Item(val number: Int, val channel: Channel) : LiveRow
}

@Composable
fun LiveScreen(
    sourceUrl: String,
    onPlay: (PlayTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember(sourceUrl) { mutableStateOf<LoadState<List<Channel>>>(LoadState.Loading) }
    var retry by remember(sourceUrl) { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Channel?>(null) }

    LaunchedEffect(sourceUrl, retry) {
        state = LoadState.Loading
        val result = ContentRepository.loadLive(context, sourceUrl)
        state = result.fold(
            onSuccess = { list ->
                if (list.isEmpty()) LoadState.Error("播放列表里没有解析到频道") else LoadState.Success(list)
            },
            onFailure = { e -> LoadState.Error(e.message ?: e.javaClass.simpleName) }
        )
    }

    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            is LoadState.Loading, is LoadState.Idle -> LoadingPane("正在加载直播源…")
            is LoadState.Error -> MessagePane(
                title = "直播源加载失败",
                detail = s.message + "\n可在「设置」里更换 M3U 地址，或清空地址使用内置演示源。",
                actionLabel = "重试",
                onAction = { retry++ }
            )
            is LoadState.Success -> {
                LaunchedEffect(s.data) {
                    if (selected == null) selected = s.data.firstOrNull()
                }
                LiveContent(
                    channels = s.data,
                    selected = selected,
                    onSelect = { selected = it },
                    onPlay = onPlay
                )
            }
        }
    }
}

@Composable
private fun LiveContent(
    channels: List<Channel>,
    selected: Channel?,
    onSelect: (Channel) -> Unit,
    onPlay: (PlayTarget) -> Unit
) {
    val rows = remember(channels) {
        val out = ArrayList<LiveRow>()
        var n = 0
        M3uParser.group(channels).forEach { (group, list) ->
            out += LiveRow.Header(group)
            list.forEach { ch ->
                n += 1
                out += LiveRow.Item(n, ch)
            }
        }
        out
    }
    val groupCount = remember(rows) { rows.count { it is LiveRow.Header } }
    val listState = rememberLazyListState()
    val firstRequester = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(channels) {
        if (!focusedOnce) {
            focusedOnce = true
            runCatching { firstRequester.requestFocus() }
        }
    }

    Row(Modifier.fillMaxSize()) {
        // —— 左：频道列表 ——
        Column(
            Modifier
                .width(430.dp)
                .fillMaxHeight()
                .background(Surface1.copy(alpha = 0.42f))
                .padding(top = 26.dp)
        ) {
            Column(Modifier.padding(horizontal = 26.dp)) {
                Text("直播频道", style = TvType.Headline, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${channels.size} 个频道 · $groupCount 个分组",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rows, key = { row ->
                    when (row) {
                        is LiveRow.Header -> "h-${row.name}"
                        is LiveRow.Item -> "i-${row.number}-${row.channel.url}"
                    }
                }) { row ->
                    when (row) {
                        is LiveRow.Header -> Text(
                            text = row.name,
                            color = AccentAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp)
                        )
                        is LiveRow.Item -> ChannelRow(
                            channel = row.channel,
                            number = row.number,
                            isSelected = selected?.url == row.channel.url,
                            modifier = if (row.number == 1) Modifier.focusRequester(firstRequester) else Modifier,
                            onClick = {
                                onSelect(row.channel)
                                onPlay(row.channel.toPlayTarget())
                            },
                            onFocused = { onSelect(row.channel) }
                        )
                    }
                }
            }
        }

        // —— 右：预览 ——
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.linearGradient(listOf(BgDeep, Color(0xFF10161F), BgDeep)))
        ) {
            PreviewPanel(channel = selected, onPlay = onPlay)
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    number: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(9.dp),
        scaleOnFocus = 1.02f,
        focusedBorder = BorderStroke(2.dp, Accent)
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when {
                        focused -> Surface2
                        isSelected -> Surface1
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = number.toString().padStart(2, '0'),
                color = if (focused) Accent else TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(30.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channel.title,
                    color = if (focused) TextPrimary else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = channel.subtitle
                if (sub != null) {
                    Text(
                        text = sub,
                        color = if (focused) TextSecondary else TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Accent)
                )
            }
        }
    }
}

@Composable
private fun PreviewPanel(channel: Channel?, onPlay: (PlayTarget) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 56.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .clip(CircleShape)
                    .background(Surface1),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.14f))
                )
                Canvas(Modifier.size(46.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.28f, size.height * 0.12f)
                        lineTo(size.width * 0.28f, size.height * 0.88f)
                        lineTo(size.width * 0.9f, size.height * 0.5f)
                        close()
                    }
                    drawPath(path, Accent)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = channel?.title ?: "请选择频道",
                style = TvType.Display,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = channel?.subtitle
            if (sub != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = sub,
                    color = TextTertiary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("LIVE", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(text = channel?.group ?: "—", color = TextSecondary, fontSize = 13.sp)
            }
            if (channel != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = channel.url,
                    color = TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("按", color = TextTertiary, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface2)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("OK", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text("播放 · 上下切换频道", color = TextTertiary, fontSize = 13.sp)
            }
            if (channel != null) {
                Spacer(Modifier.height(24.dp))
                TvButton(
                    text = "开始播放",
                    onClick = { onPlay(channel.toPlayTarget()) }
                )
            }
        }
    }
}

/** 频道 -> 播放请求：标题用中文名，副标题拼上英文名和分组 */
private fun Channel.toPlayTarget(): PlayTarget {
    val sub = listOfNotNull(subtitle, group.ifBlank { null }).joinToString(" · ")
    return PlayTarget(
        title = title,
        subtitle = sub.ifBlank { null },
        url = url,
        isLive = true
    )
}
