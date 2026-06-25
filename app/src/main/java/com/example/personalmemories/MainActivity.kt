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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color.Black) {
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
        uiState.loading -> CenterMessage("正在读取相册...")
        !uiState.hasPermission -> PermissionScreen { permissionLauncher.launch(permissions) }
        uiState.queue.isEmpty() -> CenterMessage("这里还没有可以浏览的内容，试试扩大访问范围。")
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
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("回忆流", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("允许访问照片和视频，用来生成只属于你的随机回忆流。内容不会上传。")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("选择照片与视频 / 允许访问") }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, modifier = Modifier.padding(24.dp))
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

    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val item = uiState.queue.getOrNull(page)?.let { key -> uiState.items.firstOrNull { it.mediaKey == key } }
            if (item != null) {
                MemoryPage(
                    item = item,
                    active = page == pagerState.currentPage,
                    onDoubleLike = onDoubleLike
                )
            }
        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryPage(item: MediaItemEntity, active: Boolean, onDoubleLike: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black).combinedClickable(
            onClick = {},
            onDoubleClick = onDoubleLike
        )
    ) {
        if (item.type == MediaType.PHOTO) {
            AsyncImage(
                model = item.contentUri,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            VideoPlayer(item = item, active = active)
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
    LaunchedEffect(active) {
        if (active) player.play() else player.pause()
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
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
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("随机回忆", color = Color.White, fontWeight = FontWeight.Bold)
        Text(item?.let { if (it.type == MediaType.VIDEO) "Video" else "Photo" }.orEmpty(), color = Color.White)
    }
}

@Composable
private fun ActionRail(liked: Boolean, noteCount: Int, onLike: () -> Unit, onNotes: () -> Unit, onMore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(end = 14.dp, bottom = 54.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom
    ) {
        RailButton(if (liked) "♥" else "♡", onLike)
        Spacer(Modifier.height(12.dp))
        RailButton("记\n$noteCount", onNotes)
        Spacer(Modifier.height(12.dp))
        RailButton("⋯", onMore)
    }
}

@Composable
private fun RailButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(58.dp).background(Color(0x66000000), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomInfo(item: MediaItemEntity, latest: MemoryNoteEntity?) {
    Column(
        Modifier.fillMaxSize().padding(start = 16.dp, end = 90.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(item.albumName ?: "系统相册", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        latest?.let {
            Text(
                if (it.type == NoteType.TEXT) it.text else "最近一条语音记录",
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("我的记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            uiState.notes.forEach { note ->
                NoteRow(note, onDeleteNote, onPlayVoice)
                Spacer(Modifier.height(8.dp))
            }
            TextField(
                value = text,
                onValueChange = { if (it.length <= 2000) text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("写下这一刻的感受") },
                minLines = 2
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    onSaveText(text, null)
                    text = ""
                }) { Text("保存文字") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = if (uiState.recording) onFinishRecording else onStartRecording) {
                    Text(if (uiState.recording) "完成录音" else "录音")
                }
            }
            uiState.recordedDraft?.let {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onPlayVoice(it.path) }) { Text("试听") }
                    TextButton(onClick = onCancelRecording) { Text("重录/取消") }
                    Button(onClick = onSaveVoice) { Text("保存语音") }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: MemoryNoteEntity, onDelete: (MemoryNoteEntity) -> Unit, onPlayVoice: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (note.type == NoteType.TEXT) note.text else "语音记录 ${note.durationMs / 1000}s",
            modifier = Modifier.weight(1f),
            color = Color.White
        )
        if (note.type == NoteType.AUDIO && note.audioPath != null) {
            TextButton(onClick = { onPlayVoice(note.audioPath) }) { Text("播放") }
        }
        TextButton(onClick = { onDelete(note) }) { Text("删除") }
    }
}

@Composable
private fun MoreDialog(item: MediaItemEntity?, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更多") },
        text = {
            Column {
                Text("文件：${item?.displayName ?: "未知"}")
                Text("相册：${item?.albumName ?: "未知"}")
                Text("尺寸：${item?.width ?: 0} × ${item?.height ?: 0}")
            }
        },
        confirmButton = { TextButton(onClick = onDelete) { Text("移入相册回收站", color = Color.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
