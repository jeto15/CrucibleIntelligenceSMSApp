package com.example.crucibleintelligencesmsapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crucibleintelligencesmsapp.data.repository.SmsConversation
import com.example.crucibleintelligencesmsapp.data.repository.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class InboxViewModel(
    private val smsRepository: SmsRepository
) : ViewModel() {

    val conversations: StateFlow<List<SmsConversation>> = smsRepository
        .observeConversations()
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    class Factory(
        private val smsRepository: SmsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InboxViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return InboxViewModel(smsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
