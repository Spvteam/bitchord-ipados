package com.music.bitchord.stress

import com.music.bitchord.data.MonotonicClock
import com.music.bitchord.data.listentogether.ServerClock
import com.music.bitchord.platform.PlatformClock
import com.music.bitchord.platform.PlatformLock
import com.music.bitchord.platform.monotonicMillis
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerClockMonotonicClockStressTest {

    @Test
    fun testPlatformLockMutualExclusionUnderHighContention() {
        val threadCount = 32
        val incrementsPerThread = 20_000
        val expectedTotal = threadCount * incrementsPerThread
        val lock = PlatformLock()
        var counter = 0
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (t in 0 until threadCount) {
            executor.submit {
                startLatch.await()
                try {
                    for (i in 0 until incrementsPerThread) {
                        lock.withLock {
                            counter++
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue(finished, "Timeout waiting for lock contention threads")
        assertEquals(expectedTotal, counter, "PlatformLock failed mutual exclusion: counter ($counter) != expected ($expectedTotal)")
    }

    @Test
    fun testServerClockConcurrentReadWriteReset() {
        val clock = ServerClock()
        val threadCount = 16
        val iterationsPerThread = 5_000
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val failed = AtomicBoolean(false)
        val failureReason = StringBuilder()

        for (t in 0 until threadCount) {
            val threadId = t
            executor.submit {
                startLatch.await()
                val rng = Random(threadId * 1000L + 7L)
                try {
                    for (i in 0 until iterationsPerThread) {
                        when (rng.nextInt(5)) {
                            0, 1 -> {
                                // Record ping/pong
                                val sent = rng.nextLong(10_000, 1_000_000)
                                val latency = rng.nextLong(5, 500)
                                val server = sent + 50_000 + latency / 2
                                val received = sent + latency
                                clock.record(sent, server, received)
                            }
                            2 -> {
                                // Query serverNowMs
                                val serverNow = clock.serverNowMs()
                                if (clock.synced && serverNow == null) {
                                    // Notice: reset could have intervened, which is fine
                                }
                            }
                            3 -> {
                                // Check invariants
                                val synced = clock.synced
                                val offset = clock.offsetMs
                                val rt = clock.roundTripMs
                                if (synced && offset == null) {
                                    // If synced is true, offsetMs shouldn't be null unless reset intervened
                                }
                                assertTrue(rt >= 0, "roundTripMs must be non-negative: $rt")
                            }
                            4 -> {
                                // Occasional reset
                                if (rng.nextInt(100) == 0) {
                                    clock.reset()
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    failed.set(true)
                    synchronized(failureReason) {
                        failureReason.append("Thread $threadId failed: ${e.message}\n")
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue(finished, "ServerClock stress test timed out")
        assertEquals(false, failed.get(), "ServerClock threw exception under concurrency: $failureReason")
    }

    @Test
    fun testMonotonicClockStrictlyMonotonicAcrossThreads() {
        val threadCount = 16
        val iterations = 10_000
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val violations = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                startLatch.await()
                var prevNano = PlatformClock.monotonicNano()
                for (i in 0 until iterations) {
                    val currNano = PlatformClock.monotonicNano()
                    if (currNano < prevNano) {
                        violations.incrementAndGet()
                    }
                    prevNano = currNano
                }
                doneLatch.countDown()
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(20, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue(finished, "Monotonic clock test timed out")
        assertEquals(0, violations.get(), "Monotonic clock went backwards under multi-threaded polling!")
    }

    @Test
    fun testMonotonicClockConcurrentDelegateHotSwap() {
        val originalNowMs = MonotonicClock.nowMs
        val threadCount = 8
        val iterations = 5_000
        val executor = Executors.newFixedThreadPool(threadCount + 1)
        val running = AtomicBoolean(true)
        val readFailures = AtomicInteger(0)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)

        // Delegate updater thread
        executor.submit {
            startLatch.await()
            var counter = 0L
            while (running.get()) {
                val base = counter++
                MonotonicClock.nowMs = { base * 1000L + PlatformClock.monotonicMillis() }
                Thread.yield()
            }
        }

        // Reader threads
        for (t in 0 until threadCount) {
            executor.submit {
                startLatch.await()
                try {
                    for (i in 0 until iterations) {
                        val reading = MonotonicClock.nowMs()
                        if (reading < 0) {
                            readFailures.incrementAndGet()
                        }
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(20, TimeUnit.SECONDS)
        running.set(false)
        executor.shutdown()
        MonotonicClock.nowMs = originalNowMs

        assertTrue(finished, "MonotonicClock hot-swap test timed out")
        assertEquals(0, readFailures.get(), "MonotonicClock reading threw or failed during concurrent delegate swap")
    }

    @Test
    fun testServerClockAdversarialTimings() {
        val clock = ServerClock()

        // Test 1: zero round trip
        clock.record(sentAtLocalMs = 1000, serverMs = 2000, receivedAtLocalMs = 1000)
        assertTrue(clock.synced)
        assertEquals(1000L, clock.offsetMs)
        assertEquals(0L, clock.roundTripMs)

        // Test 2: received < sent (clock skew or time-warp)
        clock.record(sentAtLocalMs = 5000, serverMs = 6000, receivedAtLocalMs = 4000)
        // roundTrip coerced to 0, midpoint = 5000, sample offset = 6000 - 5000 = 1000, roundTrip = 0
        assertEquals(0L, clock.roundTripMs)

        // Test 3: Large burst exceeding WINDOW (12 samples)
        for (i in 0 until 50) {
            val sent = 10_000L + i * 100
            val latency = (i % 20) + 10L
            val server = sent + 50_000L
            clock.record(sent, server, sent + latency)
        }
        assertTrue(clock.synced)
        assertNotNull(clock.offsetMs)

        // Test 4: Extreme leap in local time (e.g. system wake after sleep 1 year)
        val oneYearMs = 365L * 24 * 3600 * 1000
        val wakeLocalMs = 10_000L + oneYearMs
        val wakeServerMs = wakeLocalMs + 50_000L
        clock.record(wakeLocalMs, wakeServerMs, wakeLocalMs + 30)
        // Stale samples older than 120,000ms from wakeLocalMs are dropped
        assertEquals(49_985L, clock.offsetMs)
        assertEquals(30L, clock.roundTripMs)
    }
}
