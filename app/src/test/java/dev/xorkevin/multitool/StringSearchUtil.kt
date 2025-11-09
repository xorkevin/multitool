package dev.xorkevin.multitool

import org.junit.Assert
import org.junit.Test

class StringSearchUtilTest {
    @Test
    fun searchOrderedSubset() {
        for (testCase in listOf(
            Triple("a", "a", 0 to 1),
            Triple("a", "b", null),
            Triple("a", "", 0 to 1),
            Triple("", "a", null),
            Triple("abc", "ac", 0 to 3),
            Triple("abcdef", "bce", 1 to 5),
            Triple("abab", "ab", 0 to 4),
            Triple("abab", "abc", null),
        )) {
            Assert.assertEquals(
                testCase.third,
                StringSearchUtil.searchOrderedSubset(testCase.first, testCase.second)
            )
        }
    }
}
