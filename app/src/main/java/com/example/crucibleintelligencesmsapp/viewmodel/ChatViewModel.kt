package com.example.crucibleintelligencesmsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crucibleintelligencesmsapp.data.AppDatabase
import com.example.crucibleintelligencesmsapp.data.EntityProfile
import com.example.crucibleintelligencesmsapp.data.repository.SmsMessage
import com.example.crucibleintelligencesmsapp.data.repository.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val messages: StateFlow<List<SmsMessage>> = smsRepository
        .observeMessagesForThread(threadId, address)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(body: String) {
        if (body.isBlank() || address.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            smsRepository.sendMessage(address, body)
            markAsRead()
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
