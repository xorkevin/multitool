package dev.xorkevin.multitool

class StringSearchUtil {
    companion object {
        fun searchOrderedSubset(a: String, b: String): Pair<Int, Int>? {
            if (b.isEmpty()) {
                return 0 to a.length
            }

            var firstIdx = -1
            var idxA = 0
            loopB@ for (c in b) {
                while (idxA < a.length) {
                    val ca = a[idxA]
                    idxA++
                    if (c == ca) {
                        if (firstIdx < 0) {
                            firstIdx = idxA - 1
                        }
                        continue@loopB
                    }
                }
                return null
            }
            return firstIdx to a.lastIndexOf(b.last()) + 1
        }
    }
}
