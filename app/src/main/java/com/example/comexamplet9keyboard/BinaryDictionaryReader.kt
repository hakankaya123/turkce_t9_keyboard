package com.example.comexamplet9keyboard

import android.content.Context
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

/**
 * Data class representing a single dictionary entry.
 */
data class WordEntry(
    val word: String,
    val frequency: Int,
)

/**
 * Zero-Copy Memory-Mapped Binary Dictionary Reader for Android.
 *
 * Reads 36-byte records from words_tr.bin directly via FileChannel and MappedByteBuffer:
 * - 32 bytes: UTF-8 word padded with 0x00 null bytes
 * - 4 bytes: Int32 Big-Endian frequency
 */
class BinaryDictionaryReader(
    private val context: Context,
    private val assetFileName: String = "words_tr.bin"
) {

    companion object {
        const val WORD_SIZE = 32
        const val FREQ_SIZE = 4
        const val RECORD_SIZE = WORD_SIZE + FREQ_SIZE
    }

    private var buffer: ByteBuffer? = null
    private var totalRecords = 0

    /**
     * Initializes and memory-maps the binary dictionary file from assets.
     */
    fun open(): Boolean {
        return try {
            val fileDescriptor = context.assets.openFd(assetFileName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val channel = inputStream.channel

            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength

            buffer = channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength).apply {
                order(ByteOrder.BIG_ENDIAN)
            }
            totalRecords = (declaredLength / RECORD_SIZE).toInt()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Returns the total record count in the binary dictionary.
     */
    fun getRecordCount(): Int = totalRecords

    /**
     * Reads a single WordEntry at record [index] directly from the memory-mapped buffer.
     */
    fun getEntryAt(index: Int): WordEntry? {
        val buf = buffer ?: return null
        if (index !in 0 until totalRecords) return null

        val offset = index * RECORD_SIZE
        val wordBytes = ByteArray(WORD_SIZE)

        synchronized(buf) {
            buf.position(offset)
            buf.get(wordBytes, 0, WORD_SIZE)
            val freq = buf.int
            val wordStr = parseNullTerminatedUtf8(wordBytes)
            return WordEntry(wordStr, freq)
        }
    }

    /**
     * Reads all records from the memory-mapped buffer into a list of WordEntry items.
     * High-performance sequence stream processing minimizes GC overhead.
     */
    fun readAllEntries(): List<WordEntry> {
        val buf = buffer ?: return emptyList()
        val count = totalRecords
        if (count == 0) return emptyList()

        val list = ArrayList<WordEntry>(count)
        val wordBytes = ByteArray(WORD_SIZE)

        synchronized(buf) {
            buf.position(0)
            repeat(count) {
                buf.get(wordBytes, 0, WORD_SIZE)
                val freq = buf.int
                val wordStr = parseNullTerminatedUtf8(wordBytes)
                if (wordStr.isNotEmpty()) {
                    list.add(WordEntry(wordStr, freq))
                }
            }
        }
        return list
    }

    /**
     * Converts a null-padded byte array into a UTF-8 String without including trailing 0x00 bytes.
     */
    private fun parseNullTerminatedUtf8(bytes: ByteArray): String {
        var length = 0
        while (length < bytes.size && bytes[length] != 0.toByte()) {
            length++
        }
        if (length == 0) return ""
        return String(bytes, 0, length, StandardCharsets.UTF_8)
    }
}
