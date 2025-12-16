package no.entur.abt.cuckoofilter

class WordArray(
    val byteArray: ByteArray,
    val wordSize: Int,
) {
    constructor(size: Int, wordSize: Int) : this(
        ByteArray(Math.ceilDiv(size * wordSize, 8)),
        wordSize,
    )

    val size: Int get() = (byteArray.size * 8) / wordSize

    fun get(index: Int): Int {
        val bitStart = index * wordSize
        val bitEnd = bitStart + wordSize

        val firstByte = bitStart / 8
        val lastByte = (bitEnd - 1) / 8

        var acc = 0
        for (i in firstByte..lastByte) {
            acc = (acc shl 8) or (byteArray[i].toInt() and 0xFF)
        }

        val totalBits = (lastByte - firstByte + 1) * 8
        val shiftRight = totalBits - (bitEnd - firstByte * 8)

        acc = acc shr shiftRight
        return acc and ((1 shl wordSize) - 1)
    }

    fun set(
        index: Int,
        value: Int,
    ) {
        require(value ushr wordSize == 0) {
            "Value $value does not fit in $wordSize bits"
        }

        val bitStart = index * wordSize
        val bitEnd = bitStart + wordSize

        val firstByte = bitStart / 8
        val lastByte = (bitEnd - 1) / 8

        var acc = 0
        for (i in firstByte..lastByte) {
            acc = (acc shl 8) or (byteArray[i].toInt() and 0xFF)
        }

        val totalBits = (lastByte - firstByte + 1) * 8
        val shiftRight = totalBits - (bitEnd - firstByte * 8)
        val mask = ((1 shl wordSize) - 1) shl shiftRight

        acc = (acc and mask.inv()) or (value shl shiftRight)

        for (i in lastByte downTo firstByte) {
            byteArray[i] = acc.toByte()
            acc = acc shr 8
        }
    }

    fun toByteArray(): ByteArray = byteArray.clone()
}
