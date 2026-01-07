package no.entur.abt.cuckoofilter

import com.google.common.hash.Funnel
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import kotlin.math.ceil

const val DEFAULT_FINGERPRINT_SIZE = 16
const val DEFAULT_BUCKET_SIZE = 4
const val DEFAULT_LOAD_FACTOR = 0.95
const val DEFAULT_MAX_KICKS = 500

val DEFAULT_HASHER = Hashing.murmur3_32_fixed()

/**
 * A space-efficient probabilistic data structure for approximate set membership queries.
 *
 * A cuckoo filter is similar to a Bloom filter but supports deletion. It uses a compact
 * hash table with cuckoo hashing to store fingerprints of elements.
 *
 * Like other probabilistic data structures, cuckoo filters may return false positives but
 * never false negatives. That is, querying for an item that was added will always return
 * `true`, but querying for an item that was not added may occasionally return `true`.
 *
 * ## Stable Binary Representation
 *
 * This implementation is designed to have a stable binary representation suitable for
 * serialization and network transport. Fingerprints are stored compactly in a byte array using
 * big-endian byte order. The underlying [storage] uses [WordArray], which packs fingerprints
 * bit-efficiently while maintaining consistent byte ordering across platforms.
 *
 * @param T the type of elements stored in the filter
 * @property funnel the funnel used to serialize elements for hashing
 * @property bucketSize the number of fingerprint slots per bucket
 * @property loadFactor the target load factor of the filter (between 0.0 and 1.0)
 * @property maxKicks the maximum number of cuckoo kicks to attempt when inserting
 * @property hashFunction the hash function used for fingerprinting and indexing
 * @property storage the underlying storage for fingerprints
 */
@Suppress("UnstableApiUsage")
class CuckooFilter<T>(
    val funnel: Funnel<T>,
    val bucketSize: Int,
    val loadFactor: Double,
    val maxKicks: Int,
    val hashFunction: HashFunction,
    val storage: WordArray,
) {
    /**
     * Creates a cuckoo filter with the specified minimum capacity.
     *
     * @param funnel the funnel used to serialize elements for hashing
     * @param minCapacity the minimum number of elements the filter should be able to store
     * @param fingerprintSize the size of fingerprints in bits (default: 16)
     * @param bucketSize the number of fingerprint slots per bucket (default: 4)
     * @param loadFactor the target load factor between 0.0 and 1.0 (default: 0.95)
     * @param maxKicks the maximum number of cuckoo kicks when inserting (default: 500)
     * @param hashFunction the hash function for fingerprinting and indexing (default: MurmurHash3)
     */
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
    }

    /**
     * The maximum number of fingerprints this filter can store.
     */
    val capacity: Int = storage.size

    /**
     * The number of buckets in this filter.
     */
    val bucketCount: Int = capacity / bucketSize

    /**
     * Adds an item to the filter.
     *
     * This operation may fail if the filter is full or if the maximum number of cuckoo kicks
     * is exceeded during insertion. In such cases, the filter should be resized or recreated
     * with a larger capacity.
     *
     * @param item the item to add
     * @return `true` if the item was successfully added, `false` if the filter is full
     */
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

    /**
     * Checks whether an item might be in the filter.
     *
     * This operation never produces false negatives but may produce false positives.
     * That is:
     * - If this returns `false`, the item was definitely not added to the filter
     * - If this returns `true`, the item was probably added to the filter
     *
     * @param item the item to check for membership
     * @return `true` if the item might be in the filter, `false` if it definitely is not
     */
    operator fun contains(item: T): Boolean {
        val fp = fingerprint(item)
        val i1 = index1(item)
        val i2 = index2(i1, fp)
        return bucketHas(i1, fp) || bucketHas(i2, fp)
    }

    /**
     * Removes an item from the filter.
     *
     * This operation removes one occurrence of the item's fingerprint from the filter.
     * If the same item was added multiple times, only one occurrence is removed.
     *
     * Note: Due to the probabilistic nature of the filter, removing an item that was
     * never added might succeed if there's a false positive collision.
     *
     * @param item the item to remove
     * @return `true` if an occurrence was found and removed, `false` otherwise
     */
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
        return Math.floorMod(h, bucketCount)
    }

    private fun fingerprint(item: T): Int {
        val h = hash(item)
        val shift = 32 - storage.wordBits
        return h ushr shift
    }

    private fun index2(
        i1: Int,
        fp: Int,
    ): Int {
        val h = hashFunction.hashInt(fp).asInt()
        return Math.floorMod(i1 xor h, bucketCount)
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
): Int = ceil(minCapacity / (bucketSize * loadFactor)).toInt()
