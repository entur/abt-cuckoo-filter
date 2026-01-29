package no.entur.abt.cuckoofilter

/**
 * A compact array for storing fixed-size integers (words) efficiently packed in a byte array.
 *
 * This class provides bit-level storage of integers with a specified bit width, packing multiple
 * words into a single byte array to minimize memory usage. Words are stored using big-endian byte
 * order.
 *
 * @property byteArray the underlying byte array storage
 * @property wordBits the size of each word in bits (must be between 1 and 32)
 */
class WordArray(
    val byteArray: ByteArray,
    val wordBits: Int,
) {
    /**
     * Creates a WordArray with the specified number of words.
     *
     * The underlying byte array is sized to hold exactly [size] words of [wordBits] bits each,
     * rounded up to the nearest byte boundary.
     *
     * Note: Math.ceilDiv() was introduced in Java 18 and is not available in Java 8.
     * Therefore, we use the formula (a + b - 1) / b to compute ceiling division:
     * (size * wordBits + 7) / 8 is equivalent to Math.ceilDiv(size * wordBits, 8).
     * This ensures we allocate enough bytes to store all bits without truncation.
     *
     * @param size the number of words to allocate space for
     * @param wordBits the size of each word in bits
     */
    constructor(size: Int, wordBits: Int) : this(
        ByteArray((size * wordBits) ceilDiv 8),
        wordBits,
    )

    /**
     * The number of words that can be stored in this array.
     */
    val size: Int get() = (byteArray.size * 8) / wordBits

    /**
     * Retrieves the word at the specified index.
     *
     * Words are extracted from the underlying byte array using big-endian byte order.
     * The returned value will be in the range [0, 2^wordBits - 1].
     *
     * @param index the index of the word to retrieve
     * @return the word value at the specified index
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    fun get(index: Int): Int {
        val bitStart = index * wordBits
        val bitEnd = bitStart + wordBits

        val firstByte = bitStart / 8
        val lastByte = (bitEnd - 1) / 8

        var acc = 0
        for (i in firstByte..lastByte) {
            acc = (acc shl 8) or (byteArray[i].toInt() and 0xFF)
        }

        val totalBits = (lastByte - firstByte + 1) * 8
        val shiftRight = totalBits - (bitEnd - firstByte * 8)

        acc = acc shr shiftRight
        return acc and ((1 shl wordBits) - 1)
    }

    /**
     * Sets the word at the specified index to the given value.
     *
     * The value is stored in the underlying byte array using big-endian byte order.
     * The value must fit within [wordBits] bits.
     *
     * @param index the index of the word to set
     * @param value the value to store (must be in range [0, 2^wordBits - 1])
     * @throws IllegalArgumentException if the value doesn't fit in [wordBits] bits
     * @throws IndexOutOfBoundsException if the index is out of bounds
     */
    fun set(
        index: Int,
        value: Int,
    ) {
        require(value ushr wordBits == 0) {
            "Value $value does not fit in $wordBits bits"
        }

        val bitStart = index * wordBits
        val bitEnd = bitStart + wordBits

        val firstByte = bitStart / 8
        val lastByte = (bitEnd - 1) / 8

        var acc = 0
        for (i in firstByte..lastByte) {
            acc = (acc shl 8) or (byteArray[i].toInt() and 0xFF)
        }

        val totalBits = (lastByte - firstByte + 1) * 8
        val shiftRight = totalBits - (bitEnd - firstByte * 8)
        val mask = ((1 shl wordBits) - 1) shl shiftRight

        acc = (acc and mask.inv()) or (value shl shiftRight)

        for (i in lastByte downTo firstByte) {
            byteArray[i] = acc.toByte()
            acc = acc shr 8
        }
    }

    /**
     * Returns a copy of the underlying byte array.
     */
    fun toByteArray(): ByteArray = byteArray.clone()
}
