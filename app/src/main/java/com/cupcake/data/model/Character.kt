package com.cupcake.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Character(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val avatar: String,
    val personality: String,
    val languages: List<String> = listOf("en"),
    val isBuiltIn: Boolean = false,
    var isSelected: Boolean = false
) {
    fun withPromptAddition(addition: String): Character {
        return copy(systemPrompt = "$systemPrompt\n\n$addition")
    }
}
