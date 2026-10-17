package com.persephone.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.persephone.tv.data.PlayTarget
import com.persephone.tv.data.SettingsStore
import com.persephone.tv.data.VodItem
import com.persephone.tv.ui.components.FocusCard
import com.persephone.tv.ui.detail.DetailScreen
import com.persephone.tv.ui.finder.FindSourceScreen
import com.persephone.tv.ui.live.LiveScreen
import com.persephone.tv.ui.player.PlayerScreen
import com.persephone.tv.ui.settings.SettingsScreen
import com.persephone.tv.ui.theme.Accent
import com.persephone.tv.ui.theme.Bg
import com.persephone.tv.ui.theme.BgDeep
import com.persephone.tv.ui.theme.Surface1
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextSecondary
import com.persephone.tv.ui.theme.TextTertiary
import com.persephone.tv.ui.theme.PersephoneTheme
import com.persephone.tv.ui.vod.VodScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PersephoneTheme {
                RootScreen()
            }
        }
    }
}

private enum class Tab(val label: String) {
    Live("直播"),
    Vod("影视"),
    Finder("找源"),
    Settings("设置")
}

@Composable
private fun RootScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    val liveUrl by store.liveUrl.collectAsStateWithLifecycle(initialValue = "")
    val catalogUrl by store.catalogUrl.collectAsStateWithLifecycle(initialValue = "")
    val customSources by store.customSources.collectAsStateWithLifecycle(initialValue = "")

    var tab by remember { mutableStateOf(Tab.Live) }
    var detail by remember { mutableStateOf<VodItem?>(null) }
    var target by remember { mutableStateOf<PlayTarget?>(null) }

    // 播放器关闭后，焦点回到侧边栏当前项，避免遥控器「失焦」
    val railRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { railRequester.requestFocus() } }
    LaunchedEffect(target) {
        if (target == null) runCatching { railRequester.requestFocus() }
    }

    BackHandler(enabled = target != null) { target = null }
    BackHandler(enabled = target == null && detail != null) { detail = null }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Row(Modifier.fillMaxSize()) {
            SideRail(
                current = tab,
                focusRequester = railRequester,
                onSelect = {
                    tab = it
                    detail = null
                }
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val openedItem = detail
                when {
                    openedItem != null -> DetailScreen(
                        item = openedItem,
                        onPlay = { target = it },
                        onBack = { detail = null }
                    )

                    tab == Tab.Live -> LiveScreen(
                        sourceUrl = liveUrl,
                        onPlay = { target = it }
                    )

                    tab == Tab.Vod -> VodScreen(
                        sourceUrl = catalogUrl,
                        onOpen = { detail = it }
                    )

                    tab == Tab.Finder -> FindSourceScreen(
                        customSources = customSources,
                        onImport = { entry ->
                            scope.launch { store.setLiveSource(entry.url) }
                            tab = Tab.Live
                        },
                        onPlay = { target = it }
                    )

                    else -> SettingsScreen(
                        liveUrl = liveUrl,
                        catalogUrl = catalogUrl,
                        customSources = customSources,
                        onSave = { live, catalog, custom ->
                            scope.launch { store.save(live, catalog, custom) }
                        },
                        onReset = { scope.launch { store.reset() } }
                    )
                }
            }
        }

        // 播放器覆盖整个界面（含侧边栏）
        val playing = target
        if (playing != null) {
            PlayerScreen(
                target = playing,
                onExit = { target = null },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SideRail(
    current: Tab,
    focusRequester: FocusRequester,
    onSelect: (Tab) -> Unit
) {
    Column(
        Modifier
            .width(112.dp)
            .fillMaxHeight()
            .background(BgDeep)
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 品牌标记
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(15.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.16f, 0f)
                    lineTo(size.width * 0.16f, size.height)
                    lineTo(size.width, size.height * 0.5f)
                    close()
                }
                drawPath(path, Color.White)
            }
        }
        Spacer(Modifier.height(34.dp))

        Tab.values().forEach { item ->
            RailItem(
                label = item.label,
                selected = current == item,
                modifier = if (current == item) Modifier.focusRequester(focusRequester) else Modifier,
                onClick = { onSelect(item) }
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.weight(1f))
        Text("v1.0.1", color = TextTertiary, fontSize = 10.sp)
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier
            .width(84.dp)
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        scaleOnFocus = 1.06f,
        focusedBorder = BorderStroke(2.dp, Color.White)
    ) { focused ->
        Row(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        focused -> Surface1
                        selected -> Surface1.copy(alpha = 0.62f)
                        else -> Color.Transparent
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (selected || focused) 26.dp else 10.dp)
                    .background(if (selected || focused) Accent else Color.Transparent)
            )
            Box(
                Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (focused || selected) TextPrimary else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
