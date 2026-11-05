package com.example.comexamplet9keyboard

import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.net.URL

// Not: Aşağıdaki iki test geliştirici aracıdır; asset dosyalarının üzerine yazar (ve biri internete bağlanır).
// Rutin `gradlew test` çalıştırmalarında sözlüğü bozmaması için @Ignore'dur. Gerektiğinde bilerek açın.
class ExampleUnitTest {
    @Ignore("Geliştirici aracı: words_tr.txt dosyasının üzerine yazar ve internete bağlanır")
    @Test
    fun mergeDictionary() {
        val assetFile = File("src/main/assets/words_tr.txt")
        if (!assetFile.exists()) return

        val existingWords = mutableSetOf<String>()
        val lines = assetFile.readLines()
        val outputLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val word = trimmed.split(Regex("\\s+"))[0].lowercase()
                existingWords.add(word)
                outputLines.add(trimmed)
            }
        }

        try {
            val url = URL("https://raw.githubusercontent.com/CanNuhlar/Turkce-Kelime-Listesi/master/turkce_kelime_listesi.txt")
            val remoteText = url.readText()
            val tokens = remoteText.split(Regex("\\s+")).map { it.trim().lowercase() }.filter { it.length > 1 && it.all { c -> c.isLetter() } }
            
            var addedCount = 0
            for (token in tokens) {
                if (!existingWords.contains(token)) {
                    existingWords.add(token)
                    outputLines.add(token)
                    addedCount++
                }
            }
            println("Added $addedCount new words from CanNuhlar dictionary.")
            assetFile.writeText(outputLines.joinToString("\n"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Ignore("Geliştirici aracı: words_tr.bin dosyasının üzerine yazar")
    @Test
    fun generateBinaryDictionary() {
        val txtFile = File("src/main/assets/words_tr.txt")
        val binFile = File("src/main/assets/words_tr.bin")
        if (!txtFile.exists()) return

        val RECORD_SIZE = 36
        val WORD_SIZE = 32

        val lines = txtFile.readLines()
        val outStream = binFile.outputStream().buffered()

        var count = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val parts = trimmed.split(Regex("\\s+"))
            val word = parts[0].lowercase()
            val freq = if (parts.size > 1) parts[1].toLongOrNull()?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0 else 0

            val wordBytes = word.toByteArray(Charsets.UTF_8)
            val paddedBytes = ByteArray(WORD_SIZE) { 0 }
            val copyLen = minOf(wordBytes.size, WORD_SIZE)
            System.arraycopy(wordBytes, 0, paddedBytes, 0, copyLen)

            val buffer = java.nio.ByteBuffer.allocate(RECORD_SIZE)
            buffer.order(java.nio.ByteOrder.BIG_ENDIAN)
            buffer.put(paddedBytes)
            buffer.putInt(freq)

            outStream.write(buffer.array())
            count++
        }
        outStream.flush()
        outStream.close()
        println("Binary dictionary generated successfully at ${binFile.absolutePath} with $count records.")
    }
}