package com.visorcraft.ghostgalleon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs

/**
 * Immutable ambient settings snapshot consumed by [AmbientEngine] and [AmbientEngineLogic].
 */
data class AmbientConfig(
    val enabled: Boolean = false,
    val soundPackId: SoundPackId = SoundPackId.AUTO,
    val themePackId: String = "ghost",
    val volumePercent: Int = 35,
    val respectMusic: Boolean = true,
    val respectRinger: Boolean = true,
    val soundEnabled: Boolean = true,
)

/**
 * Immutable foreground/lifecycle/etiquette snapshot for ambient eligibility gating.
 */
data class AmbientRuntimeState(
    val activeAppActivityCount: Int = 0,
    val hasKeepSession: Boolean = false,
    val screenOff: Boolean = false,
    val videoPreviewUnmuted: Boolean = false,
    val externalMusicActive: Boolean = false,
    val ringerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL,
)

/**
 * Pure, host-testable state transition planner for [AmbientEngine].
 *
 * Zero Android dependencies so every lifecycle, etiquette, focus, ducking, and pack-switch
 * transition can be verified directly under JVM unit tests.
 */
object AmbientEngineLogic {

    /** High-level engine transition computed from config + runtime + playback state. */
    enum class Action {
        /** Begin playing (or resume from pause) with a smooth 600 ms fade-in. */
        START,
        /** Continue playing the active pack with no gain/duck change. */
        KEEP_PLAYING,
        /** Smoothly fade out active pack, swap synthesized loop, and fade in the new pack. */
        SWITCH_PACK,
        /** Smoothly fade out (220 ms) before pausing `AudioTrack` and abandoning focus as needed. */
        FADE_OUT_PAUSE,
        /** Stay playing the current pack while smoothly updating volume gain and/or duck state. */
        UPDATE_GAIN,
    }

    /** Platform-independent abstraction over Android audio focus ownership states. */
    enum class FocusState {
        /** No audio focus currently held. */
        NONE,
        /** Full audio focus currently held. */
        GRANTED,
        /** Focus held, transiently ducked (`AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`). */
        DUCKED,
        /** Focus lost transiently (`AUDIOFOCUS_LOSS_TRANSIENT`); keep listener until regained. */
        LOST_TRANSIENT,
        /** Focus lost permanently (`AUDIOFOCUS_LOSS`); do not immediately steal focus back. */
        LOST_PERMANENT,
    }

    /** Current playback & focus snapshot inside the ambient controller. */
    data class PlaybackSnapshot(
        val isPlaying: Boolean = false,
        val activePack: SoundPackId? = null,
        val currentVolumeGain: Float = 0f,
        val focusState: FocusState = FocusState.NONE,
        val videoDucked: Boolean = false,
        val appliedDucked: Boolean = false,
    ) {
        val hasFocus: Boolean
            get() = focusState == FocusState.GRANTED || focusState == FocusState.DUCKED
    }

    /** Pure action + side-effect directives returned by [planTransition]. */
    data class TransitionPlan(
        val action: Action,
        val resolvedPack: SoundPackId,
        val targetVolumeGain: Float,
        val shouldBeDucked: Boolean,
        val shouldRequestFocus: Boolean,
        val shouldAbandonFocus: Boolean,
    )

    /** Fallback ringer polling interval (ms) used ONLY when broadcast push registration fails. */
    const val FALLBACK_RINGER_POLL_INTERVAL_MS: Long = 2_000L

    /** Android [AudioManager] ringer mode constants mirrored cleanly for pure JVM tests. */
    const val ANDROID_RINGER_MODE_SILENT: Int = 0
    const val ANDROID_RINGER_MODE_VIBRATE: Int = 1
    const val ANDROID_RINGER_MODE_NORMAL: Int = 2

    /** Maps Android `AudioManager.ringerMode` to platform-independent [AudioPolicy.RingerState]. */
    fun mapRingerMode(androidRingerMode: Int): AudioPolicy.RingerState = when (androidRingerMode) {
        ANDROID_RINGER_MODE_SILENT -> AudioPolicy.RingerState.SILENT
        ANDROID_RINGER_MODE_VIBRATE -> AudioPolicy.RingerState.VIBRATE
        else -> AudioPolicy.RingerState.NORMAL
    }

    /**
     * Returns `true` when the 43 Hz streaming loop can execute its zero-IPC, zero-allocation
     * steady-state chunk write directly without re-acquiring locks, probing `AudioManager`,
     * or allocating `LoopInputs`/`TransitionPlan` objects.
     *
     * Any state mutation (`sync`, focus loss/gain, video duck, screen off, background,
     * push ringer change, or `release`) advances [currentStateVersion], immediately breaking
     * out of the fast path on the very next chunk.
     */
    fun canUseSteadyStateFastPath(
        isPlaying: Boolean,
        hasExplicitConfig: Boolean,
        released: Boolean,
        currentStateVersion: Long,
        lastEvaluatedVersion: Long,
        ringerPushActive: Boolean,
        nowMs: Long = 0L,
        lastFallbackRingerProbeMs: Long = 0L,
    ): Boolean {
        if (!isPlaying || !hasExplicitConfig || released) return false
        if (currentStateVersion != lastEvaluatedVersion) return false
        if (ringerPushActive) return true
        val elapsed = nowMs - lastFallbackRingerProbeMs
        return elapsed in 0L until FALLBACK_RINGER_POLL_INTERVAL_MS
    }

    /**
     * Resolves the effective [AudioPolicy.RingerState] for ambient policy gating without
     * issuing polling Binder IPCs whenever push broadcasts are active.
     */
    fun resolveEffectiveRingerState(
        explicitStateRinger: AudioPolicy.RingerState,
        pushedRinger: AudioPolicy.RingerState,
        respectRinger: Boolean,
        ringerPushActive: Boolean,
        fallbackProbe: () -> AudioPolicy.RingerState,
    ): AudioPolicy.RingerState {
        if (explicitStateRinger != AudioPolicy.RingerState.NORMAL) return explicitStateRinger
        if (!respectRinger) return AudioPolicy.RingerState.NORMAL
        if (ringerPushActive) return pushedRinger
        return fallbackProbe()
    }

    /** Resolves [AmbientConfig.soundPackId] against [AmbientConfig.themePackId]. */
    fun resolveEffectivePack(config: AmbientConfig): SoundPackId =
        config.soundPackId.resolve(config.themePackId)

    /**
     * Plans the exact next [Action] and focus/duck/gain directives for [AmbientEngine].
     *
     * Etiquette invariants:
     * - When [AudioPolicy.shouldPlayAmbient] disallows playback (external music active while
     *   `respectMusic = true`, KEEP session active, screen off, backgrounded, silent ringer,
     *   unmuted video preview, or pack `OFF`), [TransitionPlan.shouldRequestFocus] is ALWAYS
     *   `false` so starting the launcher never steals focus from external music or podcasts.
     * - On `LOST_TRANSIENT`, ambient fades out & pauses WITHOUT abandoning the focus listener
     *   so Android can automatically deliver `AUDIOFOCUS_GAIN` when the transient interruption ends.
     * - On `LOST_PERMANENT`, ambient fades out, abandons focus, and does NOT immediately re-steal
     *   focus until focus is re-acquired or the launcher cycles foreground/enable state.
     */
    fun planTransition(
        config: AmbientConfig,
        state: AmbientRuntimeState,
        current: PlaybackSnapshot,
    ): TransitionPlan {
        val resolvedPack = resolveEffectivePack(config)
        val targetVolumeGain = AudioPolicy.volumePercentToGain(config.volumePercent)
        val shouldBeDucked = current.focusState == FocusState.DUCKED || current.videoDucked

        val policyEligibleWithoutFocus = AudioPolicy.shouldPlayAmbient(
            soundEnabled = config.soundEnabled,
            ambientEnabled = config.enabled,
            pack = resolvedPack,
            ambientVolumePercent = config.volumePercent,
            activeAppActivityCount = state.activeAppActivityCount,
            hasKeepSession = state.hasKeepSession,
            screenOff = state.screenOff,
            respectRinger = config.respectRinger,
            ringerState = state.ringerState,
            respectMusic = config.respectMusic,
            externalMusicActive = state.externalMusicActive,
            videoPreviewUnmuted = state.videoPreviewUnmuted,
            audioFocusGranted = true,
        )

        if (!policyEligibleWithoutFocus) {
            // Policy itself blocks playback: fade out if active and abandon any focus registration.
            return TransitionPlan(
                action = Action.FADE_OUT_PAUSE,
                resolvedPack = resolvedPack,
                targetVolumeGain = 0f,
                shouldBeDucked = false,
                shouldRequestFocus = false,
                shouldAbandonFocus = current.focusState != FocusState.NONE,
            )
        }

        // Policy allows playback; evaluate focus state cleanly.
        if (current.focusState == FocusState.LOST_TRANSIENT) {
            // Hold focus registration without playing until AUDIOFOCUS_GAIN arrives.
            return TransitionPlan(
                action = Action.FADE_OUT_PAUSE,
                resolvedPack = resolvedPack,
                targetVolumeGain = 0f,
                shouldBeDucked = false,
                shouldRequestFocus = false,
                shouldAbandonFocus = false,
            )
        }
        if (current.focusState == FocusState.LOST_PERMANENT) {
            return TransitionPlan(
                action = Action.FADE_OUT_PAUSE,
                resolvedPack = resolvedPack,
                targetVolumeGain = 0f,
                shouldBeDucked = false,
                shouldRequestFocus = false,
                shouldAbandonFocus = true,
            )
        }

        if (!current.isPlaying) {
            return TransitionPlan(
                action = Action.START,
                resolvedPack = resolvedPack,
                targetVolumeGain = targetVolumeGain,
                shouldBeDucked = shouldBeDucked,
                shouldRequestFocus = !current.hasFocus,
                shouldAbandonFocus = false,
            )
        }

        // Currently playing: confirm focus is still held via AudioPolicy.
        val stillEligibleWithFocus = AudioPolicy.shouldPlayAmbient(
            soundEnabled = config.soundEnabled,
            ambientEnabled = config.enabled,
            pack = resolvedPack,
            ambientVolumePercent = config.volumePercent,
            activeAppActivityCount = state.activeAppActivityCount,
            hasKeepSession = state.hasKeepSession,
            screenOff = state.screenOff,
            respectRinger = config.respectRinger,
            ringerState = state.ringerState,
            respectMusic = config.respectMusic,
            externalMusicActive = state.externalMusicActive,
            videoPreviewUnmuted = state.videoPreviewUnmuted,
            audioFocusGranted = current.hasFocus,
        )
        if (!stillEligibleWithFocus) {
            return TransitionPlan(
                action = Action.FADE_OUT_PAUSE,
                resolvedPack = resolvedPack,
                targetVolumeGain = 0f,
                shouldBeDucked = false,
                shouldRequestFocus = false,
                shouldAbandonFocus = true,
            )
        }

        if (current.activePack != resolvedPack) {
            return TransitionPlan(
                action = Action.SWITCH_PACK,
                resolvedPack = resolvedPack,
                targetVolumeGain = targetVolumeGain,
                shouldBeDucked = shouldBeDucked,
                shouldRequestFocus = false,
                shouldAbandonFocus = false,
            )
        }

        val gainChanged = abs(current.currentVolumeGain - targetVolumeGain) > 1e-4f
        val duckChanged = current.appliedDucked != shouldBeDucked
        return TransitionPlan(
            action = if (gainChanged || duckChanged) Action.UPDATE_GAIN else Action.KEEP_PLAYING,
            resolvedPack = resolvedPack,
            targetVolumeGain = targetVolumeGain,
            shouldBeDucked = shouldBeDucked,
            shouldRequestFocus = false,
            shouldAbandonFocus = false,
        )
    }
}

/**
 * App-scoped streaming ambient soundscape engine backed by Android API 26+ [AudioTrack]
 * (`MODE_STREAM`, `USAGE_GAME` / `CONTENT_TYPE_MUSIC`) and polite [AudioFocusRequest].
 *
 * Threading & etiquette guarantees:
 * - All loop synthesis, [AudioTrack] lifecycle, and blocking `write()` calls execute on a
 *   single lazy daemon thread (`gg-ambient-stream`), NEVER on the UI thread.
 * - Starts with a smooth `600 ms` fade-in, ducks smoothly over `180 ms`, and ALWAYS runs
 *   a `220 ms` fade-out before pausing/stopping [AudioTrack] (zero hard-cut clicks).
 * - Checks [AudioManager.isMusicActive] before requesting audio focus when `respectMusic`
 *   is enabled so launching Ghost Galleon never pauses external music or podcasts.
 */
class AmbientEngine(
    context: Context,
    private val configProvider: () -> AmbientConfig = { AmbientConfig() },
) {
    private val appContext: Context = context.applicationContext
    private val cacheDir: File = File(appContext.cacheDir, SoundEngineLogic.CACHE_DIR_NAME)
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val ambientCacheCleaned = AtomicBoolean(false)

    private val lock = ReentrantLock()
    private val wakeCondition = lock.newCondition()

    private var stateVersion: Long = 0L
    @Volatile
    private var volatileStateVersion: Long = 0L
    private var desiredConfig: AmbientConfig = AmbientConfig()
    private var desiredState: AmbientRuntimeState = AmbientRuntimeState()
    private var hasExplicitConfig: Boolean = false
    @Volatile
    private var volatileHasExplicitConfig: Boolean = false
    private var videoPreviewDucked: Boolean = false
    private var focusState: AmbientEngineLogic.FocusState = AmbientEngineLogic.FocusState.NONE
    private var pushedRingerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL
    private var hasReceivedRingerPush: Boolean = false
    private var released: Boolean = false
    @Volatile
    private var volatileReleased: Boolean = false

    @Volatile
    private var _isPlaying: Boolean = false

    @Volatile
    private var _activePack: SoundPackId? = null

    private var workerThread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

    private val ringerListener: (AudioPolicy.RingerState) -> Unit = { newState ->
        onRingerStatePushed(newState)
    }

    private fun bumpStateVersionLocked() {
        stateVersion++
        volatileStateVersion = stateVersion
    }

    /** Handles push ringer-mode transitions from [SystemAudioMonitor] with 0 polling IPCs. */
    internal fun onRingerStatePushed(newState: AudioPolicy.RingerState) {
        lock.withLock {
            hasReceivedRingerPush = true
            if (released || pushedRingerState == newState) return
            pushedRingerState = newState
            bumpStateVersionLocked()
            if (workerThread != null) {
                wakeCondition.signalAll()
            }
        }
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        lock.withLock {
            if (released) return@withLock
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    focusState = AmbientEngineLogic.FocusState.LOST_PERMANENT
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    focusState = AmbientEngineLogic.FocusState.LOST_TRANSIENT
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    focusState = AmbientEngineLogic.FocusState.DUCKED
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focusState = AmbientEngineLogic.FocusState.GRANTED
                    if (!hasExplicitConfig) {
                        desiredConfig = runCatching { configProvider() }.getOrDefault(desiredConfig)
                    }
                }
                else -> return@withLock
            }
            bumpStateVersionLocked()
            wakeCondition.signalAll()
        }
    }

    /** True while the ambient stream is actively writing audio. */
    val isPlaying: Boolean get() = _isPlaying

    /** Currently playing concrete [SoundPackId], or null when stopped/paused. */
    val activePack: SoundPackId? get() = _activePack

    /**
     * Synchronizes ambient playback with [config] and [state].
     * Idempotent and safe to call from the UI thread (signals the daemon controller without blocking).
     */
    fun sync(config: AmbientConfig, state: AmbientRuntimeState) {
        lock.withLock {
            if (released) return
            val foregroundReturned = desiredState.activeAppActivityCount <= 0 && state.activeAppActivityCount > 0
            val reEnabled = !desiredConfig.enabled && config.enabled
            if ((foregroundReturned || reEnabled) && focusState == AmbientEngineLogic.FocusState.LOST_PERMANENT) {
                focusState = AmbientEngineLogic.FocusState.NONE
            }
            desiredConfig = config
            desiredState = state
            hasExplicitConfig = true
            volatileHasExplicitConfig = true
            bumpStateVersionLocked()
            val shouldEnsureWorker = workerThread != null || (config.enabled && config.soundEnabled)
            if (shouldEnsureWorker) {
                ensureWorkerLocked()
                wakeCondition.signalAll()
            }
        }
    }

    /**
     * Smoothly ducks or unducks active ambient playback (`180 ms` ramp) for hero video
     * previews without stopping the stream.
     */
    fun setVideoPreviewDuck(ducked: Boolean) {
        lock.withLock {
            if (released || videoPreviewDucked == ducked) return
            videoPreviewDucked = ducked
            bumpStateVersionLocked()
            if (workerThread != null) {
                wakeCondition.signalAll()
            }
        }
    }

    /** Immediately schedules a smooth fade-out and pause when the device screen turns off. */
    fun onScreenOff() {
        lock.withLock {
            if (released) return
            desiredState = desiredState.copy(screenOff = true)
            bumpStateVersionLocked()
            if (workerThread != null) {
                wakeCondition.signalAll()
            }
        }
    }

    /** Immediately schedules a smooth fade-out and pause when all app activities leave foreground. */
    fun onAppBackgrounded() {
        lock.withLock {
            if (released) return
            desiredState = desiredState.copy(activeAppActivityCount = 0)
            bumpStateVersionLocked()
            if (workerThread != null) {
                wakeCondition.signalAll()
            }
        }
    }

    /**
     * Permanently shuts down the ambient engine, fading out cleanly if active, releasing
     * [AudioTrack], and abandoning audio focus on the daemon thread.
     */
    fun release() {
        lock.withLock {
            if (released) return
            released = true
            volatileReleased = true
            desiredState = desiredState.copy(activeAppActivityCount = 0, screenOff = true)
            bumpStateVersionLocked()
            wakeCondition.signalAll()
        }
    }

    private fun ensureWorkerLocked() {
        if (workerThread != null || released) return
        val thread = Thread(::runStreamLoop, THREAD_NAME).apply {
            isDaemon = true
        }
        workerThread = thread
        thread.start()
    }

    private fun runStreamLoop() {
        val envelope = AmbientFadeEnvelope(sampleRate = AmbientSynthesizer.SAMPLE_RATE, initialVolumeGain = 0f)
        val chunkBuffer = ShortArray(CHUNK_FRAMES * AmbientSynthesizer.CHANNELS)

        var track: AudioTrack? = null
        var cachedPack: SoundPackId? = null
        var cachedLoop: ShortArray? = null
        var loopCursor = 0

        var playing = false
        var activePackLocal: SoundPackId? = null
        var appliedGain = 0f
        var appliedDucked = false
        var lastEvaluatedVersion = -1L
        var lastFallbackRingerProbeMs = 0L

        // Subscribe to push RINGER_MODE_CHANGED_ACTION off-UI once on worker startup.
        SystemAudioMonitor.addListener(ringerListener)
        val ringerPushActive = SystemAudioMonitor.ensureRegistered(appContext)
        val seededRinger = if (SystemAudioMonitor.isSeeded) {
            SystemAudioMonitor.ringerState
        } else {
            lastFallbackRingerProbeMs = System.nanoTime() / 1_000_000L
            safeRingerState()
        }
        lock.withLock {
            if (!hasReceivedRingerPush) {
                pushedRingerState = if (SystemAudioMonitor.isSeeded) SystemAudioMonitor.ringerState else seededRinger
            }
        }

        fun recoverFromWriteFailure(failedTrack: AudioTrack, observedVersion: Long) {
            stopAndReleaseTrack(failedTrack)
            track = null
            abandonFocusIfHeld()
            playing = false
            activePackLocal = null
            appliedGain = 0f
            appliedDucked = false
            lastEvaluatedVersion = -1L
            _isPlaying = false
            _activePack = null
            awaitNextStateVersion(observedVersion)
        }

        try {
            while (true) {
                // Zero-IPC, zero-allocation steady-state 43 Hz streaming fast path:
                // while playing with unchanged stateVersion and active push ringer monitor,
                // skip lock acquisition, AudioManager probes, and LoopInputs/TransitionPlan allocations.
                val activeTrackFast = track
                val activeLoopFast = cachedLoop
                if (activeTrackFast != null &&
                    activeLoopFast != null &&
                    AmbientEngineLogic.canUseSteadyStateFastPath(
                        isPlaying = playing,
                        hasExplicitConfig = volatileHasExplicitConfig,
                        released = volatileReleased,
                        currentStateVersion = volatileStateVersion,
                        lastEvaluatedVersion = lastEvaluatedVersion,
                        ringerPushActive = ringerPushActive,
                        nowMs = if (ringerPushActive) 0L else System.nanoTime() / 1_000_000L,
                        lastFallbackRingerProbeMs = lastFallbackRingerProbeMs,
                    )
                ) {
                    val writeRes = writeOneChunk(activeTrackFast, envelope, activeLoopFast, loopCursor, chunkBuffer)
                    if (writeRes < 0) {
                        recoverFromWriteFailure(activeTrackFast, lastEvaluatedVersion)
                    } else {
                        loopCursor = writeRes
                    }
                    continue
                }

                val inputs = lock.withLock {
                    val effectiveCfg = if (hasExplicitConfig) {
                        desiredConfig
                    } else {
                        runCatching { configProvider() }.getOrDefault(desiredConfig)
                    }
                    LoopInputs(
                        version = stateVersion,
                        config = effectiveCfg,
                        state = desiredState,
                        pushedRingerState = pushedRingerState,
                        released = released,
                        snapshot = AmbientEngineLogic.PlaybackSnapshot(
                            isPlaying = playing,
                            activePack = activePackLocal,
                            currentVolumeGain = appliedGain,
                            focusState = focusState,
                            videoDucked = videoPreviewDucked,
                            appliedDucked = appliedDucked,
                        ),
                    )
                }

                if (inputs.released) {
                    val activeTrack = track
                    if (playing && activeTrack != null && cachedLoop != null) {
                        drainFadeOut(activeTrack, envelope, cachedLoop, loopCursor, chunkBuffer)
                    }
                    stopAndReleaseTrack(activeTrack)
                    abandonFocusIfHeld()
                    _isPlaying = false
                    _activePack = null
                    return
                }

                val cfg = inputs.config
                val rawState = inputs.state

                // When not currently playing, probe AudioManager.isMusicActive() BEFORE requesting
                // audio focus so starting the launcher never interrupts user Spotify/podcasts.
                val probedExternalMusic = rawState.externalMusicActive ||
                    (!playing && cfg.respectMusic && safeIsMusicActive())
                val probedRinger = AmbientEngineLogic.resolveEffectiveRingerState(
                    explicitStateRinger = rawState.ringerState,
                    pushedRinger = inputs.pushedRingerState,
                    respectRinger = cfg.respectRinger,
                    ringerPushActive = ringerPushActive,
                ) {
                    safeRingerState()
                }
                if (!ringerPushActive) {
                    lastFallbackRingerProbeMs = System.nanoTime() / 1_000_000L
                }
                val effectiveState = if (probedExternalMusic == rawState.externalMusicActive &&
                    probedRinger == rawState.ringerState
                ) {
                    rawState
                } else {
                    rawState.copy(
                        externalMusicActive = probedExternalMusic,
                        ringerState = probedRinger,
                    )
                }

                val plan = AmbientEngineLogic.planTransition(cfg, effectiveState, inputs.snapshot)
                lastEvaluatedVersion = inputs.version

                when (plan.action) {
                    AmbientEngineLogic.Action.FADE_OUT_PAUSE -> {
                        val activeTrack = track
                        if (playing && activeTrack != null && cachedLoop != null) {
                            loopCursor = drainFadeOut(activeTrack, envelope, cachedLoop, loopCursor, chunkBuffer)
                        }
                        // Release native AudioTrack when pausing so no AudioFlinger mixer slot
                        // remains allocated during KEEP sessions, sleep, background, or Ambient OFF.
                        stopAndReleaseTrack(activeTrack)
                        track = null
                        if (plan.shouldAbandonFocus) {
                            abandonFocusIfHeld(
                                preservePermanentLoss = inputs.snapshot.focusState == AmbientEngineLogic.FocusState.LOST_PERMANENT,
                            )
                        }
                        playing = false
                        activePackLocal = null
                        appliedGain = 0f
                        appliedDucked = false
                        _isPlaying = false
                        _activePack = null

                        // Park at 0% CPU only if no newer stateVersion arrived during fade-out
                        awaitNextStateVersion(inputs.version)
                    }

                    AmbientEngineLogic.Action.START -> {
                        if (plan.shouldRequestFocus && !requestAmbientFocus()) {
                            awaitNextStateVersion(inputs.version)
                            continue
                        }
                        if (lock.withLock { released || stateVersion != inputs.version }) continue

                        val loop = loadOrSynthesizeLoop(plan.resolvedPack, cachedPack, cachedLoop)
                        cachedPack = plan.resolvedPack
                        cachedLoop = loop
                        if (lock.withLock { released || stateVersion != inputs.version }) continue
                        if (loop.isEmpty()) {
                            stopAndReleaseTrack(track)
                            track = null
                            abandonFocusIfHeld()
                            awaitNextStateVersion(inputs.version)
                            continue
                        }

                        if (track == null) {
                            track = createAudioTrack()
                        }
                        val activeTrack = track
                        if (activeTrack == null) {
                            abandonFocusIfHeld()
                            awaitNextStateVersion(inputs.version)
                            continue
                        }

                        loopCursor = 0
                        envelope.resetSilent()
                        envelope.setVolumeGain(plan.targetVolumeGain, durationMs = 0)
                        envelope.setDucked(plan.shouldBeDucked, durationMs = 0)
                        envelope.fadeIn(AmbientSynthesizer.FADE_IN_MS)

                        val started = runCatching {
                            activeTrack.play()
                            true
                        }.getOrDefault(false)

                        if (!started) {
                            recoverFromWriteFailure(activeTrack, inputs.version)
                            continue
                        }

                        playing = true
                        activePackLocal = plan.resolvedPack
                        appliedGain = plan.targetVolumeGain
                        appliedDucked = plan.shouldBeDucked
                        _isPlaying = true
                        _activePack = plan.resolvedPack

                        val writeRes = writeOneChunk(activeTrack, envelope, loop, loopCursor, chunkBuffer)
                        if (writeRes < 0) {
                            recoverFromWriteFailure(activeTrack, inputs.version)
                        } else {
                            loopCursor = writeRes
                        }
                    }

                    AmbientEngineLogic.Action.SWITCH_PACK -> {
                        val activeTrack = track
                        val oldLoop = cachedLoop
                        if (playing && activeTrack != null && oldLoop != null) {
                            drainFadeOut(activeTrack, envelope, oldLoop, loopCursor, chunkBuffer)
                            activePackLocal = null
                            _activePack = null
                        }
                        if (lock.withLock { released || stateVersion != inputs.version }) continue

                        val newLoop = loadOrSynthesizeLoop(plan.resolvedPack, cachedPack, cachedLoop)
                        cachedPack = plan.resolvedPack
                        cachedLoop = newLoop
                        if (lock.withLock { released || stateVersion != inputs.version }) continue
                        if (newLoop.isEmpty() || activeTrack == null) {
                            stopAndReleaseTrack(activeTrack)
                            track = null
                            abandonFocusIfHeld()
                            playing = false
                            activePackLocal = null
                            appliedGain = 0f
                            appliedDucked = false
                            _isPlaying = false
                            _activePack = null
                            continue
                        }

                        loopCursor = 0
                        envelope.resetSilent()
                        envelope.setVolumeGain(plan.targetVolumeGain, durationMs = 0)
                        envelope.setDucked(plan.shouldBeDucked, durationMs = 0)
                        envelope.fadeIn(AmbientSynthesizer.FADE_IN_MS)

                        activePackLocal = plan.resolvedPack
                        appliedGain = plan.targetVolumeGain
                        appliedDucked = plan.shouldBeDucked
                        _activePack = plan.resolvedPack

                        val writeRes = writeOneChunk(activeTrack, envelope, newLoop, loopCursor, chunkBuffer)
                        if (writeRes < 0) {
                            recoverFromWriteFailure(activeTrack, inputs.version)
                        } else {
                            loopCursor = writeRes
                        }
                    }

                    AmbientEngineLogic.Action.UPDATE_GAIN -> {
                        envelope.setVolumeGain(plan.targetVolumeGain, durationMs = AmbientSynthesizer.VOLUME_RAMP_MS)
                        envelope.setDucked(plan.shouldBeDucked, durationMs = AmbientSynthesizer.DUCK_RAMP_MS)
                        appliedGain = plan.targetVolumeGain
                        appliedDucked = plan.shouldBeDucked

                        val activeTrack = track
                        val loop = cachedLoop
                        if (activeTrack != null && loop != null && loop.isNotEmpty()) {
                            val writeRes = writeOneChunk(activeTrack, envelope, loop, loopCursor, chunkBuffer)
                            if (writeRes < 0) {
                                recoverFromWriteFailure(activeTrack, inputs.version)
                            } else {
                                loopCursor = writeRes
                            }
                        }
                    }

                    AmbientEngineLogic.Action.KEEP_PLAYING -> {
                        val activeTrack = track
                        val loop = cachedLoop
                        if (activeTrack != null && loop != null && loop.isNotEmpty()) {
                            val writeRes = writeOneChunk(activeTrack, envelope, loop, loopCursor, chunkBuffer)
                            if (writeRes < 0) {
                                recoverFromWriteFailure(activeTrack, inputs.version)
                            } else {
                                loopCursor = writeRes
                            }
                        }
                    }
                }
            }
        } catch (_: InterruptedException) {
            stopAndReleaseTrack(track)
            abandonFocusIfHeld()
            _isPlaying = false
            _activePack = null
        } catch (_: Throwable) {
            stopAndReleaseTrack(track)
            abandonFocusIfHeld()
            _isPlaying = false
            _activePack = null
        } finally {
            SystemAudioMonitor.removeListener(ringerListener)
            SystemAudioMonitor.unregisterIfIdle()
        }
    }

    private data class LoopInputs(
        val version: Long,
        val config: AmbientConfig,
        val state: AmbientRuntimeState,
        val pushedRingerState: AudioPolicy.RingerState,
        val released: Boolean,
        val snapshot: AmbientEngineLogic.PlaybackSnapshot,
    )

    private fun awaitNextStateVersion(observedVersion: Long) {
        lock.withLock {
            while (!released && stateVersion == observedVersion) {
                wakeCondition.await()
            }
        }
    }

    private fun loadOrSynthesizeLoop(
        pack: SoundPackId,
        cachedPack: SoundPackId?,
        cachedLoop: ShortArray?,
    ): ShortArray {
        if (cachedPack == pack && cachedLoop != null) return cachedLoop
        if (ambientCacheCleaned.compareAndSet(false, true)) {
            AmbientLoopCache.pruneStaleFiles(cacheDir)
        }
        return AmbientLoopCache.loadOrSynthesize(
            cacheDir = cacheDir,
            pack = pack,
            durationSec = AmbientSynthesizer.DEFAULT_DURATION_SEC,
            sampleRate = AmbientSynthesizer.SAMPLE_RATE,
        )
    }

    private fun drainFadeOut(
        track: AudioTrack,
        envelope: AmbientFadeEnvelope,
        loop: ShortArray,
        startCursor: Int,
        chunkBuffer: ShortArray,
    ): Int {
        envelope.fadeOut(AmbientSynthesizer.FADE_OUT_MS)
        var cursor = startCursor
        val maxFadeChunks = ((AmbientSynthesizer.FADE_OUT_MS / 1000.0) * AmbientSynthesizer.SAMPLE_RATE / CHUNK_FRAMES)
            .toInt() + 2
        var writtenChunks = 0
        while (!envelope.isSilent && writtenChunks < maxFadeChunks) {
            val next = writeOneChunk(track, envelope, loop, cursor, chunkBuffer)
            if (next < 0) break
            cursor = next
            writtenChunks++
        }
        return cursor
    }

    /** Writes one stereo chunk, returning the next loop frame cursor or `-1` on HAL write failure. */
    private fun writeOneChunk(
        track: AudioTrack,
        envelope: AmbientFadeEnvelope,
        loop: ShortArray,
        cursor: Int,
        chunkBuffer: ShortArray,
    ): Int {
        val nextCursor = envelope.renderLoopChunk(loop, cursor, chunkBuffer, CHUNK_FRAMES)
        val written = runCatching {
            track.write(chunkBuffer, 0, chunkBuffer.size, AudioTrack.WRITE_BLOCKING)
        }.getOrDefault(-1)
        return if (written < 0) -1 else nextCursor
    }

    private fun createAudioTrack(): AudioTrack? = runCatching {
        val sampleRate = AmbientSynthesizer.SAMPLE_RATE
        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val chunkBytes = CHUNK_FRAMES * AmbientSynthesizer.CHANNELS * 2
        val bufferBytes = maxOf(minBufBytes.coerceAtLeast(chunkBytes) * 2, chunkBytes * 3)

        val attributes = buildAudioAttributes()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            null
        } else {
            track
        }
    }.getOrNull()

    private fun buildAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private fun stopAndReleaseTrack(track: AudioTrack?) {
        if (track == null) return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
            }
            track.flush()
            track.release()
        }
    }

    private fun requestAmbientFocus(): Boolean {
        val am = audioManager ?: return false
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(buildAudioAttributes())
            .setWillPauseWhenDucked(false)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
            .also { focusRequest = it }

        val granted = runCatching {
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)

        lock.withLock {
            if (granted) {
                focusState = AmbientEngineLogic.FocusState.GRANTED
            }
        }
        return granted
    }

    private fun abandonFocusIfHeld(preservePermanentLoss: Boolean = false) {
        val am = audioManager
        val req = focusRequest
        val shouldAbandon = lock.withLock {
            val hadRegistration = focusState != AmbientEngineLogic.FocusState.NONE
            focusState = if (preservePermanentLoss) {
                AmbientEngineLogic.FocusState.LOST_PERMANENT
            } else {
                AmbientEngineLogic.FocusState.NONE
            }
            hadRegistration
        }
        if (shouldAbandon && am != null && req != null) {
            runCatching { am.abandonAudioFocusRequest(req) }
        }
    }

    private fun safeIsMusicActive(): Boolean =
        runCatching { audioManager?.isMusicActive == true }.getOrDefault(false)

    private fun safeRingerState(): AudioPolicy.RingerState {
        val mode = runCatching { audioManager?.ringerMode }
            .getOrNull() ?: AmbientEngineLogic.ANDROID_RINGER_MODE_NORMAL
        return AmbientEngineLogic.mapRingerMode(mode)
    }

    companion object {
        private const val THREAD_NAME = "gg-ambient-stream"
        /** Stereo frames per streaming write (~23.2 ms at 44.1 kHz). */
        const val CHUNK_FRAMES: Int = 1024
    }
}
