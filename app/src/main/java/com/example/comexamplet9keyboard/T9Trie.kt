package com.example.comexamplet9keyboard

// ==========================================
// OPTİMİZE EDİLMİŞ RAM VE CPU DOSTU T9 TRIE
// ==========================================
@JvmInline
value class T9Code(val value: String)

@JvmInline
value class WordIndex(val id: Int)

class T9Trie {
    class Node {
        // HashMap nesne maliyetini önlemek için sabit 10 elemanlı dizi (0-9 tuşları)
        val children = arrayOfNulls<Node>(10)

        // Boxing maliyetini önlemek için IntArray
        var candidateIndices = IntArray(0)

        fun addIndex(index: Int) {
            if (!candidateIndices.contains(index)) {
                candidateIndices += index
            }
        }
    }

    val root = Node()

    fun insert(code: T9Code, wordIndex: WordIndex) {
        var curr = root
        val str = code.value
        for (ch in str) {
            val d = ch.digitToIntOrNull() ?: continue
            if (d !in 0..9) continue

            var next = curr.children[d]
            if (next == null) {
                next = Node()
                curr.children[d] = next
            }
            curr = next
        }
        curr.addIndex(wordIndex.id)
    }

    fun search(codePrefix: String, maxResults: Int = 100): IntArray {
        var curr = root
        for (ch in codePrefix) {
            val d = ch.digitToIntOrNull() ?: return IntArray(0)
            if (d !in 0..9) return IntArray(0)

            curr = curr.children[d] ?: return IntArray(0)
        }

        val result = IntArray(maxResults)
        var count = 0

        fun collect(node: Node) {
            for (idx in node.candidateIndices) {
                if (count >= maxResults) return
                // Not: result sıfırlarla başlar; contains() kontrolü 0 numaralı kelimeyi hep atlıyordu.
                // Bir kelime ağaçta tek bir düğümde durduğundan (Node.addIndex tekrarı engeller) ek kontrol gerekmez.
                result[count++] = idx
            }
            for (child in node.children) {
                if (child != null && count < maxResults) {
                    collect(child)
                }
            }
        }

        collect(curr)
        return result.copyOf(count)
    }
}
