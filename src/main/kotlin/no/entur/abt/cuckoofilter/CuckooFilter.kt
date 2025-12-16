package no.entur.abt.cuckoofilter

import com.google.common.hash.Funnel
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

const val DEFAULT_FINGERPRINT_SIZE = 16
const val DEFAULT_BUCKET_SIZE = 4
const val DEFAULT_LOAD_FACTOR = 0.95
const val DEFAULT_MAX_KICKS = 500

val DEFAULT_HASHER = Hashing.murmur3_32_fixed()

@Suppress("UnstableApiUsage")
class CuckooFilter<T>(
    val funnel: Funnel<T>,
    val bucketSize: Int,
    val loadFactor: Double,
    val maxKicks: Int,
    val hashFunction: HashFunction,
    val storage: WordArray,
) {
    constructor(
        funnel: Funnel<T>,
        minCapacity: Int,
        fingerprintSize: Int = DEFAULT_FINGERPRINT_SIZE,
        bucketSize: Int = DEFAULT_BUCKET_SIZE,
        loadFactor: Double = DEFAULT_LOAD_FACTOR,
        maxKicks: Int = DEFAULT_MAX_KICKS,
        hashFunction: HashFunction = DEFAULT_HASHER,
    ) : this(
        funnel,
        bucketSize,
        loadFactor,
        maxKicks,
        hashFunction,
        createStorage(minCapacity, fingerprintSize, bucketSize, loadFactor),
    )

    init {
        require(storage.size % bucketSize == 0) {
            "Storage size ${storage.size} is not divisible by bucket size $bucketSize"
        }

        val bucketCount = storage.size / bucketSize

        require(isPowerOfTwo(bucketCount)) {
            "Bucket count $bucketCount is not a power of two"
        }
    }

    val capacity: Int = storage.size
    val bucketCount: Int = capacity / bucketSize

    fun add(item: T): Boolean {
        val fp = fingerprint(item)
        val i1 = index1(item)
        val i2 = index2(i1, fp)

        if (insertIntoBucket(i1, fp)) {
            return true
        }

        if (insertIntoBucket(i2, fp)) {
            return true
        }

        var index = i1
        var currentFp = fp

        for (kick in 0 until maxKicks) {
            val slot = kick % bucketSize
            val pos = bucketOffset(index) + slot

            val oldFp = storage.get(pos)
            storage.set(pos, currentFp)
            currentFp = oldFp

            index = index2(index, currentFp)

            if (insertIntoBucket(index, currentFp)) {
                return true
            }
        }

        return false
    }

    operator fun contains(item: T): Boolean {
        val fp = fingerprint(item)
        val i1 = index1(item)
        val i2 = index2(i1, fp)
        return bucketHas(i1, fp) || bucketHas(i2, fp)
    }

    fun remove(item: T): Boolean {
        val fp = fingerprint(item)
        val i1 = index1(item)
        val i2 = index2(i1, fp)

        if (deleteFromBucket(i1, fp)) {
            return true
        }

        if (deleteFromBucket(i2, fp)) {
            return true
        }

        return false
    }

    private fun hash(item: T): Int {
        val hasher = hashFunction.newHasher()
        funnel.funnel(item, hasher)
        return hasher.hash().asInt()
    }

    private fun index1(item: T): Int {
        val h = hash(item)
        return h and (bucketCount - 1)
    }

    private fun fingerprint(item: T): Int {
        val h = hash(item)
        val shift = 32 - storage.wordSize
        return h ushr shift
    }

    private fun index2(
        i1: Int,
        fp: Int,
    ): Int {
        val h = hashFunction.hashInt(fp).asInt()
        return (i1 xor h) and (bucketCount - 1)
    }

    private fun bucketOffset(bucket: Int): Int = bucket * bucketSize

    private fun insertIntoBucket(
        index: Int,
        fp: Int,
    ): Boolean {
        val base = bucketOffset(index)

        for (i in 0 until bucketSize) {
            val pos = base + i

            if (storage.get(pos) == 0) {
                storage.set(pos, fp)
                return true
            }
        }

        return false
    }

    private fun deleteFromBucket(
        index: Int,
        fp: Int,
    ): Boolean {
        val base = bucketOffset(index)

        for (i in 0 until bucketSize) {
            val pos = base + i
            if (storage.get(pos) == fp) {
                storage.set(pos, 0)
                return true
            }
        }

        return false
    }

    private fun bucketHas(
        index: Int,
        fp: Int,
    ): Boolean {
        val base = bucketOffset(index)

        for (i in 0 until bucketSize) {
            val pos = base + i
            if (storage.get(pos) == fp) {
                return true
            }
        }

        return false
    }
}

private fun createStorage(
    minCapacity: Int,
    fingerprintSize: Int,
    bucketSize: Int,
    loadFactor: Double,
): WordArray {
    val bucketCount = calculateBucketCount(minCapacity, bucketSize, loadFactor)
    return WordArray(bucketCount * bucketSize, fingerprintSize)
}

internal fun calculateBucketCount(
    minCapacity: Int,
    bucketSize: Int,
    loadFactor: Double,
): Int {
    val needed = minCapacity / (bucketSize * loadFactor)
    val exp = ceil(log2(needed)).toInt()
    return 2.0.pow(exp).toInt()
}

private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0
