package com.cupcake.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cupcake.domain.usecase.CreateConversationUseCase
import com.cupcake.domain.usecase.GetConversationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val createConversationUseCase: CreateConversationUseCase
) : ViewModel() {

    val conversations = getConversationsUseCase().asStateFlow(initialValue = emptyList())

    val showNewChatDialog = MutableStateFlow(false)

    fun createConversation(title: String) {
        viewModelScope.launch {
            createConversationUseCase(title)
        }
    }
}