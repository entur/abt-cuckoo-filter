package no.entur.abt.cuckoofilter

import com.google.common.hash.Funnels
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.random.Random

const val VALID_IDS_SEED: Int = 12345
const val INVALID_IDS_SEED: Int = 54321

@Suppress("UnstableApiUsage")
class CuckooFilterTest {
    @Test
    fun testDefaultHasher() {
        val ids = createIds(10, VALID_IDS_SEED)

        val hashes =
            listOf(
                3423625907,
                1046447328,
                1340575052,
                1412230738,
                1418597571,
                148405652,
                1877360513,
                3374663699,
                2578582238,
                1018919995,
            )

        for ((id, hash) in ids.zip(hashes)) {
            Assertions.assertEquals(
                DEFAULT_HASHER.hashBytes(id).asInt(),
                hash.toInt(),
            )
        }
    }

    @Test
    fun testAddContainsRemove() {
        val cuckooFilter = CuckooFilter(Funnels.byteArrayFunnel(), 100)
        val n = (cuckooFilter.capacity * 0.9).toInt()
        val ids = createIds(n, VALID_IDS_SEED)

        for (id in ids) {
            Assertions.assertFalse(id in cuckooFilter, "Expected filter to be empty")
        }

        for (id in ids) {
            Assertions.assertTrue(cuckooFilter.add(id))
        }

        for (id in ids) {
            Assertions.assertTrue(id in cuckooFilter, "Expected $id to have been added to filter")
        }

        for (id in ids) {
            Assertions.assertTrue(cuckooFilter.remove(id))
        }

        for (id in ids) {
            Assertions.assertFalse(id in cuckooFilter, "Expected $id to have been removed from filter")
        }
    }

    @Test
    fun testFalsePositiveRate() {
        val cuckooFilter = CuckooFilter(Funnels.byteArrayFunnel(), 10_000)

        // Insert enough items to reach the target load factor
        val n = (cuckooFilter.capacity * cuckooFilter.loadFactor).toInt()
        createIds(n, VALID_IDS_SEED).forEach { cuckooFilter.add(it) }

        // Produce enough random samples to get a decent estimated FPR
        val ids = createIds(1_000_000, INVALID_IDS_SEED)
        val falsePositiveRate = ids.count { it in cuckooFilter } / ids.size.toDouble()

        // This implementation with the default parameters should produce < 0.1% FPR
        Assertions.assertTrue(falsePositiveRate < 0.001)
    }

    @Test
    fun testCalculateBucketCount() {
        data class TestCase(
            val maxSize: Int,
            val bucketSize: Int,
            val loadFactor: Double,
            val expectedBucketCount: Int,
        )

        val testCases =
            listOf(
                TestCase(100, 4, 0.95, 27),
                TestCase(1000, 4, 0.95, 264),
                TestCase(10000, 4, 0.95, 2632),
                TestCase(100000, 4, 0.95, 26316),
            )

        for ((maxSize, bucketSize, loadFactor, expectedBucketCount) in testCases) {
            val bucketCount = calculateBucketCount(maxSize, bucketSize, loadFactor)

            Assertions.assertEquals(
                expectedBucketCount,
                bucketCount,
                "Unexpected bucket count for maxSize=$maxSize, bucketSize=$bucketSize, loadFactor=$loadFactor",
            )
        }
    }

    @Test
    fun testConstructorRequirements() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            CuckooFilter(
                Funnels.byteArrayFunnel(),
                3,
                DEFAULT_LOAD_FACTOR,
                DEFAULT_MAX_KICKS,
                DEFAULT_HASHER,
                WordArray(32, DEFAULT_FINGERPRINT_SIZE),
            )
        }

        Assertions.assertThrows(IllegalArgumentException::class.java) {
            CuckooFilter(
                Funnels.byteArrayFunnel(),
                DEFAULT_BUCKET_SIZE,
                DEFAULT_LOAD_FACTOR,
                DEFAULT_MAX_KICKS,
                DEFAULT_HASHER,
                WordArray(123, DEFAULT_FINGERPRINT_SIZE),
            )
        }
    }

    private fun createIds(
        n: Int,
        seed: Int,
    ): List<ByteArray> {
        val random = Random(seed)
        return (0..<n).map { random.nextBytes(16) }
    }
}
