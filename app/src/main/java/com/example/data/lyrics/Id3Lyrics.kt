package com.example.data.lyrics

import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.Charset

/** One lyrics tag found in an audio file: [description] is the tag's own label, often blank. */
data class EmbeddedLyrics(val description: String, val lyrics: Lyrics)

private const val MAX_TAG_BYTES = 32 * 1024 * 1024

/**
 * Lyrics tags in the ID3v2 tag at the start of [input]: plain (USLT) and synced (SYLT), in file
 * order. Empty when there's no tag or no lyrics in it; a damaged frame is skipped, not fatal.
 */
fun readId3Lyrics(input: InputStream): List<EmbeddedLyrics> {
    val data = DataInputStream(input)
    val header = ByteArray(10)
    try {
        data.readFully(header)
    } catch (_: java.io.EOFException) {
        return emptyList()
    }
    if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return emptyList()
    val version = header[3].toInt()
    if (version !in 2..4) return emptyList()
    val flags = header[5].toInt()
    val size = synchsafe(header, 6)
    // shortcut: reads the whole tag, cover art included (usually a few MB); stream it if huge tags turn up.
    if (size <= 0 || size > MAX_TAG_BYTES) return emptyList()
    var tag = ByteArray(size)
    try {
        data.readFully(tag)
    } catch (_: java.io.EOFException) {
        return emptyList()
    }
    // v2.2/2.3 apply "unsynchronisation" to the whole tag; v2.4 does it per frame.
    if (version < 4 && flags and 0x80 != 0) tag = resync(tag)

    var pos = 0
    if (version >= 3 && flags and 0x40 != 0) {
        pos = if (version == 3) 4 + bigEndian(tag, 0, 4) else synchsafe(tag, 0)
    }
    val idLength = if (version == 2) 3 else 4
    val headerLength = if (version == 2) 6 else 10
    val found = mutableListOf<EmbeddedLyrics>()
    while (pos + headerLength <= tag.size && tag[pos] != 0.toByte()) {
        val id = String(tag, pos, idLength, Charsets.ISO_8859_1)
        val frameSize = when (version) {
            2 -> bigEndian(tag, pos + 3, 3)
            3 -> bigEndian(tag, pos + 4, 4)
            else -> synchsafe(tag, pos + 4)
        }
        val start = pos + headerLength
        if (frameSize <= 0 || start + frameSize > tag.size) break
        var body = tag.copyOfRange(start, start + frameSize)
        pos = start + frameSize

        val frameFlags = if (version == 2) 0 else tag[start - 1].toInt() and 0xFF
        if (version == 3) {
            if (frameFlags and 0xC0 != 0) continue // compressed or encrypted
            if (frameFlags and 0x20 != 0) body = body.copyOfRange(1, body.size) // group byte
        } else if (version == 4) {
            if (frameFlags and 0x0C != 0) continue // compressed or encrypted
            if (frameFlags and 0x40 != 0) body = body.copyOfRange(1, body.size)
            if (frameFlags and 0x01 != 0) body = body.copyOfRange(4, body.size) // data length
            if (frameFlags and 0x02 != 0) body = resync(body)
        }
        val lyrics = runCatching {
            when (id) {
                "USLT", "ULT" -> parseUslt(body)
                "SYLT", "SLT" -> parseSylt(body)
                else -> null
            }
        }.getOrNull()
        if (lyrics != null) found += lyrics
    }
    return found
}

/** Encoding, language, description, then the text, which may itself be LRC. */
private fun parseUslt(body: ByteArray): EmbeddedLyrics? {
    val encoding = body[0].toInt()
    val (description, textStart) = readString(body, 4, encoding)
    val text = decode(body, textStart, body.size, encoding).trimEnd('\u0000')
    return lyricsFromText(text)?.let { EmbeddedLyrics(description, it) }
}

/** Encoding, language, time format, content type, description, then (text, time) pairs. */
private fun parseSylt(body: ByteArray): EmbeddedLyrics? {
    val encoding = body[0].toInt()
    if (body[4].toInt() != 2) return null // times in MPEG frames, not ms: rare, skipped
    var (description, pos) = readString(body, 6, encoding)
    val syllables = mutableListOf<Pair<String, Long>>()
    while (pos < body.size) {
        val (text, next) = readString(body, pos, encoding)
        if (next + 4 > body.size) break
        syllables += text to bigEndian(body, next, 4).toLong()
        pos = next + 4
    }
    // Karaoke-style tags store syllables and start each line with a newline; plain ones store lines.
    val karaoke = syllables.drop(1).any { it.first.startsWith('\n') || it.first.startsWith('\r') }
    val lines = if (!karaoke) {
        syllables.map { LyricLine(it.second, it.first.trim()) }
    } else {
        val out = mutableListOf<LyricLine>()
        for ((text, time) in syllables) {
            // Spaces between syllables are part of the words, so only the line break is dropped.
            if (out.isEmpty() || text.startsWith('\n') || text.startsWith('\r')) out += LyricLine(time, text.trimStart('\n', '\r'))
            else out[out.lastIndex] = out.last().copy(text = out.last().text + text)
        }
        out.map { it.copy(text = it.text.trim()) }
    }.sortedBy { it.timeMs }
    if (lines.none { it.text.isNotBlank() }) return null
    return EmbeddedLyrics(description, Lyrics.Synced(lines))
}

private fun charsetOf(encoding: Int): Charset = when (encoding) {
    1 -> Charsets.UTF_16 // reads the BOM; BE without one
    2 -> Charsets.UTF_16BE
    3 -> Charsets.UTF_8
    else -> Charsets.ISO_8859_1
}

private fun decode(bytes: ByteArray, from: Int, to: Int, encoding: Int): String {
    if (to <= from) return ""
    // Taggers write \r\n or a bare \r between lines too.
    return String(bytes, from, to - from, charsetOf(encoding)).replace("\r\n", "\n").replace('\r', '\n')
}

/** A string ending in a 0 terminator (two 0 bytes for UTF-16), and where the next one starts. */
private fun readString(bytes: ByteArray, from: Int, encoding: Int): Pair<String, Int> {
    val wide = encoding == 1 || encoding == 2
    var end = from
    while (end < bytes.size) {
        if (!wide && bytes[end] == 0.toByte()) return decode(bytes, from, end, encoding) to end + 1
        if (wide && end + 1 < bytes.size && bytes[end] == 0.toByte() && bytes[end + 1] == 0.toByte()) {
            return decode(bytes, from, end, encoding) to end + 2
        }
        end += if (wide) 2 else 1
    }
    return decode(bytes, from, bytes.size, encoding) to bytes.size
}

/** Undoes unsynchronisation: every 0xFF 0x00 pair was written for a plain 0xFF. */
private fun resync(bytes: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream(bytes.size)
    var i = 0
    while (i < bytes.size) {
        out.write(bytes[i].toInt())
        if (bytes[i] == 0xFF.toByte() && i + 1 < bytes.size && bytes[i + 1] == 0.toByte()) i++
        i++
    }
    return out.toByteArray()
}

private fun synchsafe(b: ByteArray, at: Int): Int =
    (b[at].toInt() and 0x7F shl 21) or (b[at + 1].toInt() and 0x7F shl 14) or
        (b[at + 2].toInt() and 0x7F shl 7) or (b[at + 3].toInt() and 0x7F)

private fun bigEndian(b: ByteArray, at: Int, length: Int): Int {
    var value = 0
    for (i in 0 until length) value = (value shl 8) or (b[at + i].toInt() and 0xFF)
    return value
}
