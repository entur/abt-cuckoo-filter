package no.entur.abt.cuckoofilter

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class WordArrayTest {
    @Test
    fun testWordArray() {
        class TestCase(
            val wordSize: Int,
            val words: IntArray,
            val byteArray: ByteArray,
        )

        val testCases =
            arrayOf(
                TestCase(
                    8,
                    intArrayOf(0xAA, 0xBB, 0xCC, 0xDD),
                    byteArrayOf(
                        0xAA.toByte(),
                        0xBB.toByte(),
                        0xCC.toByte(),
                        0xDD.toByte(),
                    ),
                ),
                TestCase(
                    12,
                    intArrayOf(0xAAB, 0xCCD, 0xEEF),
                    byteArrayOf(
                        0xAA.toByte(),
                        0xBC.toByte(),
                        0xCD.toByte(),
                        0xEE.toByte(),
                        0xF0.toByte(),
                    ),
                ),
                TestCase(
                    16,
                    intArrayOf(0xAABB, 0xCCDD, 0xEEFF),
                    byteArrayOf(
                        0xAA.toByte(),
                        0xBB.toByte(),
                        0xCC.toByte(),
                        0xDD.toByte(),
                        0xEE.toByte(),
                        0xFF.toByte(),
                    ),
                ),
            )

        for (testCase in testCases) {
            val wordArray = WordArray(testCase.words.size, testCase.wordSize)

            for ((index, value) in testCase.words.withIndex()) {
                wordArray.set(index, value)
            }

            for ((index, value) in testCase.words.withIndex()) {
                Assertions.assertEquals(value, wordArray.get(index))
            }

            val byteArray = wordArray.toByteArray()

            Assertions.assertArrayEquals(
                testCase.byteArray,
                byteArray,
                byteArray.toHexString(),
            )

            val wordArray2 = WordArray(byteArray, testCase.wordSize)

            for ((index, value) in testCase.words.withIndex()) {
                Assertions.assertEquals(value, wordArray2.get(index))
            }
        }
    }
}
