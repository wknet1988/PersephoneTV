package com.persephone.tv.ui.finder

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persephone.tv.data.Channel
import com.persephone.tv.data.PlayTarget
import com.persephone.tv.data.SourceCatalog
import com.persephone.tv.data.SourceEntry
import com.persephone.tv.data.SourceFinder
import com.persephone.tv.ui.components.FocusCard
import com.persephone.tv.ui.components.TvButton
import com.persephone.tv.ui.theme.Accent
import com.persephone.tv.ui.theme.AccentAmber
import com.persephone.tv.ui.theme.Divider
import com.persephone.tv.ui.theme.Surface1
import com.persephone.tv.ui.theme.Surface2
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextSecondary
import com.persephone.tv.ui.theme.TextTertiary
import com.persephone.tv.ui.theme.TvType
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF3DD68C)

private sealed interface SourceState {
    data object Idle : SourceState
    data object Probing : SourceState
    data class Ready(val channels: List<Channel>, val latencyMs: Long) : SourceState
    data class Failed(val message: String) : SourceState
}

/**
 * 找源：一键联网检索公开直播源。
 *
 * 流程：拉取候选源列表 → 并发下载 + 解析 → 按耗时排序 → 选中源即可预览频道、
 * 直接试播，或一键设为当前直播源。
 */
@Composable
fun FindSourceScreen(
    customSources: String,
    onImport: (SourceEntry) -> Unit,
    onPlay: (PlayTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = remember(customSources) { SourceCatalog.withCustom(customSources) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var states by remember(entries) {
        mutableStateOf(
            entries.associate { e ->
                val cached = SourceFinder.cached(e.id)
                e.id to if (cached == null) {
                    SourceState.Idle
                } else if (cached.ok) {
                    SourceState.Ready(cached.channels, cached.latencyMs)
                } else {
                    SourceState.Failed(cached.error ?: "检索失败")
                }
            }
        )
    }
    var selectedId by remember(entries) { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    fun searchAll() {
        if (searching) return
        searching = true
        states = entries.associate { it.id to (SourceState.Probing as SourceState) }
        scope.launch {
            SourceFinder.probeAll(context, entries) { result ->
                SourceFinder.remember(result)
                states = states + (
                    result.entry.id to if (result.ok) {
                        SourceState.Ready(result.channels, result.latencyMs)
                    } else {
                        SourceState.Failed(result.error ?: "检索失败")
                    }
                    )
                if (selectedId == null && result.ok) selectedId = result.entry.id
            }
            searching = false
        }
    }

    // 进入页面自动联网检索（有缓存则直接用缓存结果）
    LaunchedEffect(entries) {
        if (entries.none { SourceFinder.cached(it.id) != null }) searchAll()
        else if (selectedId == null) {
            selectedId = entries.firstOrNull { (states[it.id] as? SourceState.Ready) != null }?.id
        }
    }

    val selectedEntry = entries.firstOrNull { it.id == selectedId }
    // 包内清单不用联网拉，耗时文案换成「播放仍需联网」
    val selectedIsBuiltin = selectedEntry?.isAsset == true
    val selectedState = selectedEntry?.let { states[it.id] }
    val allChannels = (selectedState as? SourceState.Ready)?.channels.orEmpty()
    val shownChannels = remember(allChannels, keyword) { SourceFinder.search(allChannels, keyword) }

    Row(modifier.fillMaxSize().padding(start = 46.dp, end = 46.dp, top = 28.dp, bottom = 26.dp)) {
        // —— 左：候选源 ——
        Column(Modifier.width(430.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("找源", style = TvType.Display, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${entries.size} 个源 · 包内清单 + 联网检索",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
                TvButton(
                    text = if (searching) "检索中…" else "重新检索",
                    onClick = { searchAll() }
                )
            }

            Spacer(Modifier.height(18.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    SourceRow(
                        entry = entry,
                        state = states[entry.id] ?: SourceState.Idle,
                        selected = entry.id == selectedId,
                        onClick = {
                            when (states[entry.id]) {
                                is SourceState.Ready -> onImport(entry)
                                else -> {
                                    selectedId = entry.id
                                    scope.launch {
                                        states = states + (entry.id to (SourceState.Probing as SourceState))
                                        val r = SourceFinder.probe(context, entry)
                                        SourceFinder.remember(r)
                                        states = states + (
                                            entry.id to if (r.ok) {
                                                SourceState.Ready(r.channels, r.latencyMs)
                                            } else {
                                                SourceState.Failed(r.error ?: "检索失败")
                                            }
                                            )
                                    }
                                }
                            }
                        },
                        onFocused = { selectedId = entry.id }
                    )
                }
            }
        }

        Spacer(Modifier.width(26.dp))

        // —— 右：频道 ——
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = selectedEntry?.name ?: "选择一个源",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (val s = selectedState) {
                            is SourceState.Ready -> if (selectedIsBuiltin) {
                                "${s.channels.size} 个频道 · 清单随包附带，播放需联网"
                            } else {
                                "${s.channels.size} 个频道 · 拉取耗时 ${s.latencyMs} ms"
                            }
                            is SourceState.Probing -> "正在联网检索…"
                            is SourceState.Failed -> s.message
                            else -> selectedEntry?.note ?: "按 OK 开始检索"
                        },
                        color = TextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selectedEntry != null && selectedState is SourceState.Ready) {
                    TvButton(text = "设为直播源", onClick = { onImport(selectedEntry) })
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text("按频道名过滤，例如 CCTV、体育", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.fillMaxWidth(0.62f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Surface1,
                    unfocusedContainerColor = Surface1,
                    cursorColor = Accent,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Divider,
                    focusedPlaceholderColor = TextTertiary,
                    unfocusedPlaceholderColor = TextTertiary
                )
            )

            Spacer(Modifier.height(14.dp))
            when (val s = selectedState) {
                is SourceState.Ready -> {
                    if (shownChannels.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("没有匹配「$keyword」的频道", color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(shownChannels, key = { it.url }) { ch ->
                                ChannelRow(
                                    channel = ch,
                                    onClick = {
                                        val sub = listOfNotNull(ch.subtitle, ch.group.ifBlank { null })
                                            .joinToString(" · ")
                                        onPlay(
                                            PlayTarget(
                                                title = ch.title,
                                                subtitle = sub.ifBlank { null },
                                                url = ch.url,
                                                isLive = true,
                                                headers = ch.headers
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                is SourceState.Probing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在下载并解析播放列表…", color = TextSecondary, fontSize = 14.sp)
                }

                is SourceState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("这个源没拉到", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, color = TextSecondary, fontSize = 13.sp)
                    }
                }

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "左侧选中一个源，按 OK 联网检索",
                        color = TextTertiary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    entry: SourceEntry,
    state: SourceState,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RoundedCornerShape(10.dp),
        scaleOnFocus = 1.02f,
        focusedBorder = BorderStroke(2.dp, Accent)
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        focused -> Surface2
                        selected -> Surface1
                        else -> Color.Transparent
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (selected || focused) 34.dp else 12.dp)
                    .background(if (selected || focused) Accent else Color.Transparent)
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 10.dp)
            ) {
                Text(
                    text = entry.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when (state) {
                        is SourceState.Failed -> state.message
                        is SourceState.Ready -> entry.note
                        else -> entry.note
                    },
                    color = if (state is SourceState.Failed) Accent else TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusChip(state)
            Spacer(Modifier.width(10.dp))
        }
    }
}

@Composable
private fun StatusChip(state: SourceState) {
    val (text, color) = when (state) {
        is SourceState.Idle -> "未检索" to TextTertiary
        is SourceState.Probing -> "检索中" to AccentAmber
        is SourceState.Ready -> "${state.channels.size} 频道" to OkGreen
        is SourceState.Failed -> "失败" to Accent
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChannelRow(channel: Channel, onClick: () -> Unit) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(8.dp),
        scaleOnFocus = 1.01f,
        focusedBorder = BorderStroke(2.dp, Accent)
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(if (focused) Surface2 else Color.Transparent)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (focused) Accent else Divider)
            )
            Spacer(Modifier.width(12.dp))
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
            Text(
                text = channel.group,
                color = TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}
