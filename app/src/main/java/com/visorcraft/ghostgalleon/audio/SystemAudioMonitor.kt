package com.visorcraft.ghostgalleon.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-shared push monitor for [AudioManager.RINGER_MODE_CHANGED_ACTION].
 *
 * Subscribes once on `applicationContext` (off the UI thread) so both [SoundEngine]
 * and [AmbientEngine] receive instant ringer-mode transitions with **0 polling
 * Binder IPCs** during normal playback.
 *
 * All push state and listener callbacks are pure-JVM testable via [publishRingerState]
 * and [setPushActiveForTest].
 */
object SystemAudioMonitor {

    private val registerLock = Any()

    @Volatile
    private var _ringerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL

    @Volatile
    private var _pushActive: Boolean = false

    @Volatile
    private var _seeded: Boolean = false

    private var registeredContext: Context? = null
    private var receiver: BroadcastReceiver? = null
    private val listeners = CopyOnWriteArrayList<(AudioPolicy.RingerState) -> Unit>()

    /** Latest known ringer state (updated via push broadcast or initial off-UI seed). */
    val ringerState: AudioPolicy.RingerState get() = _ringerState

    /** True once [AudioManager.RINGER_MODE_CHANGED_ACTION] broadcast receiver is active. */
    val isPushActive: Boolean get() = _pushActive

    /** True once at least one authoritative ringer value has been observed. */
    val isSeeded: Boolean get() = _seeded

    /**
     * Subscribes [listener] to push ringer-mode transitions. Safe to call from any thread
     * without issuing any Binder IPCs.
     */
    fun addListener(listener: (AudioPolicy.RingerState) -> Unit) {
        listeners.addIfAbsent(listener)
    }

    /** Unsubscribes [listener] from push ringer-mode transitions. */
    fun removeListener(listener: (AudioPolicy.RingerState) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Ensures the process-wide `RINGER_MODE_CHANGED_ACTION` receiver is registered on
     * `applicationContext` and seeds the initial ringer mode from the sticky broadcast
     * (or a single off-UI `AudioManager.ringerMode` read).
     *
     * MUST be called off the UI thread (e.g. `AUDIO_SYNTH` or `gg-ambient-stream`).
     * Safe to call repeatedly, in unit tests, or during teardown.
     *
     * @return `true` if push broadcasts are active, `false` if fallback polling is needed.
     */
    fun ensureRegistered(context: Context): Boolean {
        if (_pushActive) return true
        val initialState: AudioPolicy.RingerState?
        val notifyListeners: Boolean
        synchronized(registerLock) {
            if (_pushActive) return true
            val app = runCatching { context.applicationContext ?: context }.getOrDefault(context)
            val rx = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != AudioManager.RINGER_MODE_CHANGED_ACTION) return
                    val raw = if (intent.hasExtra(AudioManager.EXTRA_RINGER_MODE)) {
                        intent.getIntExtra(
                            AudioManager.EXTRA_RINGER_MODE,
                            SoundEngineLogic.ANDROID_RINGER_MODE_NORMAL,
                        )
                    } else {
                        readRingerModeFallback(app)
                    }
                    publishRingerState(SoundEngineLogic.mapRingerMode(raw))
                }
            }
            val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
            val stickyResult = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(rx, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    app.registerReceiver(rx, filter)
                }
            }
            if (stickyResult.isSuccess) {
                registeredContext = app
                receiver = rx
                _pushActive = true
                val sticky = stickyResult.getOrNull()
                val raw = if (sticky != null && sticky.hasExtra(AudioManager.EXTRA_RINGER_MODE)) {
                    sticky.getIntExtra(
                        AudioManager.EXTRA_RINGER_MODE,
                        SoundEngineLogic.ANDROID_RINGER_MODE_NORMAL,
                    )
                } else {
                    readRingerModeFallback(app)
                }
                val mapped = SoundEngineLogic.mapRingerMode(raw)
                val changed = _ringerState != mapped
                _ringerState = mapped
                _seeded = true
                initialState = mapped
                notifyListeners = changed
            } else {
                val raw = readRingerModeFallback(app)
                val mapped = SoundEngineLogic.mapRingerMode(raw)
                val changed = _ringerState != mapped
                _ringerState = mapped
                _seeded = true
                initialState = mapped
                notifyListeners = changed
            }
        }
        if (notifyListeners && initialState != null) {
            dispatchToListeners(initialState)
        }
        return _pushActive
    }

    /**
     * Unregisters the broadcast receiver when no listeners remain (e.g. engine teardown).
     * Idempotent and safe against already-unregistered or mock contexts.
     */
    fun unregisterIfIdle() {
        synchronized(registerLock) {
            if (listeners.isNotEmpty()) return
            val rx = receiver ?: return
            val ctx = registeredContext
            receiver = null
            registeredContext = null
            _pushActive = false
            _seeded = false
            if (ctx != null) {
                runCatching { ctx.unregisterReceiver(rx) }
            }
        }
    }

    /** Publishes a new ringer state (from broadcast or host test) and notifies listeners on change. */
    internal fun publishRingerState(newState: AudioPolicy.RingerState) {
        val changed = synchronized(registerLock) {
            _seeded = true
            val diff = _ringerState != newState
            _ringerState = newState
            diff
        }
        if (changed) {
            dispatchToListeners(newState)
        }
    }

    internal fun setPushActiveForTest(active: Boolean) {
        synchronized(registerLock) {
            _pushActive = active
        }
    }

    internal fun resetForTest() {
        synchronized(registerLock) {
            receiver = null
            registeredContext = null
            listeners.clear()
            _ringerState = AudioPolicy.RingerState.NORMAL
            _pushActive = false
            _seeded = false
        }
    }

    private fun dispatchToListeners(state: AudioPolicy.RingerState) {
        for (listener in listeners) {
            runCatching { listener(state) }
        }
    }

    private fun readRingerModeFallback(context: Context): Int = runCatching {
        val am = context.getSystemService(AudioManager::class.java)
            ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
        am?.ringerMode ?: SoundEngineLogic.ANDROID_RINGER_MODE_NORMAL
    }.getOrDefault(SoundEngineLogic.ANDROID_RINGER_MODE_NORMAL)
}

/**
 * Pure, host-testable non-blocking system audio probe coordinator for [SoundEngine].
 *
 * Guarantees the calling (UI) thread NEVER executes synchronous `AudioManager` Binder IPCs:
 * reads `@Volatile` cached values in O(1) RAM and dispatches coalesced TTL-gated background
 * probes onto an off-UI executor (`AUDIO_SYNTH`).
 */
internal class AsyncAudioProbe {

    @Volatile
    var lastProbeMs: Long = Long.MIN_VALUE
        private set

    @Volatile
    var cachedRingerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL
        private set

    @Volatile
    var cachedSystemMusicActive: Boolean = false
        private set

    private val refreshInFlight = AtomicBoolean(false)
    private val probeEpoch = AtomicLong(0L)
    private val ringerLock = Any()
    private var ringerPushVersion: Long = 0L

    /** Marks the cached probe stale so the next refresh re-queries system audio state. */
    fun invalidate() {
        probeEpoch.incrementAndGet()
        lastProbeMs = Long.MIN_VALUE
    }

    /** Current monotonic push-ringer sequence; pass to [applyProbeResults] to prevent stale overwrites. */
    fun currentRingerPushVersion(): Long = synchronized(ringerLock) { ringerPushVersion }

    /** Applies an immediate push ringer update without touching [lastProbeMs] or Binder. */
    fun onRingerPushed(state: AudioPolicy.RingerState) {
        synchronized(ringerLock) {
            ringerPushVersion++
            cachedRingerState = state
        }
    }

    /**
     * Updates cached values from a background worker probe.
     *
     * [ringerState] is committed ONLY if no newer [onRingerPushed] event arrived since
     * [expectedRingerPushVersion] was captured, guaranteeing a slow worker probe can never
     * clobber a live push ringer transition.
     */
    fun applyProbeResults(
        ringerState: AudioPolicy.RingerState? = null,
        systemMusicActive: Boolean? = null,
        completedAtMs: Long,
        expectedEpoch: Long = probeEpoch.get(),
        expectedRingerPushVersion: Long = currentRingerPushVersion(),
    ) {
        if (ringerState != null) {
            synchronized(ringerLock) {
                if (ringerPushVersion == expectedRingerPushVersion) {
                    cachedRingerState = ringerState
                }
            }
        }
        if (systemMusicActive != null) {
            cachedSystemMusicActive = systemMusicActive
        }
        if (probeEpoch.get() == expectedEpoch) {
            lastProbeMs = completedAtMs
        }
    }

    /**
     * Triggers a coalesced background refresh via [dispatch] if TTL has expired and at least
     * one non-pushed probe is required. Never blocks or invokes [runProbe] on the caller thread.
     *
     * @return `true` if a background refresh task was scheduled, `false` if fresh, skipped, or in-flight.
     */
    fun maybeScheduleRefresh(
        nowMs: Long,
        queryRinger: Boolean,
        queryMusic: Boolean,
        ringerPushActive: Boolean,
        ttlMs: Long = SoundEngineLogic.SYSTEM_AUDIO_CACHE_TTL_MS,
        dispatch: (() -> Unit) -> Unit,
        clockMs: () -> Long = { nowMs },
        runProbe: (needRinger: Boolean, needMusic: Boolean, epoch: Long, ringerPushVersion: Long) -> Unit,
    ): Boolean {
        val needRinger = queryRinger && !ringerPushActive
        val needMusic = queryMusic
        if (!needRinger && !needMusic) return false
        if (!SoundEngineLogic.shouldRefreshSystemProbe(lastProbeMs, nowMs, ttlMs)) return false
        if (!refreshInFlight.compareAndSet(false, true)) return false

        val epoch = probeEpoch.get()
        val ringerVer = currentRingerPushVersion()
        val prevLastProbe = lastProbeMs
        var dispatched = false
        try {
            dispatch {
                try {
                    runProbe(needRinger, needMusic, epoch, ringerVer)
                    if (probeEpoch.get() == epoch && lastProbeMs == prevLastProbe) {
                        lastProbeMs = clockMs()
                    }
                } finally {
                    refreshInFlight.set(false)
                }
            }
            dispatched = true
        } finally {
            if (!dispatched) {
                refreshInFlight.set(false)
            }
        }
        return true
    }

    /** Current invalidation epoch for background tasks calling [applyProbeResults]. */
    fun currentEpoch(): Long = probeEpoch.get()

    /** True while an async probe refresh is currently queued or running. */
    fun isRefreshInFlight(): Boolean = refreshInFlight.get()
}
