package com.persephone.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.persephone.tv.ui.components.SectionTitle
import com.persephone.tv.ui.components.TvButton
import com.persephone.tv.ui.theme.Accent
import com.persephone.tv.ui.theme.Divider
import com.persephone.tv.ui.theme.Surface1
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextSecondary
import com.persephone.tv.ui.theme.TextTertiary
import com.persephone.tv.ui.theme.TvType

@Composable
fun SettingsScreen(
    liveUrl: String,
    catalogUrl: String,
    customSources: String,
    onSave: (String, String, String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var live by remember(liveUrl) { mutableStateOf(liveUrl) }
    var catalog by remember(catalogUrl) { mutableStateOf(catalogUrl) }
    var custom by remember(customSources) { mutableStateOf(customSources) }
    var savedHint by remember { mutableStateOf(false) }

    val liveFieldRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { liveFieldRequester.requestFocus() } }

    Row(modifier.fillMaxSize().padding(start = 54.dp, end = 54.dp, top = 26.dp, bottom = 22.dp)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Text("设置", style = TvType.Display, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "配置自己的片源。留空则使用内置演示数据，改完记得保存。",
                color = TextTertiary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(26.dp))
            SectionTitle(text = "直播源（M3U / M3U8）")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = live,
                onValueChange = { live = it; savedHint = false },
                label = { Text("M3U 地址") },
                placeholder = { Text("https://example.com/live.m3u") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(liveFieldRequester),
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "支持 http(s) 链接、file:// 或绝对路径；支持 #EXTINF 的 group-title / tvg-logo。",
                color = TextTertiary,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle(text = "影视目录（JSON）")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = catalog,
                onValueChange = { catalog = it; savedHint = false },
                label = { Text("JSON 地址") },
                placeholder = { Text("https://example.com/catalog.json") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "结构：{\"categories\":[{\"name\":\"电影\",\"items\":[{\"title\":\"…\",\"episodes\":[{\"name\":\"正片\",\"url\":\"…\"}]}]}]}",
                color = TextTertiary,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle(text = "自定义源池（找源页用）")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it; savedHint = false },
                label = { Text("每行一个，格式：名称|地址") },
                placeholder = { Text("我的源|https://example.com/list.m3u") },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "这些地址会出现在「找源」页，和内置公开源一起参与一键检索。只写地址也可以，名称会自动编号。",
                color = TextTertiary,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(30.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvButton(
                    text = "保存并刷新",
                    onClick = {
                        onSave(live.trim(), catalog.trim(), custom.trim())
                        savedHint = true
                    }
                )
                Spacer(Modifier.width(14.dp))
                TvButton(
                    text = "恢复内置演示",
                    primary = false,
                    onClick = {
                        live = ""
                        catalog = ""
                        custom = ""
                        onReset()
                        savedHint = true
                    }
                )
                if (savedHint) {
                    Spacer(Modifier.width(16.dp))
                    Text("已保存", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Spacer(Modifier.width(48.dp))

        // —— 右列：遥控器说明 ——
        Column(Modifier.width(400.dp).fillMaxHeight()) {
            SectionTitle(text = "遥控器操作")
            Spacer(Modifier.height(12.dp))
            KeyHintTable()
            Spacer(Modifier.height(16.dp))
            Text(
                "提示：部分电视盒子对 HLS 支持较弱，若直播卡顿可在片源侧改用低码率线路；" +
                    "播放器已开启硬解与网络缓存，直播源需为 HLS（m3u8）或 MP4。",
                color = TextTertiary,
                fontSize = 11.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun KeyHintTable() {
    val rows = listOf(
        "方向键" to "移动焦点",
        "OK" to "确认 / 播放 / 暂停",
        "左 / 右（播放中）" to "快退 / 快进 10 秒",
        "返回" to "退出或返回上一级"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface1.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        rows.forEachIndexed { index, (key, desc) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier
                        .width(150.dp)
                        .background(Surface1, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(key, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Text(desc, color = TextSecondary, fontSize = 12.sp)
            }
            if (index != rows.lastIndex) {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = Surface1,
    unfocusedContainerColor = Surface1,
    cursorColor = Accent,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Divider,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSecondary
)
