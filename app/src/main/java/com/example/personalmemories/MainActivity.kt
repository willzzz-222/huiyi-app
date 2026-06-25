package com.example.personalmemories

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.personalmemories.data.MediaItemEntity
import com.example.personalmemories.data.MediaType
import com.example.personalmemories.data.MemoryNoteEntity
import com.example.personalmemories.data.NoteType
import com.example.personalmemories.ui.MemoryUiState
import com.example.personalmemories.ui.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AppBackground = Color(0xFF050708)
private val Panel = Color(0xE61A1F20)
private val PanelSoft = Color(0xB3141819)
private val Border = Color(0x1FFFFFFF)
private val Primary = Color(0xFFFF3B68)
private val Success = Color(0xFF65D466)
private val MutedText = Color(0xB8FFFFFF)
private val Danger = Color(0xFFFF4D57)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AppBackground,
                    surface = Panel,
                    primary = Primary,
                    onPrimary = Color.White
                )
            ) {
                Surface(color = AppBackground) {
                    MemoryFlowRoot()
                }
            }
        }
    }
}

@Composable
private fun MemoryFlowRoot(viewModel: MemoryViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val permissions = remember { mediaPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.load(hasMediaPermission(context))
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) viewModel.startRecording() else Toast.makeText(context, "拒绝麦克风权限后无法录音", Toast.LENGTH_SHORT).show()
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) viewModel.onDeleteConfirmed()
    }

    LaunchedEffect(Unit) {
        viewModel.load(hasMediaPermission(context))
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    when {
        uiState.loading -> LoadingScreen()
        !uiState.hasPermission -> PermissionScreen { permissionLauncher.launch(permissions) }
        uiState.queue.isEmpty() -> EmptyScreen(
            title = "还没有可浏览内容",
            message = "试试扩大照片与视频访问范围，或调整系统相册权限。",
            actionText = "重新选择照片",
            onAction = { permissionLauncher.launch(permissions) }
        )
        else -> MemoryFeed(
            uiState = uiState,
            onIndex = viewModel::setIndex,
            onLike = viewModel::toggleLike,
            onDoubleLike = viewModel::doubleTapLike,
            onSaveText = viewModel::saveTextNote,
            onDeleteNote = viewModel::deleteNote,
            onStartRecording = {
                if (hasRecordPermission(context)) viewModel.startRecording() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onFinishRecording = viewModel::finishRecording,
            onCancelRecording = viewModel::cancelRecording,
            onPlayVoice = viewModel::playVoice,
            onSaveVoice = viewModel::saveVoiceDraft,
            onDeleteMedia = {
                viewModel.buildDeleteRequest()?.let { sender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } ?: Toast.makeText(context, "当前系统不支持相册回收站请求", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(0.45f), modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(18.dp))
            Text("加载中...", color = Color.White, fontWeight = FontWeight.Medium)
            Text("正在读取你的相册内容", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF101719), AppBackground, Color(0xFF08090A))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(26.dp))
            Text("回忆流", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("重新遇见自己的照片和视频", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "内容只在本机读取和记录，不上传。授权后即可进入随机回忆流。",
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("选择照片与视频", fontWeight = FontWeight.SemiBold)
            }
        }
        Text("不需要账号，不需要网络", color = Color.White.copy(0.52f), modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun EmptyScreen(title: String, message: String, actionText: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppBackground).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(0.42f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = MutedText)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(actionText) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryFeed(
    uiState: MemoryUiState,
    onIndex: (Int) -> Unit,
    onLike: () -> Unit,
    onDoubleLike: () -> Unit,
    onSaveText: (String, MemoryNoteEntity?) -> Unit,
    onDeleteNote: (MemoryNoteEntity) -> Unit,
    onStartRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayVoice: (String) -> Unit,
    onSaveVoice: () -> Unit,
    onDeleteMedia: () -> Unit
) {
    var showNotes by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = uiState.currentIndex, pageCount = { uiState.queue.size })

    LaunchedEffect(pagerState.currentPage) { onIndex(pagerState.currentPage) }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = uiState.queue.getOrNull(page)?.let { key -> uiState.items.firstOrNull { it.mediaKey == key } }
            if (item != null) MemoryPage(item, page == pagerState.currentPage, onDoubleLike)
        }
        FeedChrome()
        TopBar(uiState.currentItem)
        ActionRail(
            liked = uiState.currentState?.isLiked == true,
            noteCount = uiState.notes.size,
            onLike = onLike,
            onNotes = { showNotes = true },
            onMore = { showMore = true }
        )
        uiState.currentItem?.let { BottomInfo(it, uiState.notes.firstOrNull()) }
    }

    if (showNotes) {
        NotesSheet(
            uiState = uiState,
            onDismiss = { showNotes = false },
            onSaveText = onSaveText,
            onDeleteNote = onDeleteNote,
            onStartRecording = onStartRecording,
            onFinishRecording = onFinishRecording,
            onCancelRecording = onCancelRecording,
            onPlayVoice = onPlayVoice,
            onSaveVoice = onSaveVoice
        )
    }
    if (showMore) {
        MoreDialog(
            item = uiState.currentItem,
            onDismiss = { showMore = false },
            onDelete = {
                showMore = false
                onDeleteMedia()
            }
        )
    }
}

@Composable
private fun FeedChrome() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(0.55f),
                    0.28f to Color.Transparent,
                    0.68f to Color.Transparent,
                    1f to Color.Black.copy(0.78f)
                )
            )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryPage(item: MediaItemEntity, active: Boolean, onDoubleLike: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
            .combinedClickable(onClick = {}, onDoubleClick = onDoubleLike)
    ) {
        if (item.type == MediaType.PHOTO) {
            AsyncImage(
                model = item.contentUri,
                contentDescription = item.displayName ?: "照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            VideoPlayer(item, active)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(item: MediaItemEntity, active: Boolean) {
    val context = LocalContext.current
    val player = remember(item.contentUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(item.contentUri))
            prepare()
        }
    }
    LaunchedEffect(active) { if (active) player.play() else player.pause() }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PlayerView(it).apply {
                useController = false
                this.player = player
            }
        }
    )
}

@Composable
private fun TopBar(item: MediaItemEntity?) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("回忆流", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (item?.type == MediaType.VIDEO) Icons.Default.VideoFile else Icons.Default.Image,
                contentDescription = null,
                tint = Color.White.copy(0.82f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (item?.type == MediaType.VIDEO) "视频" else "照片", color = Color.White.copy(0.82f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActionRail(liked: Boolean, noteCount: Int, onLike: () -> Unit, onNotes: () -> Unit, onMore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(end = 14.dp, bottom = 42.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(0.46f)).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RailButton(
                icon = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (liked) "已赞" else "点赞",
                tint = if (liked) Primary else Color.White,
                onClick = onLike
            )
            RailDivider()
            RailButton(Icons.Default.ChatBubbleOutline, "评论", Color.White, onNotes, count = noteCount)
            RailDivider()
            RailButton(Icons.Default.MoreVert, "更多", Color.White, onMore)
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit, count: Int? = null) {
    IconButton(onClick = onClick, modifier = Modifier.size(58.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            Text((count ?: label).toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RailDivider() {
    Box(Modifier.width(30.dp).height(1.dp).background(Border))
}

@Composable
private fun BottomInfo(item: MediaItemEntity, latest: MemoryNoteEntity?) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(start = 18.dp, end = 96.dp, bottom = 38.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(formatDate(item.dateTaken), color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            latest?.let { if (it.type == NoteType.TEXT) it.text else "有一条语音记录" }
                ?: (item.albumName ?: "来自系统相册"),
            color = Color.White.copy(0.88f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheet(
    uiState: MemoryUiState,
    onDismiss: () -> Unit,
    onSaveText: (String, MemoryNoteEntity?) -> Unit,
    onDeleteNote: (MemoryNoteEntity) -> Unit,
    onStartRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayVoice: (String) -> Unit,
    onSaveVoice: () -> Unit
) {
    var text by remember(uiState.currentItem?.mediaKey) { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1011),
        contentColor = Color.White,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(width = 42.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(0.22f))) }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("我的记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${uiState.notes.size} 条", color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            if (uiState.notes.isEmpty()) {
                EmptyNotes()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(210.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.notes, key = { it.noteId }) { note ->
                        NoteRow(note, onDeleteNote, onPlayVoice)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TextField(
                value = text,
                onValueChange = { if (it.length <= 2000) text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("写下这一刻...", color = Color.White.copy(0.44f)) },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(0.08f),
                    unfocusedContainerColor = Color.White.copy(0.07f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        onSaveText(text, null)
                        text = ""
                    }) {
                        Icon(Icons.Default.Send, contentDescription = "保存文字记录", tint = Color.White)
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            RecordingControls(uiState, onStartRecording, onFinishRecording, onCancelRecording, onPlayVoice, onSaveVoice)
        }
    }
}

@Composable
private fun EmptyNotes() {
    Column(
        modifier = Modifier.fillMaxWidth().height(156.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(0.32f), modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(10.dp))
        Text("还没有任何记录", color = Color.White.copy(0.8f))
        Text("写下此刻的想法，记录你的回忆", color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecordingControls(
    uiState: MemoryUiState,
    onStartRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayVoice: (String) -> Unit,
    onSaveVoice: () -> Unit
) {
    if (uiState.recording) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Danger))
                Spacer(Modifier.width(8.dp))
                Text("正在录音 ${formatDuration(uiState.recordingElapsedMs)}", color = Color.White)
            }
            Button(onClick = onFinishRecording, colors = ButtonDefaults.buttonColors(containerColor = Danger), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("完成")
            }
        }
        return
    }
    uiState.recordedDraft?.let {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onPlayVoice(it.path) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("试听 ${it.durationMs / 1000}s")
            }
            TextButton(onClick = onCancelRecording, modifier = Modifier.weight(1f)) { Text("重录") }
            Button(onClick = onSaveVoice, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("保存") }
        }
        return
    }
    Button(
        onClick = onStartRecording,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.10f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(Icons.Default.Mic, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("添加语音记录")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(note: MemoryNoteEntity, onDelete: (MemoryNoteEntity) -> Unit, onPlayVoice: (String) -> Unit) {
    var deleteVisible by remember(note.noteId) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(if (deleteVisible) 0.11f else 0.075f))
            .combinedClickable(
                onClick = { if (deleteVisible) deleteVisible = false },
                onLongClick = { deleteVisible = true }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (note.type == NoteType.AUDIO) Icons.Default.PlayArrow else Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (note.type == NoteType.TEXT) note.text else "语音记录 ${note.durationMs / 1000}s",
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(formatDate(note.createdAt), color = MutedText, style = MaterialTheme.typography.labelSmall)
        }
        if (note.type == NoteType.AUDIO && note.audioPath != null) {
            IconButton(onClick = { onPlayVoice(note.audioPath) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "播放语音", tint = Color.White)
            }
        }
        if (deleteVisible) {
            IconButton(onClick = { onDelete(note) }) {
                Icon(Icons.Default.Close, contentDescription = "删除记录", tint = Color.White.copy(0.78f))
            }
        }
    }
}

@Composable
private fun MoreDialog(item: MediaItemEntity?, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111516),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("详情与操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("拍摄日期", item?.dateTaken?.let { formatDate(it) } ?: "未知")
                InfoRow("相册", item?.albumName ?: "未知")
                InfoRow("尺寸", "${item?.width ?: 0} × ${item?.height ?: 0}")
                InfoRow("文件名", item?.displayName ?: "未知")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Danger.copy(0.12f)).clickable(onClick = onDelete).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Danger)
                    Spacer(Modifier.width(10.dp))
                    Text("移入相册回收站", color = Danger, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MutedText)
        Text(value, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

private fun formatDate(time: Long): String {
    if (time <= 0L) return "未知时间"
    return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(time))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaPermission(context: android.content.Context): Boolean {
    fun granted(permission: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 -> granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun hasRecordPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
