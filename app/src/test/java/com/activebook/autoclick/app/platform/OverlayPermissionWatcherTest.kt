package com.activebook.autoclick.app.platform

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayPermissionWatcherTest {

    @Test
    fun `AppOps callback triggers onGranted immediately once and closes watcher`() =
        runTest(StandardTestDispatcher()) {
            var granted = false
            var callback: (() -> Unit)? = null
            var closed = false
            var onGrantedCalls = 0

            val watcher = OverlayPermissionWatcher(
                scope = this,
                hasOverlayPermission = { granted },
                registerAppOpWatcher = { cb ->
                    callback = cb
                    AutoCloseable { closed = true }
                },
                onGranted = { onGrantedCalls += 1 },
            )

            watcher.start(withFallbackPoll = true)
            runCurrent()
            assertFalse(closed)

            granted = true
            callback?.invoke()
            callback?.invoke()
            runCurrent()

            assertEquals(1, onGrantedCalls)
            assertTrue(closed)
        }

    @Test
    fun `fallback poll triggers onGranted when AppOps callback does not fire`() =
        runTest(StandardTestDispatcher()) {
            var granted = false
            var closed = false
            var onGrantedCalls = 0

            val watcher = OverlayPermissionWatcher(
                scope = this,
                hasOverlayPermission = { granted },
                registerAppOpWatcher = { AutoCloseable { closed = true } },
                onGranted = { onGrantedCalls += 1 },
                pollIntervalMs = 250L,
                pollTimeoutMs = 2_000L,
            )

            watcher.start(withFallbackPoll = true)
            advanceTimeBy(500L)
            runCurrent()
            assertEquals(0, onGrantedCalls)

            granted = true
            advanceTimeBy(250L)
            runCurrent()

            assertEquals(1, onGrantedCalls)
            assertTrue(closed)
        }

    @Test
    fun `fallback poll stops cleanly at timeout without triggering onGranted`() =
        runTest(StandardTestDispatcher()) {
            var granted = false
            var onGrantedCalls = 0

            val watcher = OverlayPermissionWatcher(
                scope = this,
                hasOverlayPermission = { granted },
                registerAppOpWatcher = { AutoCloseable { } },
                onGranted = { onGrantedCalls += 1 },
                pollIntervalMs = 250L,
                pollTimeoutMs = 1_000L,
            )

            watcher.start(withFallbackPoll = true)
            advanceTimeBy(1_500L)
            runCurrent()

            granted = true
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(0, onGrantedCalls)
        }

    @Test
    fun `cancel prevents subsequent callbacks from firing onGranted`() =
        runTest(StandardTestDispatcher()) {
            var granted = false
            var callback: (() -> Unit)? = null
            var closed = false
            var onGrantedCalls = 0

            val watcher = OverlayPermissionWatcher(
                scope = this,
                hasOverlayPermission = { granted },
                registerAppOpWatcher = { cb ->
                    callback = cb
                    AutoCloseable { closed = true }
                },
                onGranted = { onGrantedCalls += 1 },
            )

            watcher.start(withFallbackPoll = true)
            watcher.cancel()
            assertTrue(closed)

            granted = true
            callback?.invoke()
            advanceTimeBy(1_000L)
            runCurrent()

            assertEquals(0, onGrantedCalls)
        }

    @Test
    fun `start is a no-op when overlay permission is already granted`() =
        runTest(StandardTestDispatcher()) {
            var registered = false
            var onGrantedCalls = 0

            val watcher = OverlayPermissionWatcher(
                scope = this,
                hasOverlayPermission = { true },
                registerAppOpWatcher = {
                    registered = true
                    AutoCloseable { }
                },
                onGranted = { onGrantedCalls += 1 },
            )

            watcher.start(withFallbackPoll = true)
            runCurrent()

            assertFalse(registered)
            assertEquals(0, onGrantedCalls)
        }
}
