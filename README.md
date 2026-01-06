# Cuckoo Filter

A space-efficient probabilistic data structure for approximate set membership queries in Kotlin.

## Overview

A cuckoo filter is similar to a Bloom filter but supports deletion and provides better lookup performance. It uses a compact hash table with cuckoo hashing to store fingerprints of elements.

Like other probabilistic data structures, cuckoo filters may return **false positives** but **never false negatives**. That is:

- Querying for an item that was added will always return `true`
- Querying for an item that was not added may occasionally return `true`

## Features

- **Fast lookup and insertion** with O(1) expected time complexity
- **Deletion support** unlike traditional Bloom filters
- **Space-efficient** storage using compact fingerprints
- **Stable binary representation** suitable for serialization and network transport
- **Configurable parameters** for fingerprint size, bucket size, and load factor

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("no.entur:abt-cuckoo-filter:1.0-SNAPSHOT")
}
```

## Usage

### Basic Operations

```kotlin
import com.google.common.hash.Funnels
import no.entur.abt.cuckoofilter.CuckooFilter

// Create a filter with minimum capacity
val filter = CuckooFilter(Funnels.byteArrayFunnel(), minCapacity = 1000)

// Add items
val item = "example".toByteArray()
filter.add(item) // returns true if successful

// Check membership
if (item in filter) {
    println("Item might be in the filter")
}

// Remove items
filter.remove(item) // returns true if found and removed
```

## Requirements

- Kotlin 2.2+
- JVM 21+
- Google Guava (for hash functions and funnels)

## License

See [LICENSE.txt](LICENSE.txt)
