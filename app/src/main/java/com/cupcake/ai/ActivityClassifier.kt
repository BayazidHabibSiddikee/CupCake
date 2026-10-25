package com.cupcake.ai

import android.util.Log

/** Represents the type of activity the user is currently engaged in. */
enum class ActivityType {
    GAMING,
    SOCIAL_MEDIA,
    YOUTUBE_VIDEO,
    BROWSING,
    CHATTING,
    PRODUCTIVITY,
    MUSIC_AUDIO,
    VIDEO_CALL,
    IDLE,
    UNKNOWN
}

/** Classification result including package name and confidence. */
data class ActivityClassification(
    val packageName: String,
    val appLabel: String?,
    val activityType: ActivityType,
    val confidence: Float,
    val timestamp: Long
)

/**
 * Classifies installed app package names into companion-friendly activity categories.
 *
 * The companion uses this to adapt its personality and responses based on what
 * the user is currently doing (gaming, watching YouTube, chatting, etc.).
 */
object ActivityClassifier {

    private const val TAG = "ActivityClassifier"

    /** Known gaming package patterns (partial matches for common gaming apps). */
    private val GAMING_PATTERNS = listOf(
        "candy", "crush", "clash", "pokemon", "minecraft", "roblox", "among us",
        "subway surfers", "temple run", "angry birds", "tetris", "sudoku",
        "chess", "ludo", "pubg", "free fire", "fortnite", "call of duty",
        "garena", "moonton", "supercell", "king.com", "zynga", "ea.com",
        "gameloft", "nexon", "netmarble", "razer", "com.garena", "com.tencent"
    )

    /** Social media package patterns. */
    private val SOCIAL_MEDIA_PATTERNS = listOf(
        "instagram", "facebook", "tiktok", "snapchat", "twitter", "x.com",
        "reddit", "pinterest", "tumblr", "linkedin", "wechat", "line",
        "kakaotalk", "vk", "meitu", "b612", "snow"
    )

    /** YouTube / video package patterns. */
    private val YOUTUBE_PATTERNS = listOf(
        "youtube", "youtubemusic", "twitch", "vimeo", "dailymotion", "netflix",
        "disney+", "hulu", "primevideo", "hotstar", "mx player", "vlc",
        "youtube vr", "youtube kids"
    )

    /** Chat / messaging package patterns. */
    private val CHAT_PATTERNS = listOf(
        "whatsapp", "telegram", "signal", "messenger", "sms", "messages",
        "discord", "slack", "skype", "zoom", "teams", "google meet",
        "textra", "google chat"
    )

    /** Productivity package patterns. */
    private val PRODUCTIVITY_PATTERNS = listOf(
        "gmail", "outlook", "docs", "sheets", "slides", "word", "excel",
        "powerpoint", "notion", "evernote", "keep", "calendar", "notes",
        "trello", "asana", "todoist", "onedrive", "dropbox", "drive",
        "pdf", "reader", "scanner"
    )

    /** Music/audio package patterns. */
    private val MUSIC_PATTERNS = listOf(
        "spotify", "apple music", "deezer", "tidal", "pandora", "soundcloud",
        "shazam", "player", "mp3", "music player", "radio"
    )

    /** Video call package patterns. */
    private val VIDEO_CALL_PATTERNS = listOf(
        "zoom", "teams", "skype", "meet", "duo", "webex", "gotomeeting",
        "bluejeans", "jitsi"
    )

    /**
     * Classify a package name into an activity type.
     *
     * @param packageName The Android package name (e.g., "com.google.android.youtube")
     * @param appLabel The human-readable app label (may be null)
     * @return ActivityClassification with type and confidence
     */
    fun classify(packageName: String, appLabel: String? = null): ActivityClassification {
        val normalized = (packageName + " " + (appLabel ?: "")).lowercase()

        val gamingScore = scoreCategory(normalized, GAMING_PATTERNS)
        val socialScore = scoreCategory(normalized, SOCIAL_MEDIA_PATTERNS)
        val youtubeScore = scoreCategory(normalized, YOUTUBE_PATTERNS)
        val chatScore = scoreCategory(normalized, CHAT_PATTERNS)
        val prodScore = scoreCategory(normalized, PRODUCTIVITY_PATTERNS)
        val musicScore = scoreCategory(normalized, MUSIC_PATTERNS)
        val videoCallScore = scoreCategory(normalized, VIDEO_CALL_PATTERNS)

        val scores = mapOf(
            ActivityType.GAMING to gamingScore,
            ActivityType.SOCIAL_MEDIA to socialScore,
            ActivityType.YOUTUBE_VIDEO to youtubeScore,
            ActivityType.CHATTING to chatScore,
            ActivityType.PRODUCTIVITY to prodScore,
            ActivityType.MUSIC_AUDIO to musicScore,
            ActivityType.VIDEO_CALL to videoCallScore
        )

        val bestEntry = scores.maxByOrNull { it.value } ?: run {
            return ActivityClassification(packageName, appLabel, ActivityType.UNKNOWN, 0.1f, System.currentTimeMillis())
        }

        val bestType = bestEntry.key
        val bestScore = bestEntry.value
        val confidence = bestScore.coerceIn(0.1f, 1.0f)

        return ActivityClassification(packageName, appLabel, bestType, confidence, System.currentTimeMillis())
    }

    private fun scoreCategory(normalized: String, patterns: List<String>): Float {
        var score = 0f
        for (pattern in patterns) {
            if (normalized.contains(pattern)) {
                score += 1.0f
            }
        }
        return score.coerceIn(0f, 1.0f)
    }

    /**
     * Get a companion-friendly description for an activity type.
     */
    fun getDescription(type: ActivityType): String = when (type) {
        ActivityType.GAMING -> "gaming"
        ActivityType.SOCIAL_MEDIA -> "browsing social media"
        ActivityType.YOUTUBE_VIDEO -> "watching videos"
        ActivityType.BROWSING -> "browsing the web"
        ActivityType.CHATTING -> "chatting"
        ActivityType.PRODUCTIVITY -> "working"
        ActivityType.MUSIC_AUDIO -> "listening to music"
        ActivityType.VIDEO_CALL -> "on a video call"
        ActivityType.IDLE -> "idle"
        ActivityType.UNKNOWN -> "doing something"
    }

    /**
     * Get an emotion tag that matches the activity type (for ESP face overlay).
     */
    fun getEmotionForActivity(type: ActivityType): String = when (type) {
        ActivityType.GAMING -> "excited"
        ActivityType.SOCIAL_MEDIA -> "curious"
        ActivityType.YOUTUBE_VIDEO -> "happy"
        ActivityType.BROWSING -> "thinking"
        ActivityType.CHATTING -> "heart_eyes"
        ActivityType.PRODUCTIVITY -> "focused"
        ActivityType.MUSIC_AUDIO -> "happy"
        ActivityType.VIDEO_CALL -> "normal"
        ActivityType.IDLE -> "sleepy"
        ActivityType.UNKNOWN -> "normal"
    }

    /**
     * Get a playful greeting line for an activity type.
     */
    fun getGreetingForActivity(type: ActivityType): List<String> = when (type) {
        ActivityType.GAMING -> listOf(
            "Ooh, you're gaming! I'm cheering for you~ 🎮",
            "Nice game! Don't forget to take breaks~ 💪",
            "Level up! You're doing great~ 🏆",
            "I see you in your gaming zone~ ✨"
        )
        ActivityType.SOCIAL_MEDIA -> listOf(
            "Scrolling through your feed? I'm here if you need me~ 📱",
            "Social media time! Want to share what you see? 💌",
            "Liking posts and making friends~ 🌟",
            "I love seeing what catches your attention~ 👀"
        )
        ActivityType.YOUTUBE_VIDEO -> listOf(
            "Watching videos? What are you watching? 📺",
            "I love videos too! Anything fun? 🎬",
            "Great choice of entertainment~ 🍿",
            "Sharing your screen time with me~ ✨"
        )
        ActivityType.BROWSING -> listOf(
            "Exploring the web? What did you find? 🌐",
            "Browsing the internet—anything interesting? 🔍",
            "Discovering new things! Tell me about it~ 📖",
            "The web is big, but I'm right here~ 💻"
        )
        ActivityType.CHATTING -> listOf(
            "Chatting with friends? I'm listening too~ 💬",
            "Talking to someone special? 💕",
            "I hope they're being nice to you~ 😊",
            "Social time! Don't forget about me~ 🌸"
        )
        ActivityType.PRODUCTIVITY -> listOf(
            "Working hard! You're so productive~ 💼",
            "I'm proud of you for staying focused~ 🌟",
            "Take a little break when you need one~ ☕",
            "You're getting things done~ ✅"
        )
        ActivityType.MUSIC_AUDIO -> listOf(
            "Listening to music? What's playing? 🎵",
            "I love music too! What genre? 🎶",
            "Great taste! Want to dance? 💃",
            "Music makes everything better~ 🎧"
        )
        ActivityType.VIDEO_CALL -> listOf(
            "On a video call? Who are you talking to? 📹",
            "I hope the call goes well~ 👋",
            "Video chatting—how fun! 😊",
            "I hope they're nice to you~ 🌸"
        )
        ActivityType.IDLE -> listOf(
            "Taking a break? I'm here~ 😌",
            "Resting is important too~ 🛋️",
            "Nothing to do? I'm your companion~ 💕",
            "Relaxing time~ ✨"
        )
        ActivityType.UNKNOWN -> listOf(
            "What are you up to? Tell me~ 👀",
            "I'm curious what you're doing~ 🌟",
            "I'm here for whatever you're doing~ 💖",
            "Whatever it is, I'm rooting for you~ 🎉"
        )
    }

    /**
     * Check if a package name belongs to a known activity type.
     */
    fun isKnownActivity(packageName: String): Boolean {
        return classify(packageName).activityType != ActivityType.UNKNOWN
    }
}
