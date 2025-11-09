package dev.xorkevin.multitool

import kotlin.math.max

class StringSearchUtil {
    companion object {
        fun searchOrderedSubset(a: String, b: String): Pair<Int, Int> {
            if (b.length > a.length) {
                return -1 to -1
            }
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
                return -1 to -1
            }
            return firstIdx to a.lastIndexOf(b.last()) + 1
        }

        const val SCORE_MATCH = 4
        const val GAP_PENALTY = 1
        const val BONUS_BOUNDARY = 2
        const val BONUS_FIRST_CHAR_MULTIPLIER = 2

        fun scoreSmithWaterman(a: String, b: String): Int {
            if (b.isEmpty()) {
                return 0
            }
            if (b.length == 1) {
                return (SCORE_MATCH + BONUS_BOUNDARY) * BONUS_FIRST_CHAR_MULTIPLIER
            }

            var maxScore = 0
            val scores = IntArray(b.length)
            val reached = BooleanArray(b.length)
            var prevIsBoundary = true
            for (ca in a) {
                for ((col, c) in b.withIndex()) {
                    val gapScore = max(
                        scores[col] - GAP_PENALTY,
                        0,
                    )
                    val consecutiveScore = if ((col == 0 || reached[col - 1]) && c == ca) {
                        var incr = SCORE_MATCH
                        if (prevIsBoundary) {
                            incr += BONUS_BOUNDARY
                        }
                        if (col == 0) {
                            incr * BONUS_FIRST_CHAR_MULTIPLIER
                        } else {
                            scores[col - 1] + incr
                        }
                    } else {
                        0
                    }
                    val score = if (consecutiveScore > gapScore) {
                        reached[col] = true
                        consecutiveScore
                    } else {
                        gapScore
                    }
                    scores[col] = score
                }
                maxScore = max(maxScore, scores.last())
                prevIsBoundary = ca == '/' || ca.isWhitespace()
            }
            return maxScore
        }

        fun searchSmithWaterman(a: String, b: String): Int {
            val (begin, end) = searchOrderedSubset(a, b)
            if (begin < 0) {
                return -1
            }
            return scoreSmithWaterman(a.substring(begin, end), b)
        }
    }
}
