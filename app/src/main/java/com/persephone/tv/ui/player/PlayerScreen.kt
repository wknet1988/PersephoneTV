package com.persephone.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.persephone.tv.data.PlayTarget
import com.persephone.tv.ui.components.TvButton
import com.persephone.tv.ui.theme.Accent
import com.persephone.tv.ui.theme.Surface2
import com.persephone.tv.ui.theme.TextPrimary
import com.persephone.tv.ui.theme.TextSecondary
import com.persephone.tv.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/** 源没指定 UA 时用的默认值，和拉列表时保持一致 */
private const val DEFAULT_UA = "PersephoneTV/1.0 (Android TV)"

/**
 * 全屏播放器。
 *
 * 遥控器按键：
 * OK / 播放暂停键 → 暂停 / 继续
 * 左 / 右         → 快退 / 快进 10 秒（直播不可用）
 * 上 / 下         → 唤出控制条
 * 返回            → 退出播放（由外层 BackHandler 处理）
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    target: PlayTarget,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val exoPlayer = remember(target.url, target.headers) {
        // 部分源做了防盗链：必须带对 Referer / User-Agent 才给流，
        // 还有不少源是 http 起播后跳到 https，所以放开跨协议重定向。
        val headers = target.headers
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: DEFAULT_UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
        val extra = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        if (extra.isNotEmpty()) httpFactory.setDefaultRequestProperties(extra)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(target.url))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = buildString {
                    append("播放失败：")
                    append(error.errorCodeName)
                    error.message?.let { append("\n").append(it) }
                }
                controlsVisible = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // 进度轮询
    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            delay(400L)
        }
    }

    // 控制条自动隐藏
    LaunchedEffect(controlsVisible, interactionTick, isPlaying) {
        if (controlsVisible && isPlaying && errorMessage == null) {
            delay(4000L)
            controlsVisible = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    fun wakeControls() {
        controlsVisible = true
        interactionTick += 1
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                when {
                    event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                        keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY ||
                        keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (exoPlayer.playWhenReady) exoPlayer.pause() else exoPlayer.play()
                        wakeControls()
                        true
                    }

                    event.key == Key.DirectionLeft ||
                        keyCode == android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (!target.isLive) exoPlayer.seekBack()
                        wakeControls()
                        true
                    }

                    event.key == Key.DirectionRight ||
                        keyCode == android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (!target.isLive) exoPlayer.seekForward()
                        wakeControls()
                        true
                    }

                    event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
                        wakeControls()
                        true
                    }

                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setPlayer(exoPlayer)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.player = exoPlayer }
        )

        // —— 顶部信息条 ——
        AnimatedVisibility(
            visible = controlsVisible && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                        )
                    )
                    .padding(start = 48.dp, end = 48.dp, top = 30.dp, bottom = 46.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (target.isLive) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Accent)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "直播",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = target.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!target.subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(target.subtitle, color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // —— 缓冲中 ——
        if (buffering && errorMessage == null) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
            )
        }

        // —— 暂停提示 ——
        if (!isPlaying && !buffering && errorMessage == null && controlsVisible) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 26.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("已暂停", color = TextPrimary, fontSize = 15.sp)
            }
        }

        // —— 底部控制条 ——
        AnimatedVisibility(
            visible = controlsVisible && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .padding(start = 48.dp, end = 48.dp, top = 52.dp, bottom = 30.dp)
            ) {
                if (!target.isLive && durationMs > 0L) {
                    val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .background(Accent)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "OK 暂停 · 左右快进快退 · 返回退出",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "直播中",
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("OK 暂停 · 返回退出", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }
        }

        // —— 错误兜底 ——
        val err = errorMessage
        if (err != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(horizontal = 80.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("无法播放", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = err,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = target.url,
                        color = TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvButton(
                        text = "重试",
                        onClick = {
                            errorMessage = null
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        }
                    )
                    Spacer(Modifier.width(14.dp))
                    TvButton(text = "退出", onClick = onExit, primary = false)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
