package com.example.personalmemories.ui

import android.app.Application
import android.app.PendingIntent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalmemories.MemoryFlowApp
import com.example.personalmemories.data.MediaItemEntity
import com.example.personalmemories.data.MediaStateEntity
import com.example.personalmemories.data.MemoryNoteEntity
import com.example.personalmemories.data.NoteType
import com.example.personalmemories.data.ShuffleSessionEntity
import com.example.personalmemories.media.ShuffleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class MemoryUiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val items: List<MediaItemEntity> = emptyList(),
    val queue: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val currentState: MediaStateEntity? = null,
    val notes: List<MemoryNoteEntity> = emptyList(),
    val message: String? = null,
    val recording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordedDraft: VoiceDraft? = null
) {
    val currentItem: MediaItemEntity? get() = queue.getOrNull(currentIndex)?.let { key -> items.firstOrNull { it.mediaKey == key } }
}

data class VoiceDraft(val path: String, val durationMs: Long)

class MemoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MemoryFlowApp
    private val dao = app.database.memoryDao()
    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()
    private var activeRecordingPath: String? = null
    private var recordingLimitJob: Job? = null
    private var recordingTickerJob: Job? = null

    init {
        app.audio.onAudioFocusNeeded = { pauseVideoSignal() }
    }

    fun load(hasPermission: Boolean) {
        viewModelScope.launch {
            if (!hasPermission) {
                _uiState.value = MemoryUiState(loading = false, hasPermission = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(loading = true, hasPermission = true)
            val scanned = withContext(Dispatchers.IO) { app.scanner.scan() }
            dao.upsertMedia(scanned)
            val active = dao.activeMedia()
            val previous = dao.getSession()
            val queue = restoreOrCreateQueue(active, previous)
            val index = previous?.currentIndex?.coerceIn(0, queue.lastIndex.coerceAtLeast(0)) ?: 0
            dao.upsertSession(ShuffleSessionEntity(orderedMediaKeys = queue.joinToString("|"), currentIndex = index))
            _uiState.value = MemoryUiState(
                loading = false,
                hasPermission = true,
                items = active,
                queue = queue,
                currentIndex = index
            )
            loadCurrentDetails()
        }
    }

    fun setIndex(index: Int) {
        val state = _uiState.value
        if (index !in state.queue.indices || index == state.currentIndex) return
        viewModelScope.launch {
            _uiState.value = state.copy(currentIndex = index)
            dao.upsertSession(ShuffleSessionEntity(orderedMediaKeys = state.queue.joinToString("|"), currentIndex = index))
            state.queue.getOrNull(index)?.let { key ->
                val old = dao.stateByKey(key) ?: MediaStateEntity(mediaKey = key)
                dao.upsertState(old.copy(viewCount = old.viewCount + 1, lastSeenAt = System.currentTimeMillis()))
            }
            loadCurrentDetails()
        }
    }

    fun toggleLike() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch {
            val old = dao.stateByKey(item.mediaKey) ?: MediaStateEntity(mediaKey = item.mediaKey)
            val next = old.copy(isLiked = !old.isLiked, updatedAt = System.currentTimeMillis())
            dao.upsertState(next)
            _uiState.value = _uiState.value.copy(currentState = next)
        }
    }

    fun doubleTapLike() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch {
            val old = dao.stateByKey(item.mediaKey) ?: MediaStateEntity(mediaKey = item.mediaKey)
            val next = old.copy(isLiked = true, updatedAt = System.currentTimeMillis())
            dao.upsertState(next)
            _uiState.value = _uiState.value.copy(currentState = next, message = "已点赞")
        }
    }

    fun saveTextNote(text: String, editing: MemoryNoteEntity? = null) {
        val item = _uiState.value.currentItem ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 2000) {
            _uiState.value = _uiState.value.copy(message = if (trimmed.isEmpty()) "记录不能为空" else "最多 2000 字")
            return
        }
        viewModelScope.launch {
            dao.upsertNote(
                MemoryNoteEntity(
                    noteId = editing?.noteId ?: UUID.randomUUID().toString(),
                    mediaKey = item.mediaKey,
                    type = NoteType.TEXT,
                    text = trimmed,
                    createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            loadCurrentDetails()
        }
    }

    fun deleteNote(note: MemoryNoteEntity) {
        viewModelScope.launch {
            if (note.type == NoteType.AUDIO) note.audioPath?.let { File(app.filesDir, it).delete() }
            dao.deleteNote(note.noteId)
            loadCurrentDetails()
        }
    }

    fun startRecording() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val relative = "voice_notes/${item.mediaKey}_${System.currentTimeMillis()}.m4a".replace(":", "_")
            val file = File(app.filesDir, relative)
            runCatching { app.audio.startRecording(file) }
                .onSuccess {
                    activeRecordingPath = relative
                    _uiState.value = _uiState.value.copy(recording = true, recordingElapsedMs = 0L, recordedDraft = null, message = "正在录音")
                    recordingTickerJob?.cancel()
                    recordingTickerJob = viewModelScope.launch {
                        while (_uiState.value.recording) {
                            delay(250L)
                            _uiState.value = _uiState.value.copy(recordingElapsedMs = _uiState.value.recordingElapsedMs + 250L)
                        }
                    }
                    recordingLimitJob?.cancel()
                    recordingLimitJob = viewModelScope.launch {
                        delay(5 * 60 * 1000L)
                        if (_uiState.value.recording) finishRecording()
                    }
                }
                .onFailure { _uiState.value = _uiState.value.copy(message = "无法开始录音") }
        }
    }

    fun finishRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            val duration = app.audio.stopRecording()
            recordingLimitJob?.cancel()
            recordingLimitJob = null
            recordingTickerJob?.cancel()
            recordingTickerJob = null
            val candidate = activeRecordingPath?.let { File(app.filesDir, it) }
            activeRecordingPath = null
            if (duration < 1000 || candidate == null || !candidate.exists()) {
                candidate?.delete()
                _uiState.value = _uiState.value.copy(recording = false, recordingElapsedMs = 0L, message = "录音太短，未保存")
            } else {
                val relative = candidate.relativeTo(app.filesDir).path
                _uiState.value = _uiState.value.copy(recording = false, recordingElapsedMs = 0L, recordedDraft = VoiceDraft(relative, duration), message = "录音完成，可试听后保存")
            }
        }
    }

    fun cancelRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            app.audio.cancelRecording()
            recordingLimitJob?.cancel()
            recordingLimitJob = null
            recordingTickerJob?.cancel()
            recordingTickerJob = null
            activeRecordingPath?.let { File(app.filesDir, it).delete() }
            activeRecordingPath = null
            _uiState.value.recordedDraft?.path?.let { File(app.filesDir, it).delete() }
            _uiState.value = _uiState.value.copy(recording = false, recordingElapsedMs = 0L, recordedDraft = null)
        }
    }

    fun playVoice(relativePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.audio.playVoice(File(app.filesDir, relativePath))
        }
    }

    fun saveVoiceDraft() {
        val item = _uiState.value.currentItem ?: return
        val draft = _uiState.value.recordedDraft ?: return
        viewModelScope.launch {
            dao.upsertNote(
                MemoryNoteEntity(
                    noteId = UUID.randomUUID().toString(),
                    mediaKey = item.mediaKey,
                    type = NoteType.AUDIO,
                    audioPath = draft.path,
                    durationMs = draft.durationMs
                )
            )
            _uiState.value = _uiState.value.copy(recordedDraft = null)
            loadCurrentDetails()
        }
    }

    fun buildDeleteRequest(): IntentSender? {
        val item = _uiState.value.currentItem ?: return null
        if (Build.VERSION.SDK_INT < 30) return null
        val uri = Uri.parse(item.contentUri)
        val pendingIntent: PendingIntent = MediaStore.createTrashRequest(getApplication<Application>().contentResolver, listOf(uri), true)
        return pendingIntent.intentSender
    }

    fun onDeleteConfirmed() {
        val item = _uiState.value.currentItem ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.audioNotesForMedia(item.mediaKey).forEach { note ->
                note.audioPath?.let { File(app.filesDir, it).delete() }
            }
            dao.removeMediaAndRecords(item.mediaKey)
            val items = dao.activeMedia()
            val queue = _uiState.value.queue.filterNot { it == item.mediaKey }
            val index = _uiState.value.currentIndex.coerceAtMost((queue.lastIndex).coerceAtLeast(0))
            dao.upsertSession(ShuffleSessionEntity(orderedMediaKeys = queue.joinToString("|"), currentIndex = index))
            _uiState.value = _uiState.value.copy(items = items, queue = queue, currentIndex = index, message = "已移入相册回收站")
            loadCurrentDetails()
        }
    }

    fun onMessageShown() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun loadCurrentDetails() {
        val item = _uiState.value.currentItem ?: return
        val state = dao.stateByKey(item.mediaKey) ?: MediaStateEntity(item.mediaKey)
        _uiState.value = _uiState.value.copy(currentState = state, notes = dao.notesByKey(item.mediaKey))
    }

    private fun restoreOrCreateQueue(active: List<MediaItemEntity>, previous: ShuffleSessionEntity?): List<String> {
        val activeKeys = active.map { it.mediaKey }.toSet()
        val restored = previous?.orderedMediaKeys.orEmpty().split("|").filter { it in activeKeys }
        return if (restored.isNotEmpty()) restored else ShuffleEngine.newRound(active)
    }

    private fun pauseVideoSignal() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    override fun onCleared() {
        app.audio.release()
        super.onCleared()
    }
}
