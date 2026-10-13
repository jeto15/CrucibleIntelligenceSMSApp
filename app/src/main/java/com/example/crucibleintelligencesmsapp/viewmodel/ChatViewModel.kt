package com.example.crucibleintelligencesmsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crucibleintelligencesmsapp.data.AppDatabase
import com.example.crucibleintelligencesmsapp.data.EntityProfile
import com.example.crucibleintelligencesmsapp.data.MediaAttachment
import com.example.crucibleintelligencesmsapp.data.repository.SmsMessage
import com.example.crucibleintelligencesmsapp.data.repository.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class ChatViewModel(
    val threadId: Long,
    val address: String,
    val initialTitle: String,
    private val database: AppDatabase,
    private val smsRepository: SmsRepository
) : ViewModel() {

    init {
        markAsRead()
    }

    fun markAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            smsRepository.markAsRead(threadId, address)
        }
    }

    val profile: StateFlow<EntityProfile?> = flow {
        val entityProfile = smsRepository.getOrCreateProfileForAddress(address)
        if (entityProfile != null) {
            emitAll(database.profileDao().observeProfileById(entityProfile.id))
        } else {
            emit(null)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _sentAttachmentsMap = MutableStateFlow<Map<Long, List<MediaAttachment>>>(emptyMap())

    val messages: StateFlow<List<SmsMessage>> = combine(
        smsRepository.observeMessagesForThread(threadId, address),
        _sentAttachmentsMap
    ) { msgList, attMap ->
        if (attMap.isEmpty()) {
            msgList
        } else {
            msgList.map { msg ->
                val atts = attMap[msg.id] ?: attMap.entries.firstOrNull { entry ->
                    !msg.isIncoming && abs(entry.key - msg.timestamp) < 15000
                }?.value
                if (!atts.isNullOrEmpty()) {
                    msg.copy(attachments = atts)
                } else {
                    msg
                }
            }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(body: String, attachments: List<MediaAttachment> = emptyList()) {
        if (body.isBlank() && attachments.isEmpty()) return
        if (address.isBlank()) return

        val sendTime = System.currentTimeMillis()
        if (attachments.isNotEmpty()) {
            _sentAttachmentsMap.update { current ->
                current + (sendTime to attachments)
            }
        }

        val textToSend = if (body.isNotBlank()) {
            body
        } else {
            "[Attachment: ${attachments.joinToString { it.name }}]"
        }

        viewModelScope.launch(Dispatchers.IO) {
            smsRepository.sendMessage(address, textToSend)
            markAsRead()
        }
    }

    fun saveProfileNotes(notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = profile.value
            if (currentProfile != null) {
                database.profileDao().updateProfile(currentProfile.copy(notes = notes))
            } else {
                val newProfile = EntityProfile(
                    name = initialTitle.ifBlank { null },
                    phoneNumber = address,
                    normalizedNumber = address.replace(Regex("[^0-9+]"), ""),
                    notes = notes
                )
                database.profileDao().insertProfile(newProfile)
            }
        }
    }

    class Factory(
        private val threadId: Long,
        private val address: String,
        private val initialTitle: String,
        private val database: AppDatabase,
        private val smsRepository: SmsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(threadId, address, initialTitle, database, smsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
