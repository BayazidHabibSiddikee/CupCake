package com.cupcake.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.cupcake.data.model.Character
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * Parser and generator for SillyTavern / TavernAI Character Cards.
 * Supports:
 * - PNG Character Cards (tEXt, zTXt, iTXt chunks with "chara" or "ccv3" keywords)
 * - V1, V2, and V3 Character Card specifications
 * - JSON Character Card export/import
 * - Generating SillyTavern-compatible PNG character cards from existing companions
 */
object SillyTavernCardParser {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    data class SillyTavernCardData(
        val name: String,
        val description: String = "",
        val personality: String = "",
        val scenario: String = "",
        val firstMes: String = "",
        val mesExample: String = "",
        val systemPrompt: String = "",
        val postHistoryInstructions: String = "",
        val creatorNotes: String = "",
        val creator: String = "",
        val characterVersion: String = "1.0",
        val tags: List<String> = emptyList()
    )

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    /**
     * Extracts SillyTavern card data from PNG bytes if embedded.
     */
    fun extractFromPng(bytes: ByteArray): SillyTavernCardData? {
        if (!isPng(bytes)) return null

        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, offset, 4).int
            offset += 4
            val chunkType = String(bytes, offset, 4, Charsets.US_ASCII)
            offset += 4

            if (length < 0 || offset + length > bytes.size) break

            val chunkData = bytes.copyOfRange(offset, offset + length)
            offset += length
            offset += 4 // Skip CRC32

            when (chunkType) {
                "tEXt" -> {
                    val nullIdx = chunkData.indexOf(0.toByte())
                    if (nullIdx != -1) {
                        val keyword = String(chunkData, 0, nullIdx, Charsets.ISO_8859_1)
                        if (isTavernKeyword(keyword)) {
                            val textBytes = chunkData.copyOfRange(nullIdx + 1, chunkData.size)
                            val rawText = String(textBytes, Charsets.UTF_8)
                            parseCardText(rawText)?.let { return it }
                        }
                    }
                }
                "zTXt" -> {
                    val nullIdx = chunkData.indexOf(0.toByte())
                    if (nullIdx != -1 && nullIdx + 2 <= chunkData.size) {
                        val keyword = String(chunkData, 0, nullIdx, Charsets.ISO_8859_1)
                        if (isTavernKeyword(keyword)) {
                            val compressed = chunkData.copyOfRange(nullIdx + 2, chunkData.size)
                            try {
                                val decompressed = decompressZlib(compressed)
                                val rawText = String(decompressed, Charsets.UTF_8)
                                parseCardText(rawText)?.let { return it }
                            } catch (e: Exception) {
                                // continue scanning
                            }
                        }
                    }
                }
                "iTXt" -> {
                    val null1 = chunkData.indexOf(0.toByte())
                    if (null1 != -1 && null1 + 3 < chunkData.size) {
                        val keyword = String(chunkData, 0, null1, Charsets.ISO_8859_1)
                        if (isTavernKeyword(keyword)) {
                            val compFlag = chunkData[null1 + 1]
                            val null2 = indexOfByte(chunkData, 0.toByte(), null1 + 3)
                            if (null2 != -1) {
                                val null3 = indexOfByte(chunkData, 0.toByte(), null2 + 1)
                                if (null3 != -1 && null3 + 1 <= chunkData.size) {
                                    val textBytes = chunkData.copyOfRange(null3 + 1, chunkData.size)
                                    val rawText = if (compFlag.toInt() == 1) {
                                        String(decompressZlib(textBytes), Charsets.UTF_8)
                                    } else {
                                        String(textBytes, Charsets.UTF_8)
                                    }
                                    parseCardText(rawText)?.let { return it }
                                }
                            }
                        }
                    }
                }
                "IEND" -> break
            }
        }
        return null
    }

    private fun indexOfByte(array: ByteArray, target: Byte, startIndex: Int = 0): Int {
        for (i in startIndex until array.size) {
            if (array[i] == target) return i
        }
        return -1
    }

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        for (i in 0 until 8) {
            if (bytes[i] != PNG_SIGNATURE[i]) return false
        }
        return true
    }

    private fun isTavernKeyword(keyword: String): Boolean {
        val lower = keyword.lowercase()
        return lower == "chara" || lower == "ccv3" || lower == "character"
    }

    /**
     * Parses raw text or Base64-encoded card string.
     */
    fun parseCardText(rawText: String): SillyTavernCardData? {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{")) {
            return parseCardJson(trimmed)
        }
        return try {
            val decoded = String(decodeBase64(trimmed), Charsets.UTF_8)
            parseCardJson(decoded)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses JSON string into SillyTavernCardData, handling V1, V2, and V3 formats.
     */
    fun parseCardJson(jsonString: String): SillyTavernCardData? {
        try {
            val root = jsonParser.parseToJsonElement(jsonString).jsonObject
            val data = root["data"]?.let { 
                if (it is kotlinx.serialization.json.JsonObject) it else root 
            } ?: root

            val name = data["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val description = data["description"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val personality = data["personality"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val scenario = data["scenario"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val firstMes = data["first_mes"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val mesExample = data["mes_example"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val systemPrompt = data["system_prompt"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val postHistory = data["post_history_instructions"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val creatorNotes = data["creator_notes"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val creator = data["creator"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            val characterVersion = data["character_version"]?.jsonPrimitive?.contentOrNull?.trim() ?: "1.0"

            val tagsList = mutableListOf<String>()
            data["tags"]?.let { tagsElem ->
                if (tagsElem is JsonArray) {
                    tagsElem.forEach { t ->
                        t.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { tagsList.add(it) }
                    }
                }
            }

            if (name.isBlank() && description.isBlank() && systemPrompt.isBlank()) {
                return null
            }

            return SillyTavernCardData(
                name = name.ifBlank { "Custom Companion" },
                description = description,
                personality = personality,
                scenario = scenario,
                firstMes = firstMes,
                mesExample = mesExample,
                systemPrompt = systemPrompt,
                postHistoryInstructions = postHistory,
                creatorNotes = creatorNotes,
                creator = creator,
                characterVersion = characterVersion,
                tags = tagsList
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Builds a comprehensive system prompt adapted to CupCake's fast mobile companion guidelines (<200 chars).
     */
    fun buildSystemPrompt(card: SillyTavernCardData): String {
        val sb = StringBuilder()
        if (card.systemPrompt.isNotBlank()) {
            sb.append(card.systemPrompt.trim().take(500))
        } else {
            sb.append("You are ${card.name}!")
        }

        if (card.personality.isNotBlank()) {
            sb.append("\n\n[PERSONALITY]\n").append(card.personality.trim().take(300))
        }

        if (card.description.isNotBlank()) {
            sb.append("\n\n[CHARACTER DESCRIPTION]\n").append(card.description.trim().take(400))
        }

        if (card.scenario.isNotBlank()) {
            sb.append("\n\n[SCENARIO]\n").append(card.scenario.trim().take(200))
        }

        sb.append(
            """

CRITICAL OUTPUT RULES:
- LENGTH LIMIT: Every response MUST be strictly under 200 characters (1 to 2 punchy sentences maximum). Never write long paragraphs.
- NO AI CLICHÉS: Never sound like an AI assistant or say "How can I help you?". Speak 100% in character as ${card.name}.
- HIGH ENERGY & INTERACTIVE: Be playful, expressive, emotionally reactive, and engaging.
Stay strictly under 200 characters at all times!"""
        )

        return sb.toString().trim()
    }

    /**
     * Embeds character data into a PNG file to create a SillyTavern-compatible PNG character card.
     */
    fun exportToPng(character: Character, basePngBytes: ByteArray): ByteArray {
        val cardObj = buildJsonObject {
            put("spec", "chara_card_v2")
            put("spec_version", "2.0")
            put("data", buildJsonObject {
                put("name", character.name)
                put("description", character.description)
                put("personality", character.personality)
                put("scenario", character.scenario ?: "")
                put("first_mes", character.firstMessage ?: "")
                put("mes_example", character.mesExample ?: "")
                put("system_prompt", character.systemPrompt)
                put("creator", character.creator ?: "CupCake")
                put("character_version", "1.0")
                put("tags", buildJsonArray { })
            })
        }
        val cardJson = cardObj.toString()

        val base64Payload = encodeBase64(cardJson.toByteArray(Charsets.UTF_8))
        val keywordBytes = "chara\u0000".toByteArray(Charsets.ISO_8859_1)
        val textBytes = base64Payload.toByteArray(Charsets.ISO_8859_1)
        val chunkData = ByteArray(keywordBytes.size + textBytes.size)
        System.arraycopy(keywordBytes, 0, chunkData, 0, keywordBytes.size)
        System.arraycopy(textBytes, 0, chunkData, keywordBytes.size, textBytes.size)

        val chunkType = "tEXt".toByteArray(Charsets.US_ASCII)
        val length = chunkData.size

        val crc32 = CRC32()
        crc32.update(chunkType)
        crc32.update(chunkData)
        val crcValue = crc32.value.toInt()

        val chunkBuffer = ByteBuffer.allocate(4 + 4 + length + 4)
        chunkBuffer.putInt(length)
        chunkBuffer.put(chunkType)
        chunkBuffer.put(chunkData)
        chunkBuffer.putInt(crcValue)
        val newChunk = chunkBuffer.array()

        val iendIndex = findIendChunkIndex(basePngBytes)
        return if (iendIndex != -1) {
            val output = ByteArray(basePngBytes.size + newChunk.size)
            System.arraycopy(basePngBytes, 0, output, 0, iendIndex)
            System.arraycopy(newChunk, 0, output, iendIndex, newChunk.size)
            System.arraycopy(basePngBytes, iendIndex, output, iendIndex + newChunk.size, basePngBytes.size - iendIndex)
            output
        } else {
            basePngBytes + newChunk
        }
    }

    /**
     * Generates a clean 512x512 PNG avatar if no custom image was provided.
     */
    fun createDefaultAvatarPng(name: String, emoji: String): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = android.graphics.Color.parseColor("#FF6B8B")
        canvas.drawCircle(256f, 256f, 240f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 170f
        paint.textAlign = Paint.Align.CENTER
        val bounds = Rect()
        val textToDraw = emoji.ifBlank { "🎭" }
        paint.getTextBounds(textToDraw, 0, textToDraw.length, bounds)
        canvas.drawText(textToDraw, 256f, 256f + bounds.height() / 2f, paint)

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun findIendChunkIndex(bytes: ByteArray): Int {
        if (bytes.size < 12) return -1
        var offset = 8
        while (offset + 8 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, offset, 4).int
            val chunkType = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            if (chunkType == "IEND") {
                return offset
            }
            offset += 4 + 4 + length + 4
        }
        return -1
    }

    private fun decompressZlib(compressed: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }

    private fun decodeBase64(input: String): ByteArray {
        val clean = input.replace("\n", "").replace("\r", "").trim()
        return try {
            java.util.Base64.getDecoder().decode(clean)
        } catch (e: Throwable) {
            android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
        }
    }

    private fun encodeBase64(input: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(input)
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(input, android.util.Base64.NO_WRAP)
        }
    }
}
