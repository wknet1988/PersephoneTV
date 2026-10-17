package com.persephone.tv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.persephone.tv.data.ContentRepository
import com.persephone.tv.data.LoadState
import com.persephone.tv.data.VodCategory
import com.persephone.tv.data.VodItem
import com.persephone.tv.ui.components.LoadingPane
import com.persephone.tv.ui.components.MessagePane
import com.persephone.tv.ui.components.PosterCard
import com.persephone.tv.ui.components.SectionTitle
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextTertiary
import com.persephone.tv.ui.theme.TvType

@Composable
fun VodScreen(
    sourceUrl: String,
    onOpen: (VodItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember(sourceUrl) { mutableStateOf<LoadState<List<VodCategory>>>(LoadState.Loading) }
    var retry by remember(sourceUrl) { mutableStateOf(0) }

    LaunchedEffect(sourceUrl, retry) {
        state = LoadState.Loading
        val result = ContentRepository.loadCatalog(context, sourceUrl)
        state = result.fold(
            onSuccess = { list ->
                if (list.isEmpty()) LoadState.Error("目录里没有内容") else LoadState.Success(list)
            },
            onFailure = { e -> LoadState.Error(e.message ?: e.javaClass.simpleName) }
        )
    }

    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            is LoadState.Loading, is LoadState.Idle -> LoadingPane("正在加载影视目录…")
            is LoadState.Error -> MessagePane(
                title = "影视目录加载失败",
                detail = s.message + "\n可在「设置」里更换 JSON 地址，或清空地址使用内置演示片库。",
                actionLabel = "重试",
                onAction = { retry++ }
            )
            is LoadState.Success -> VodContent(categories = s.data, onOpen = onOpen)
        }
    }
}

@Composable
private fun VodContent(categories: List<VodCategory>, onOpen: (VodItem) -> Unit) {
    val firstRequester = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    val total = remember(categories) { categories.sumOf { it.items.size } }

    LaunchedEffect(categories) {
        if (!focusedOnce) {
            focusedOnce = true
            runCatching { firstRequester.requestFocus() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Transparent),
        contentPadding = PaddingValues(start = 54.dp, end = 54.dp, top = 30.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        item(key = "vod-header") {
            Column {
                Text("影视", style = TvType.Display, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${categories.size} 个分类 · $total 部作品 · 数据来自 JSON 片源",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }

        categories.forEach { category ->
            item(key = "cat-${category.name}") {
                Column {
                    SectionTitle(text = category.name, trailing = "${category.items.size} 部")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(category.items, key = { "${category.name}-${it.title}" }) { item ->
                            val isFirst = category === categories.firstOrNull() &&
                                item === category.items.firstOrNull()
                            PosterCard(
                                item = item,
                                onClick = { onOpen(item) },
                                modifier = if (isFirst) Modifier.focusRequester(firstRequester) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}
