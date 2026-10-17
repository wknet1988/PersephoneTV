package com.persephone.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.persephone.tv.data.VodItem
import com.persephone.tv.ui.theme.Accent
import com.persephone.tv.ui.theme.Bg
import com.persephone.tv.ui.theme.Surface1
import com.persephone.tv.ui.theme.Surface2
import com.persephone.tv.ui.theme.Surface3
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextSecondary
import com.persephone.tv.ui.theme.TextTertiary
import com.persephone.tv.ui.theme.TvType

/**
 * 可聚焦卡片基类：遥控器移动到卡片上时放大 + 描边。
 * 所有电视端可点击元素都基于它，保证焦点表现一致。
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    scaleOnFocus: Float = 1.06f,
    focusedBorder: BorderStroke = BorderStroke(3.dp, Color.White),
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleOnFocus else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "focusScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(if (focused) Modifier.border(focusedBorder, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        content(focused)
    }
}

/** 电视端主按钮 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val bg by animateColorAsState(
        targetValue = when {
            focused -> Color.White
            primary -> Accent
            else -> Surface3
        },
        animationSpec = tween(150),
        label = "btnBg"
    )
    val fg by animateColorAsState(
        targetValue = when {
            focused -> Bg
            primary -> Color.White
            else -> TextPrimary
        },
        animationSpec = tween(150),
        label = "btnFg"
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "btnScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 28.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** 海报卡片（影视列表用） */
@Composable
fun PosterCard(
    item: VodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 172.dp
) {
    Column(modifier = modifier.width(cardWidth)) {
        FocusCard(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardWidth * 1.45f),
            shape = RoundedCornerShape(12.dp),
            scaleOnFocus = 1.07f
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                PosterPlaceholder(item)
                if (!item.poster.isNullOrBlank()) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!item.rating.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.62f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = item.rating,
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val meta = listOfNotNull(item.year, item.episodes.size.takeIf { it > 1 }?.let { "$it 集" })
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = TextTertiary,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

/** 没有海报时的占位：渐变 + 首字 */
@Composable
fun PosterPlaceholder(item: VodItem) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Surface2, Surface1)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = item.title.take(1),
                color = TextSecondary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(22.dp)
                    .height(2.dp)
                    .background(Accent)
            )
        }
    }
}

/** 分组标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(17.dp)
                    .background(Accent)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = text,
                style = TvType.Title,
                color = TextPrimary
            )
        }
        if (trailing != null) {
            Text(text = trailing, color = TextTertiary, fontSize = 12.sp)
        }
    }
}

/** 加载中 */
@Composable
fun LoadingPane(message: String = "正在加载…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(14.dp))
            Text(text = message, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

/** 错误 / 空状态 */
@Composable
fun MessagePane(
    title: String,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(520.dp)
        ) {
            Text(text = title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = detail,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(22.dp))
                TvButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}
