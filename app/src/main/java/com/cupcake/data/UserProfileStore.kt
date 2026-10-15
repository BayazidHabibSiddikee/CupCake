package com.cupcake.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the companion knows about the person it is talking to. */
data class UserProfile(
    val name: String = "",
    val age: String = "",
    val gender: String = "",
    val relationship: String = "",
    val likes: String = "",
    val dislikes: String = "",
    val notes: String = ""
) {
    val isBlank: Boolean
        get() = name.isBlank() && age.isBlank() && gender.isBlank() &&
                relationship.isBlank() && likes.isBlank() && dislikes.isBlank() && notes.isBlank()

    /** Name used when addressing the user; falls back to a neutral "User". */
    fun displayName(): String = name.ifBlank { "User" }

    /** Compact block appended to the system prompt, or "" when nothing was filled in. */
    fun toPromptBlock(): String {
        if (isBlank) return ""
        val lines = buildList {
            if (name.isNotBlank()) add("Name: $name")
            if (age.isNotBlank()) add("Age: $age")
            if (gender.isNotBlank()) add("Gender/pronouns: $gender")
            if (relationship.isNotBlank()) add("Relationship to you: $relationship")
            if (likes.isNotBlank()) add("Likes: $likes")
            if (dislikes.isNotBlank()) add("Dislikes: $dislikes")
            if (notes.isNotBlank()) add("Notes: $notes")
        }
        return "What you know about the user:\n" + lines.joinToString("\n") +
                "\nAlways address them as ${displayName()}."
    }
}

/** Persists the user profile so the companion remembers who it talks to. */
@Singleton
class UserProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "cupcake_user_profile"
        private const val KEY_NAME = "name"
        private const val KEY_AGE = "age"
        private const val KEY_GENDER = "gender"
        private const val KEY_RELATIONSHIP = "relationship"
        private const val KEY_LIKES = "likes"
        private const val KEY_DISLIKES = "dislikes"
        private const val KEY_NOTES = "notes"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    fun current(): UserProfile = _profile.value

    fun save(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_NAME, profile.name.trim())
            .putString(KEY_AGE, profile.age.trim())
            .putString(KEY_GENDER, profile.gender.trim())
            .putString(KEY_RELATIONSHIP, profile.relationship.trim())
            .putString(KEY_LIKES, profile.likes.trim())
            .putString(KEY_DISLIKES, profile.dislikes.trim())
            .putString(KEY_NOTES, profile.notes.trim())
            .apply()
        _profile.value = profile
    }

    private fun load(): UserProfile = UserProfile(
        name = prefs.getString(KEY_NAME, "") ?: "",
        age = prefs.getString(KEY_AGE, "") ?: "",
        gender = prefs.getString(KEY_GENDER, "") ?: "",
        relationship = prefs.getString(KEY_RELATIONSHIP, "") ?: "",
        likes = prefs.getString(KEY_LIKES, "") ?: "",
        dislikes = prefs.getString(KEY_DISLIKES, "") ?: "",
        notes = prefs.getString(KEY_NOTES, "") ?: ""
    )
}
