package com.visorcraft.ghostgalleon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.visorcraft.ghostgalleon.settings.ThemePack
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Snapshot of audio settings consumed by [SoundEngine].
 *
 * Defaults match Ghost Galleon's baseline handheld tuning so [SoundEngine] can run
 * independently before Settings UI fields are wired in Iteration 3.
 */
data class SoundEngineConfig(
    val soundEnabled: Boolean = true,
    val sfxVolumePercent: Int = SoundEngineLogic.DEFAULT_SFX_VOLUME_PERCENT,
    val soundPackId: SoundPackId = SoundPackId.AUTO,
    val themePackId: String = ThemePack.GHOST.id,
    val respectRinger: Boolean = true,
    val respectMusic: Boolean = true,
    val bootChimeEnabled: Boolean = true,
    val achievementChimeEnabled: Boolean = true,
    val playHudQuiet: Boolean = true,
    val videoPreviewUnmuted: Boolean = false,
    val customSoundPackUri: String = "",
) {
    /** Resolves the effective concrete pack, or [SoundPackId.OFF] when disabled. */
    fun resolvedPack(): SoundPackId = SoundEngineLogic.resolveEffectivePack(
        soundEnabled = soundEnabled,
        configuredPack = soundPackId,
        themePackId = themePackId,
    )
}

/**
 * Live application/device lifecycle snapshot queried by [SoundEngine].
 */
data class SoundRuntimeState(
    val activeAppActivityCount: Int = 1,
    val hasKeepSession: Boolean = false,
    val screenOff: Boolean = false,
)

/**
 * Pure, host-testable state, caching, generation, preview-sequence, and playback-parameter
 * helpers for [SoundEngine]. Zero Android runtime dependencies.
 */
object SoundEngineLogic {

    const val CACHE_DIR_NAME: String = "gg_sfx"
    const val SYNTH_CACHE_VERSION: Int = 1
    const val MAX_STREAMS: Int = 8
    const val AUDITION_MAX_STREAMS: Int = 4
    const val DEFAULT_SFX_VOLUME_PERCENT: Int = 75
    const val MIN_PLAYBACK_RATE: Float = 0.5f
    const val MAX_PLAYBACK_RATE: Float = 2.0f
    const val MIN_AUDIBLE_GAIN: Float = 1e-4f
    const val PREVIEW_SECOND_NOTE_DELAY_MS: Long = 110L
    const val SYSTEM_AUDIO_CACHE_TTL_MS: Long = 250L
    const val BOOT_CHIME_PENDING_TTL_MS: Long = 2_500L

    // Matches android.media.AudioManager.RINGER_MODE_* constants without importing android.*
    const val ANDROID_RINGER_MODE_SILENT: Int = 0
    const val ANDROID_RINGER_MODE_VIBRATE: Int = 1
    const val ANDROID_RINGER_MODE_NORMAL: Int = 2

    /** Pack synchronization plan computed on settings/theme changes. */
    enum class SyncAction {
        /** Already loaded or loading the target pack (or already idle for OFF). */
        NO_OP,
        /** Tear down active pool and remain idle because audio is disabled or OFF. */
        RELEASE_TO_IDLE,
        /** Retire any prior pool and asynchronously synthesize/load the new concrete pack. */
        SWITCH_PACK,
    }

    /** Single timed cue in a Settings pack-preview motif. */
    data class PreviewStep(
        val cue: SoundCue,
        val delayMs: Long,
        val pan: Float = 0f,
    )

    /** Computed stream playback parameters ready for `SoundPool.play`. */
    data class PlayParams(
        val leftGain: Float,
        val rightGain: Float,
        val rate: Float,
        val priority: Int,
    )

    /** Thread-safe monotonic generation counter for discarding stale async loads. */
    class GenerationGate {
        private val counter = AtomicLong(0L)

        fun current(): Long = counter.get()

        fun advance(): Long = counter.incrementAndGet()

        fun isCurrent(generation: Long): Boolean = counter.get() == generation
    }

    /**
     * Signature 2-note pack preview motif: gentle left-of-center tick followed 110 ms later
     * by a right-of-center confirm chime.
     */
    val PREVIEW_MOTIF: List<PreviewStep> = listOf(
        PreviewStep(cue = SoundCue.NAV_TICK, delayMs = 0L, pan = -0.10f),
        PreviewStep(cue = SoundCue.CONFIRM, delayMs = PREVIEW_SECOND_NOTE_DELAY_MS, pan = 0.10f),
    )

    private val PRIORITY_LOAD_CUES: List<SoundCue> = listOf(
        SoundCue.BOOT_CHIME,
        SoundCue.NAV_TICK,
        SoundCue.CONFIRM,
        SoundCue.BACK,
        SoundCue.NAV_EDGE_BUMP,
        SoundCue.NAV_PAGE_TURN,
        SoundCue.DOCK_ENTER,
        SoundCue.DOCK_EXIT,
        SoundCue.MODAL_OPEN,
        SoundCue.MODAL_CLOSE,
        SoundCue.LAUNCH_GAME,
        SoundCue.LAUNCH_APP,
        SoundCue.SWAP_DISPLAY,
        SoundCue.MODE_TOGGLE,
        SoundCue.FILTER_SELECT,
    )

    /**
     * Cold-start synthesis/load order: interactive & boot cues first so immediate UI inputs
     * become audible within milliseconds while rare celebration cues finish behind them.
     */
    val LOAD_ORDER: List<SoundCue> = (PRIORITY_LOAD_CUES + SoundCue.entries).distinct()

    private val CONCRETE_PACK_IDS: Set<String> = SoundPackId.CONCRETE_PACKS.map { it.id }.toSet()
    private val VALID_CUE_NAMES: Set<String> = SoundCue.entries.map { it.name.lowercase() }.toSet()

    /**
     * Resolves the effective concrete [SoundPackId] for playback, returning [SoundPackId.OFF]
     * whenever sound is disabled or [configuredPack] is [SoundPackId.OFF].
     */
    fun resolveEffectivePack(
        soundEnabled: Boolean,
        configuredPack: SoundPackId,
        themePackId: String?,
    ): SoundPackId {
        if (!soundEnabled || !configuredPack.isPlayable) return SoundPackId.OFF
        return configuredPack.resolve(themePackId)
    }

    /** Normalizes a user-selected custom SAF sound pack tree URI (trimmed, or empty if unset). */
    fun normalizeCustomPackUri(uri: String?): String = uri?.trim().orEmpty()

    /**
     * Merges materialized custom SAF cue paths over procedural fallback paths so any missing
     * cue cleanly falls back to the active built-in sound pack.
     */
    fun resolveCueLoadPaths(
        customPaths: Map<SoundCue, String>,
        fallbackPaths: Map<SoundCue, String>,
    ): Map<SoundCue, String> = CustomSoundPackMatcher.resolveWithFallback(customPaths, fallbackPaths)

    /**
     * Decides whether [SoundEngine.syncWithSettings] should no-op, release to idle,
     * or switch to a new concrete pack (or custom SAF sound pack override).
     */
    fun planSync(
        currentTargetPack: SoundPackId,
        nextEffectivePack: SoundPackId,
        hasActiveOrPendingSession: Boolean,
        currentCustomPackUri: String = "",
        nextCustomPackUri: String = "",
    ): SyncAction {
        if (!nextEffectivePack.isPlayable) {
            return if (hasActiveOrPendingSession || currentTargetPack != SoundPackId.OFF) {
                SyncAction.RELEASE_TO_IDLE
            } else {
                SyncAction.NO_OP
            }
        }
        val curCustom = normalizeCustomPackUri(currentCustomPackUri)
        val nextCustom = normalizeCustomPackUri(nextCustomPackUri)
        return if (hasActiveOrPendingSession &&
            currentTargetPack == nextEffectivePack &&
            curCustom == nextCustom
        ) {
            SyncAction.NO_OP
        } else {
            SyncAction.SWITCH_PACK
        }
    }

    /**
     * Builds the versioned cache filename `<packId>_v<ver>_<cue>.wav` for a concrete pack.
     */
    fun cacheFileName(
        pack: SoundPackId,
        cue: SoundCue,
        version: Int = SYNTH_CACHE_VERSION,
    ): String {
        require(pack in SoundPackId.CONCRETE_PACKS) {
            "Cache files require a concrete playable pack, got $pack"
        }
        require(version >= 1) { "Cache version must be >= 1, got $version" }
        return "${pack.id}_v${version}_${cue.name.lowercase()}.wav"
    }

    /**
     * Returns `true` iff [fileName] is a valid cache file for [currentVersion], a known
     * concrete pack, and a known [SoundCue].
     */
    fun isCurrentCacheFileName(
        fileName: String,
        currentVersion: Int = SYNTH_CACHE_VERSION,
    ): Boolean {
        if (currentVersion < 1) return false
        if (!fileName.endsWith(".wav")) return false
        val stem = fileName.removeSuffix(".wav")
        val marker = "_v${currentVersion}_"
        val idx = stem.indexOf(marker)
        if (idx <= 0) return false
        val packPart = stem.substring(0, idx)
        val cuePart = stem.substring(idx + marker.length)
        return packPart in CONCRETE_PACK_IDS && cuePart in VALID_CUE_NAMES
    }

    /**
     * Filters [fileNames] in `gg_sfx/` down to stale entries (older synth versions, orphaned
     * `.tmp` writes, retired packs/cues, or unrecognized junk) safe to delete.
     */
    fun staleCacheFiles(
        fileNames: Iterable<String>,
        currentVersion: Int = SYNTH_CACHE_VERSION,
    ): List<String> = fileNames.filter { !isCurrentCacheFileName(it, currentVersion) }

    /**
     * Validates a 44-byte RIFF/WAVE header against the actual on-disk [fileLengthBytes].
     */
    fun isValidWavHeader(
        headerBytes: ByteArray,
        fileLengthBytes: Long,
        expectedSampleRate: Int = PcmSynth.DEFAULT_SAMPLE_RATE,
        expectedChannels: Int = 1,
    ): Boolean {
        if (fileLengthBytes <= WavEncoder.HEADER_BYTES) return false
        val info = WavEncoder.inspectHeader(headerBytes) ?: return false
        if (info.audioFormat != WavEncoder.AUDIO_FORMAT_PCM) return false
        if (info.bitsPerSample != WavEncoder.BITS_PER_SAMPLE) return false
        if (info.channels != expectedChannels) return false
        if (info.sampleRate != expectedSampleRate) return false
        if (info.dataBytes <= 0) return false
        val frameBytes = expectedChannels * (WavEncoder.BITS_PER_SAMPLE / 8)
        if (info.dataBytes % frameBytes != 0) return false
        return WavEncoder.HEADER_BYTES.toLong() + info.dataBytes.toLong() == fileLengthBytes
    }

    /** Maps Android `AudioManager.RINGER_MODE_*` ints to [AudioPolicy.RingerState]. */
    fun mapRingerMode(androidRingerMode: Int): AudioPolicy.RingerState = when (androidRingerMode) {
        ANDROID_RINGER_MODE_SILENT -> AudioPolicy.RingerState.SILENT
        ANDROID_RINGER_MODE_VIBRATE -> AudioPolicy.RingerState.VIBRATE
        ANDROID_RINGER_MODE_NORMAL -> AudioPolicy.RingerState.NORMAL
        else -> AudioPolicy.RingerState.NORMAL
    }

    /** Returns `true` when the cached system AudioManager probe TTL has expired. */
    fun shouldRefreshSystemProbe(
        lastProbeMs: Long,
        nowMs: Long,
        ttlMs: Long = SYSTEM_AUDIO_CACHE_TTL_MS,
    ): Boolean {
        if (lastProbeMs == Long.MIN_VALUE) return true
        val elapsed = nowMs - lastProbeMs
        return elapsed < 0L || elapsed >= ttlMs.coerceAtLeast(0L)
    }

    /**
     * Masks [AudioManager.isMusicActive] when our own [AmbientEngine] is playing (`USAGE_GAME`/
     * `CONTENT_TYPE_MUSIC`), so Ghost Galleon's own ambient soundscape never tricks [SoundEngine]
     * into treating it as external user music (Spotify/podcasts).
     */
    fun isExternalMusicActive(
        systemMusicActive: Boolean,
        ownAmbientPlaying: Boolean,
    ): Boolean = systemMusicActive && !ownAmbientPlaying

    /**
     * Returns `true` iff a cold-start boot chime queued during async warm-up is still within
     * its freshness window ([ttlMs]) and safe to play once [SoundCue.BOOT_CHIME] finishes decoding.
     */
    fun isPendingBootChimeValid(
        requestedAtMs: Long,
        nowMs: Long,
        ttlMs: Long = BOOT_CHIME_PENDING_TTL_MS,
    ): Boolean {
        if (requestedAtMs == Long.MIN_VALUE) return false
        val elapsed = nowMs - requestedAtMs
        return elapsed in 0L..ttlMs.coerceAtLeast(0L)
    }

    /**
     * Determines whether an explicit Settings audition or pack preview is allowed to play.
     *
     * Respects global sound enable, volume > 0, playable target pack, screen/foreground state,
     * and ringer etiquette, while ignoring `hasKeepSession` so Settings previews work even if
     * an emulator session is open on another panel.
     */
    fun shouldAudition(
        soundEnabled: Boolean,
        targetPack: SoundPackId,
        sfxVolumePercent: Int,
        screenOff: Boolean,
        backgrounded: Boolean,
        respectRinger: Boolean,
        ringerState: AudioPolicy.RingerState,
    ): Boolean {
        if (!soundEnabled || !targetPack.isPlayable || sfxVolumePercent <= 0) return false
        if (screenOff || backgrounded) return false
        if (respectRinger && ringerState != AudioPolicy.RingerState.NORMAL) return false
        return true
    }

    /**
     * Computes stereo gains, playback rate, and priority for a standard [SoundEngine.play] call.
     * Returns `null` when the resulting gain is inaudible.
     */
    fun computePlayParams(
        cue: SoundCue,
        sfxVolumePercent: Int,
        pan: Float = 0f,
        repeatStreak: Int = 0,
        pitchMul: Float = 1f,
        volumeMul: Float = 1f,
        respectMusic: Boolean = true,
        externalMusicActive: Boolean = false,
        videoPreviewUnmuted: Boolean = false,
        hasKeepSession: Boolean = false,
        playHudQuiet: Boolean = true,
    ): PlayParams? {
        val baseGain = AudioPolicy.volumePercentToGain(sfxVolumePercent)
        if (baseGain <= MIN_AUDIBLE_GAIN) return null

        val expr = AudioPolicy.repeatExpression(repeatStreak)
        val duck = AudioPolicy.sfxDuckFactor(
            cue = cue,
            respectMusic = respectMusic,
            externalMusicActive = externalMusicActive,
            videoPreviewUnmuted = videoPreviewUnmuted,
            hasKeepSession = hasKeepSession,
            playHudQuiet = playHudQuiet,
        )
        val safeVolMul = if (volumeMul.isFinite()) volumeMul.coerceIn(0f, 1.5f) else 1f
        val masterGain = (baseGain * expr.gainMultiplier * duck * safeVolMul).coerceIn(0f, 1f)
        if (masterGain <= MIN_AUDIBLE_GAIN) return null

        val safePan = if (pan.isFinite()) pan.coerceIn(-1f, 1f) else 0f
        val stereo = AudioPolicy.stereoGains(masterGain, safePan)
        val safePitchMul = if (pitchMul.isFinite()) pitchMul else 1f
        val rate = (expr.pitchRate * safePitchMul).coerceIn(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)
        return PlayParams(
            leftGain = stereo.left.coerceIn(0f, 1f),
            rightGain = stereo.right.coerceIn(0f, 1f),
            rate = rate,
            priority = cue.priority,
        )
    }

    /**
     * Computes stereo gains, unity rate, and priority for [SoundEngine.audition] and
     * [SoundEngine.previewPack] calls.
     */
    fun computeAuditionParams(
        cue: SoundCue,
        sfxVolumePercent: Int,
        pan: Float = 0f,
        respectMusic: Boolean = true,
        externalMusicActive: Boolean = false,
        videoPreviewUnmuted: Boolean = false,
    ): PlayParams? = computePlayParams(
        cue = cue,
        sfxVolumePercent = sfxVolumePercent,
        pan = pan,
        repeatStreak = 0,
        pitchMul = 1f,
        volumeMul = 1f,
        respectMusic = respectMusic,
        externalMusicActive = externalMusicActive,
        videoPreviewUnmuted = videoPreviewUnmuted,
        hasKeepSession = false,
        playHudQuiet = false,
    )
}

/**
 * App-scoped, off-UI-thread `SoundPool` runtime for Ghost Galleon UI/system SFX.
 *
 * Key invariants:
 * - Uses `USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SONIFICATION` and **NEVER** requests
 *   `AUDIOFOCUS_GAIN`, so UI SFX never pause or interrupt Spotify, podcasts, or emulators.
 * - All PCM synthesis, WAV cache I/O, `SoundPool.load`, and `SoundPool.release` run exclusively
 *   on a dedicated daemon single-thread executor (`AUDIO_SYNTH`), never touching the UI thread
 *   or sharing `ROM_IO`/`APP_IO`.
 * - Atomic generation gating plus per-session `ReentrantReadWriteLock` (`tryLock` on UI thread)
 *   ensure rapid pack/theme switches never throw `IllegalStateException` or block UI input.
 */
class SoundEngine(
    context: Context,
    private val configProvider: () -> SoundEngineConfig,
    private val runtimeStateProvider: () -> SoundRuntimeState = { SoundRuntimeState() },
    private val isOwnAmbientPlaying: () -> Boolean = { false },
    private val cacheVersion: Int = SoundEngineLogic.SYNTH_CACHE_VERSION,
) {
    private val appContext: Context = context.applicationContext
    private val cacheDir: File = File(appContext.cacheDir, SoundEngineLogic.CACHE_DIR_NAME)
    private val audioManager: AudioManager? = runCatching {
        appContext.getSystemService(AudioManager::class.java)
    }.getOrNull()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cooldownTracker = AudioPolicy.CooldownTracker()
    private val generationGate = SoundEngineLogic.GenerationGate()
    private val previewToken = AtomicLong(0L)
    private val pendingBootChimeRequestedAtMs = AtomicLong(NO_PENDING_TIME)
    private val staleCacheCleaned = AtomicBoolean(false)
    private val backgrounded = AtomicBoolean(false)

    private val stateLock = Any()
    @Volatile
    private var targetPack: SoundPackId = SoundPackId.OFF
    @Volatile
    private var targetCustomPackUri: String = ""
    private var pendingLoadGeneration: Long = NO_GENERATION

    private val activeSession = AtomicReference<PoolSession?>(null)
    private val auditionSession = AtomicReference<PoolSession?>(null)
    private val pendingAudition = AtomicReference<PendingAudition?>(null)

    private val previewLock = Any()
    private val pendingPreviewRunnables = mutableListOf<Runnable>()

    @Volatile
    private var lastSystemProbeMs: Long = Long.MIN_VALUE
    @Volatile
    private var cachedRingerState: AudioPolicy.RingerState = AudioPolicy.RingerState.NORMAL
    @Volatile
    private var cachedSystemMusicActive: Boolean = false

    private class PoolSession(
        val generation: Long,
        val pack: SoundPackId,
        val customPackUri: String,
        val pool: SoundPool,
        maxTrackedStreams: Int,
    ) {
        val lock = ReentrantReadWriteLock()
        val sampleIds = AtomicIntegerArray(SoundCue.entries.size)
        val loadedSampleIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
        val recentStreams = IntArray(maxTrackedStreams.coerceAtLeast(1))
        var recentStreamCursor: Int = 0
        @Volatile
        var released: Boolean = false
        val releaseScheduled = AtomicBoolean(false)

        fun recordStream(streamId: Int) {
            if (streamId == 0) return
            synchronized(recentStreams) {
                recentStreams[recentStreamCursor] = streamId
                recentStreamCursor = (recentStreamCursor + 1) % recentStreams.size
            }
        }

        fun drainRecentStreams(): IntArray = synchronized(recentStreams) {
            val copy = recentStreams.copyOf()
            recentStreams.fill(0)
            recentStreamCursor = 0
            copy
        }
    }

    private data class PendingAudition(
        val token: Long,
        val steps: List<SoundEngineLogic.PreviewStep>,
        val session: PoolSession,
    )

    /** Currently loaded active concrete pack, or [SoundPackId.OFF] while idle/loading. */
    val activePack: SoundPackId
        get() {
            val session = activeSession.get()
            return if (session != null && !session.released) session.pack else SoundPackId.OFF
        }

    /** True once the active pool has decoded core interactive cues (`NAV_TICK` + `CONFIRM`). */
    val isReady: Boolean
        get() = isCueLoaded(SoundCue.NAV_TICK) && isCueLoaded(SoundCue.CONFIRM)

    /** True if [cue] is decoded and ready for zero-latency playback on the active pool. */
    fun isCueLoaded(cue: SoundCue): Boolean {
        val session = activeSession.get() ?: return false
        if (session.released) return false
        val sampleId = session.sampleIds.get(cue.ordinal)
        return sampleId != 0 && session.loadedSampleIds.contains(sampleId)
    }

    /**
     * Kicks off asynchronous synthesis/cache check and `SoundPool` preload for the currently
     * configured pack. Safe to call during [android.app.Application.onCreate].
     */
    fun warmUp() {
        syncWithSettings()
    }

    /**
     * Reconciles the loaded `SoundPool` against the latest [SoundEngineConfig].
     *
     * - Idempotent when the resolved concrete pack is already active or loading (e.g. volume
     *   or non-pack settings changed).
     * - Releases the active `SoundPool` cleanly when sound is disabled or pack is [SoundPackId.OFF].
     * - Bumps the atomic generation counter and loads the new pack asynchronously on pack/theme switch.
     */
    fun syncWithSettings() {
        invalidateSystemProbe()
        val config = runCatching { configProvider() }.getOrDefault(SoundEngineConfig())
        val nextPack = config.resolvedPack()
        val nextCustomUri = SoundEngineLogic.normalizeCustomPackUri(config.customSoundPackUri)

        val oldSession: PoolSession?
        val action: SoundEngineLogic.SyncAction
        val loadGen: Long
        val shouldClearCustomCache: Boolean

        synchronized(stateLock) {
            val currentSession = activeSession.get()
            val hasSession = (currentSession != null && !currentSession.released) ||
                (pendingLoadGeneration == generationGate.current() && pendingLoadGeneration != NO_GENERATION)
            shouldClearCustomCache = targetCustomPackUri.isNotEmpty() && nextCustomUri.isEmpty()
            action = SoundEngineLogic.planSync(
                currentTargetPack = targetPack,
                nextEffectivePack = nextPack,
                hasActiveOrPendingSession = hasSession,
                currentCustomPackUri = targetCustomPackUri,
                nextCustomPackUri = nextCustomUri,
            )
            when (action) {
                SoundEngineLogic.SyncAction.NO_OP -> {
                    if (shouldClearCustomCache) {
                        targetCustomPackUri = ""
                    }
                    oldSession = null
                    loadGen = NO_GENERATION
                }
                SoundEngineLogic.SyncAction.RELEASE_TO_IDLE -> {
                    stopSessionStreams(activeSession.get())
                    stopSessionStreams(auditionSession.get())
                    loadGen = generationGate.advance()
                    targetPack = SoundPackId.OFF
                    targetCustomPackUri = nextCustomUri
                    pendingLoadGeneration = NO_GENERATION
                    oldSession = activeSession.getAndSet(null)
                    oldSession?.released = true
                }
                SoundEngineLogic.SyncAction.SWITCH_PACK -> {
                    loadGen = generationGate.advance()
                    targetPack = nextPack
                    targetCustomPackUri = nextCustomUri
                    pendingLoadGeneration = loadGen
                    oldSession = activeSession.getAndSet(null)
                    oldSession?.released = true
                }
            }
        }

        when (action) {
            SoundEngineLogic.SyncAction.NO_OP -> {
                if (shouldClearCustomCache) {
                    AUDIO_SYNTH.execute { CustomSoundPackLoader.clearCache(appContext) }
                }
            }
            SoundEngineLogic.SyncAction.RELEASE_TO_IDLE -> {
                pendingBootChimeRequestedAtMs.set(NO_PENDING_TIME)
                cancelPendingPreviews()
                val oldAudition = detachAuditionSession()
                if (oldSession != null || oldAudition != null || shouldClearCustomCache) {
                    AUDIO_SYNTH.execute {
                        retireSession(oldSession)
                        retireSession(oldAudition)
                        if (shouldClearCustomCache) {
                            CustomSoundPackLoader.clearCache(appContext)
                        }
                    }
                }
            }
            SoundEngineLogic.SyncAction.SWITCH_PACK -> {
                AUDIO_SYNTH.execute {
                    retireSession(oldSession)
                    if (shouldClearCustomCache) {
                        CustomSoundPackLoader.clearCache(appContext)
                    }
                    loadPackSession(nextPack, nextCustomUri, loadGen)
                }
            }
        }
    }

    /**
     * Scans a user-selected SAF sound pack folder off the UI thread on [AUDIO_SYNTH]
     * and delivers [CustomPackScanResult] back on the main looper.
     */
    fun scanCustomSoundPackAsync(
        treeUriString: String?,
        onResult: (CustomPackScanResult) -> Unit,
    ) {
        AUDIO_SYNTH.execute {
            val result = runCatching {
                CustomSoundPackLoader.scanTree(appContext, treeUriString)
            }.getOrDefault(CustomPackScanResult(cueEntries = emptyMap(), totalMatched = 0))
            mainHandler.post { onResult(result) }
        }
    }

    /** Clears materialized `custom_*` sound pack cache directories off the UI thread. */
    fun clearCustomSoundPackCacheAsync() {
        AUDIO_SYNTH.execute { CustomSoundPackLoader.clearCache(appContext) }
    }

    /**
     * Plays a UI/system [cue] on the active `SoundPool` if permitted by [AudioPolicy].
     *
     * Never blocks the UI thread, never requests audio focus, and returns silently while
     * asynchronous warm-up is still completing.
     *
     * @return `true` if a `SoundPool` stream was started, `false` if suppressed/throttled/unloaded.
     */
    fun play(
        cue: SoundCue,
        pan: Float = 0f,
        repeatStreak: Int = 0,
        pitchMul: Float = 1f,
        volumeMul: Float = 1f,
    ): Boolean {
        if (backgrounded.get()) return false
        val config = runCatching { configProvider() }.getOrDefault(SoundEngineConfig())
        val effectivePack = config.resolvedPack()
        if (!effectivePack.isPlayable) return false
        val effectiveCustomUri = SoundEngineLogic.normalizeCustomPackUri(config.customSoundPackUri)

        val session = activeSession.get()
        if (session == null ||
            session.released ||
            session.pack != effectivePack ||
            session.customPackUri != effectiveCustomUri
        ) {
            // Self-heal if theme, pack, or custom SAF folder changed before syncWithSettings() was called.
            if (targetPack != effectivePack || targetCustomPackUri != effectiveCustomUri) {
                syncWithSettings()
            }
            return false
        }

        val sampleId = session.sampleIds.get(cue.ordinal)
        if (sampleId == 0 || !session.loadedSampleIds.contains(sampleId)) return false

        val runtime = runCatching { runtimeStateProvider() }.getOrDefault(SoundRuntimeState())
        val nowMs = SystemClock.elapsedRealtime()
        refreshSystemProbeIfNeeded(nowMs, config.respectRinger, config.respectMusic)

        val ringerState = cachedRingerState
        val externalMusicActive = effectiveExternalMusicActive()

        val allowed = AudioPolicy.shouldPlaySfx(
            soundEnabled = config.soundEnabled,
            pack = effectivePack,
            sfxVolumePercent = config.sfxVolumePercent,
            cue = cue,
            respectRinger = config.respectRinger,
            ringerState = ringerState,
            respectMusic = config.respectMusic,
            externalMusicActive = externalMusicActive,
            bootChimeEnabled = config.bootChimeEnabled,
            achievementChimeEnabled = config.achievementChimeEnabled,
            activeAppActivityCount = runtime.activeAppActivityCount,
            hasKeepSession = runtime.hasKeepSession,
            screenOff = runtime.screenOff,
        )
        if (!allowed) return false

        val params = SoundEngineLogic.computePlayParams(
            cue = cue,
            sfxVolumePercent = config.sfxVolumePercent,
            pan = pan,
            repeatStreak = repeatStreak,
            pitchMul = pitchMul,
            volumeMul = volumeMul,
            respectMusic = config.respectMusic,
            externalMusicActive = externalMusicActive,
            videoPreviewUnmuted = config.videoPreviewUnmuted,
            hasKeepSession = runtime.hasKeepSession,
            playHudQuiet = config.playHudQuiet,
        ) ?: return false

        if (!cooldownTracker.tryAcquire(cue, nowMs)) return false
        return playOnSession(session, sampleId, params)
    }

    /**
     * Plays [SoundCue.BOOT_CHIME] immediately if decoded, or arms a bounded one-shot pending
     * trigger (`2,500 ms` TTL) so it fires automatically as soon as cold-start async warm-up
     * finishes decoding `BOOT_CHIME` while the launcher stays foregrounded and out of KEEP sessions.
     */
    fun requestBootChime(): Boolean {
        if (backgrounded.get()) return false
        val config = runCatching { configProvider() }.getOrDefault(SoundEngineConfig())
        val effectivePack = config.resolvedPack()
        if (!config.soundEnabled || !config.bootChimeEnabled || !effectivePack.isPlayable) {
            return false
        }
        val runtime = runCatching { runtimeStateProvider() }.getOrDefault(SoundRuntimeState())
        if (runtime.screenOff || runtime.hasKeepSession || runtime.activeAppActivityCount <= 0) {
            return false
        }

        if (play(SoundCue.BOOT_CHIME)) {
            pendingBootChimeRequestedAtMs.set(NO_PENDING_TIME)
            return true
        }

        val current = activeSession.get()
        if (current != null && !current.released && current.pack == effectivePack &&
            isCueLoaded(SoundCue.BOOT_CHIME)
        ) {
            // Already loaded; suppressed by ringer/external-music/volume policy, so do not pend.
            return false
        }

        syncWithSettings()
        pendingBootChimeRequestedAtMs.set(SystemClock.elapsedRealtime())
        // Re-check immediately in case BOOT_CHIME finished decoding concurrently.
        return maybeFirePendingBootChime()
    }

    /**
     * Auditions a single [cue] (optionally for [packOverride]) without blocking the UI thread.
     * Intended for Settings sliders and test buttons.
     */
    fun audition(cue: SoundCue, packOverride: SoundPackId? = null) {
        cancelPendingPreviews()
        val config = runCatching { configProvider() }.getOrDefault(SoundEngineConfig())
        val target = (packOverride ?: config.soundPackId).resolve(config.themePackId)
        if (!canAudition(config, target)) return
        val token = previewToken.incrementAndGet()
        val steps = listOf(SoundEngineLogic.PreviewStep(cue = cue, delayMs = 0L, pan = 0f))
        executePreviewSteps(target, steps, token)
    }

    /**
     * Plays a crisp signature 2-note motif ([SoundCue.NAV_TICK] → [SoundCue.CONFIRM] spaced
     * ~110 ms) for browsing sound packs in Settings without blocking the UI thread.
     */
    fun previewPack(packId: SoundPackId) {
        cancelPendingPreviews()
        val config = runCatching { configProvider() }.getOrDefault(SoundEngineConfig())
        val target = packId.resolve(config.themePackId)
        if (!canAudition(config, target)) return
        val token = previewToken.incrementAndGet()
        executePreviewSteps(target, SoundEngineLogic.PREVIEW_MOTIF, token)
    }

    /**
     * Called when all tracked Ghost Galleon activities have stopped (app moved to background).
     * Stops active SFX streams, cancels pending preview motifs, and frees any temporary audition
     * pool while keeping the primary loaded pool warm for instant launcher return.
     */
    fun onAppBackgrounded() {
        backgrounded.set(true)
        pendingBootChimeRequestedAtMs.set(NO_PENDING_TIME)
        cancelPendingPreviews()
        stopSessionStreams(activeSession.get())
        stopSessionStreams(auditionSession.get())
        val oldAudition = detachAuditionSession()
        if (oldAudition != null) {
            AUDIO_SYNTH.execute { retireSession(oldAudition) }
        }
    }

    /**
     * Called when the first tracked Ghost Galleon activity enters the foreground.
     * Refreshes system audio etiquette probes and ensures the active pack matches settings.
     */
    fun onAppForegrounded() {
        backgrounded.set(false)
        invalidateSystemProbe()
        cooldownTracker.reset()
        syncWithSettings()
    }

    /**
     * Called on `ACTION_SCREEN_OFF` to silence any active streams and pending preview motifs.
     */
    fun onScreenOff() {
        pendingBootChimeRequestedAtMs.set(NO_PENDING_TIME)
        cancelPendingPreviews()
        stopSessionStreams(activeSession.get())
        stopSessionStreams(auditionSession.get())
    }

    /**
     * Safe, idempotent teardown releasing all active and audition `SoundPool` instances off-UI.
     */
    fun release() {
        pendingBootChimeRequestedAtMs.set(NO_PENDING_TIME)
        cancelPendingPreviews()
        cooldownTracker.reset()
        stopSessionStreams(activeSession.get())
        stopSessionStreams(auditionSession.get())
        val oldActive: PoolSession?
        synchronized(stateLock) {
            generationGate.advance()
            targetPack = SoundPackId.OFF
            targetCustomPackUri = ""
            pendingLoadGeneration = NO_GENERATION
            oldActive = activeSession.getAndSet(null)
            oldActive?.released = true
        }
        val oldAudition = detachAuditionSession()
        if (oldActive != null || oldAudition != null) {
            AUDIO_SYNTH.execute {
                retireSession(oldActive)
                retireSession(oldAudition)
            }
        }
    }

    private fun canAudition(config: SoundEngineConfig, targetPack: SoundPackId): Boolean {
        val runtime = runCatching { runtimeStateProvider() }.getOrDefault(SoundRuntimeState())
        val nowMs = SystemClock.elapsedRealtime()
        refreshSystemProbeIfNeeded(nowMs, config.respectRinger, config.respectMusic)
        return SoundEngineLogic.shouldAudition(
            soundEnabled = config.soundEnabled,
            targetPack = targetPack,
            sfxVolumePercent = config.sfxVolumePercent,
            screenOff = runtime.screenOff,
            backgrounded = backgrounded.get(),
            respectRinger = config.respectRinger,
            ringerState = cachedRingerState,
        )
    }

    private fun executePreviewSteps(
        targetPack: SoundPackId,
        steps: List<SoundEngineLogic.PreviewStep>,
        token: Long,
    ) {
        // Fast path: active pool already matches targetPack and has every cue decoded.
        val current = activeSession.get()
        if (current != null && !current.released && current.pack == targetPack &&
            areAllStepCuesLoaded(current, steps)
        ) {
            dispatchPreviewSteps(current, steps, token)
            return
        }

        // Slow path: synthesize/load only the needed cues off the UI thread.
        AUDIO_SYNTH.execute {
            if (previewToken.get() != token || backgrounded.get()) return@execute
            ensureStaleCacheCleaned()

            // Re-check activeSession on AUDIO_SYNTH (it may have just finished submitting/loading).
            val active = activeSession.get()
            if (active != null && !active.released && active.pack == targetPack &&
                steps.all { active.sampleIds.get(it.cue.ordinal) != 0 }
            ) {
                armOrDispatchPreview(active, steps, token)
                return@execute
            }

            val session = getOrCreateAuditionSession(targetPack, token) ?: return@execute
            for (step in steps) {
                if (previewToken.get() != token || backgrounded.get() || session.released) {
                    return@execute
                }
                if (session.sampleIds.get(step.cue.ordinal) == 0) {
                    val wavFile = ensureCachedWav(targetPack, step.cue) ?: continue
                    val sampleId = runCatching {
                        session.pool.load(wavFile.absolutePath, 1)
                    }.onFailure {
                        Log.w(TAG, "Audition SoundPool.load failed for ${step.cue}", it)
                    }.getOrDefault(0)
                    if (sampleId != 0) {
                        session.sampleIds.set(step.cue.ordinal, sampleId)
                    }
                }
            }
            if (previewToken.get() != token || backgrounded.get() || session.released) {
                return@execute
            }
            armOrDispatchPreview(session, steps, token)
        }
    }

    private fun getOrCreateAuditionSession(targetPack: SoundPackId, token: Long): PoolSession? {
        val existing = auditionSession.get()
        if (existing != null && !existing.released && existing.pack == targetPack) {
            return existing
        }
        val old = detachAuditionSession()
        retireSession(old)

        if (previewToken.get() != token || backgrounded.get()) return null

        val pool = runCatching {
            buildSoundPool(SoundEngineLogic.AUDITION_MAX_STREAMS)
        }.onFailure {
            Log.w(TAG, "Failed creating audition SoundPool", it)
        }.getOrNull() ?: return null

        val session = PoolSession(
            generation = token,
            pack = targetPack,
            customPackUri = "",
            pool = pool,
            maxTrackedStreams = SoundEngineLogic.AUDITION_MAX_STREAMS,
        )
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && !session.released) {
                session.loadedSampleIds.add(sampleId)
                maybeFirePendingAudition(session)
            }
        }
        val published = synchronized(stateLock) {
            if (previewToken.get() != token || backgrounded.get()) {
                false
            } else {
                auditionSession.set(session)
                true
            }
        }
        if (!published) {
            retireSession(session)
            return null
        }
        return session
    }

    private fun armOrDispatchPreview(
        session: PoolSession,
        steps: List<SoundEngineLogic.PreviewStep>,
        token: Long,
    ) {
        if (areAllStepCuesLoaded(session, steps)) {
            dispatchPreviewSteps(session, steps, token)
            return
        }
        val pending = PendingAudition(token = token, steps = steps, session = session)
        pendingAudition.set(pending)
        // Re-check after publishing so we never miss a concurrent OnLoadComplete callback.
        maybeFirePendingAudition(session)
    }

    private fun maybeFirePendingAudition(session: PoolSession) {
        val pending = pendingAudition.get() ?: return
        if (pending.session !== session) return
        if (previewToken.get() != pending.token || backgrounded.get() || session.released) {
            pendingAudition.compareAndSet(pending, null)
            return
        }
        if (!areAllStepCuesLoaded(session, pending.steps)) return
        if (pendingAudition.compareAndSet(pending, null)) {
            dispatchPreviewSteps(session, pending.steps, pending.token)
        }
    }

    private fun areAllStepCuesLoaded(
        session: PoolSession,
        steps: List<SoundEngineLogic.PreviewStep>,
    ): Boolean = steps.all { step ->
        val sampleId = session.sampleIds.get(step.cue.ordinal)
        sampleId != 0 && session.loadedSampleIds.contains(sampleId)
    }

    private fun dispatchPreviewSteps(
        session: PoolSession,
        steps: List<SoundEngineLogic.PreviewStep>,
        token: Long,
    ) {
        for (step in steps) {
            if (step.delayMs <= 0L) {
                playAuditionStep(session, step, token)
            } else {
                lateinit var runnable: Runnable
                runnable = Runnable {
                    synchronized(previewLock) {
                        pendingPreviewRunnables.remove(runnable)
                    }
                    playAuditionStep(session, step, token)
                }
                synchronized(previewLock) {
                    if (previewToken.get() != token || backgrounded.get()) return
                    pendingPreviewRunnables.add(runnable)
                }
                mainHandler.postDelayed(runnable, step.delayMs)
            }
        }
    }

    private fun playAuditionStep(
        session: PoolSession,
        step: SoundEngineLogic.PreviewStep,
        token: Long,
    ) {
        if (previewToken.get() != token || backgrounded.get() || session.released) return
        val config = runCatching { configProvider() }.getOrDefault(SoundEngineConfig())
        val runtime = runCatching { runtimeStateProvider() }.getOrDefault(SoundRuntimeState())
        if (runtime.screenOff) return
        val nowMs = SystemClock.elapsedRealtime()
        refreshSystemProbeIfNeeded(nowMs, config.respectRinger, config.respectMusic)

        val params = SoundEngineLogic.computeAuditionParams(
            cue = step.cue,
            sfxVolumePercent = config.sfxVolumePercent,
            pan = step.pan,
            respectMusic = config.respectMusic,
            externalMusicActive = effectiveExternalMusicActive(),
            videoPreviewUnmuted = config.videoPreviewUnmuted,
        ) ?: return

        val sampleId = session.sampleIds.get(step.cue.ordinal)
        if (sampleId != 0 && session.loadedSampleIds.contains(sampleId)) {
            playOnSession(session, sampleId, params)
        }
    }

    private fun cancelPendingPreviews() {
        previewToken.incrementAndGet()
        pendingAudition.set(null)
        val callbacks = synchronized(previewLock) {
            val copy = pendingPreviewRunnables.toList()
            pendingPreviewRunnables.clear()
            copy
        }
        callbacks.forEach { mainHandler.removeCallbacks(it) }
    }

    private fun loadPackSession(pack: SoundPackId, customPackUri: String, generation: Long) {
        if (!generationGate.isCurrent(generation)) return
        ensureStaleCacheCleaned()

        val customPaths: Map<SoundCue, String> = if (customPackUri.isNotEmpty()) {
            runCatching {
                CustomSoundPackLoader.materializeToCache(
                    context = appContext,
                    treeUriString = customPackUri,
                    includeAmbient = false,
                ).cachedCuePaths
            }.onFailure {
                Log.w(TAG, "CustomSoundPackLoader failed for $customPackUri", it)
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        if (!generationGate.isCurrent(generation)) return

        val pool = runCatching {
            buildSoundPool(SoundEngineLogic.MAX_STREAMS)
        }.onFailure {
            Log.w(TAG, "Failed creating SoundPool for $pack", it)
        }.getOrNull()

        if (pool == null) {
            clearPendingLoadIfMatches(generation)
            return
        }

        val session = PoolSession(
            generation = generation,
            pack = pack,
            customPackUri = customPackUri,
            pool = pool,
            maxTrackedStreams = SoundEngineLogic.MAX_STREAMS,
        )
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && !session.released && generationGate.isCurrent(generation)) {
                session.loadedSampleIds.add(sampleId)
                maybeFirePendingAudition(session)
                if (sampleId == session.sampleIds.get(SoundCue.BOOT_CHIME.ordinal)) {
                    maybeFirePendingBootChime(session)
                }
            }
        }

        val published = synchronized(stateLock) {
            if (!generationGate.isCurrent(generation)) {
                false
            } else {
                activeSession.set(session)
                if (pendingLoadGeneration == generation) {
                    pendingLoadGeneration = NO_GENERATION
                }
                true
            }
        }
        if (!published) {
            retireSession(session)
            return
        }

        // Discard any stale audition pool targeting this same pack now that activeSession owns it.
        val existingAudition = auditionSession.get()
        if (existingAudition != null && existingAudition.pack == pack &&
            auditionSession.compareAndSet(existingAudition, null)
        ) {
            existingAudition.released = true
            retireSession(existingAudition)
        }

        for (cue in SoundEngineLogic.LOAD_ORDER) {
            if (!generationGate.isCurrent(generation) || session.released) {
                retireSession(session)
                return
            }
            val customCueMap = customPaths[cue]?.let { mapOf(cue to it) }.orEmpty()
            val fallbackCueMap = if (customCueMap.isEmpty()) {
                ensureCachedWav(pack, cue)?.absolutePath?.let { mapOf(cue to it) }.orEmpty()
            } else {
                emptyMap()
            }
            val resolvedPath = SoundEngineLogic.resolveCueLoadPaths(
                customPaths = customCueMap,
                fallbackPaths = fallbackCueMap,
            )[cue] ?: continue
            if (!generationGate.isCurrent(generation) || session.released) {
                retireSession(session)
                return
            }
            var sampleId = runCatching {
                session.pool.load(resolvedPath, 1)
            }.onFailure {
                Log.w(TAG, "SoundPool.load failed for $pack/$cue ($resolvedPath)", it)
            }.getOrDefault(0)
            // Graceful fallback to procedural WAV if a user-provided custom file fails to load.
            if (sampleId == 0 && customCueMap.isNotEmpty()) {
                val fallbackFile = ensureCachedWav(pack, cue)
                if (fallbackFile != null && generationGate.isCurrent(generation) && !session.released) {
                    sampleId = runCatching {
                        session.pool.load(fallbackFile.absolutePath, 1)
                    }.onFailure {
                        Log.w(TAG, "Fallback SoundPool.load failed for $pack/$cue", it)
                    }.getOrDefault(0)
                }
            }
            if (sampleId != 0) {
                session.sampleIds.set(cue.ordinal, sampleId)
                maybeFirePendingAudition(session)
                if (cue == SoundCue.BOOT_CHIME) {
                    maybeFirePendingBootChime(session)
                }
            }
        }
    }

    private fun maybeFirePendingBootChime(session: PoolSession? = activeSession.get()): Boolean {
        val reqMs = pendingBootChimeRequestedAtMs.get()
        if (reqMs == NO_PENDING_TIME) return false
        val nowMs = SystemClock.elapsedRealtime()
        if (!SoundEngineLogic.isPendingBootChimeValid(reqMs, nowMs)) {
            pendingBootChimeRequestedAtMs.compareAndSet(reqMs, NO_PENDING_TIME)
            return false
        }
        if (backgrounded.get()) {
            pendingBootChimeRequestedAtMs.compareAndSet(reqMs, NO_PENDING_TIME)
            return false
        }
        val config = runCatching { configProvider() }.getOrDefault(SoundEngineConfig())
        val effectivePack = config.resolvedPack()
        val runtime = runCatching { runtimeStateProvider() }.getOrDefault(SoundRuntimeState())
        if (!config.soundEnabled || !config.bootChimeEnabled || !effectivePack.isPlayable ||
            runtime.screenOff || runtime.hasKeepSession || runtime.activeAppActivityCount <= 0
        ) {
            pendingBootChimeRequestedAtMs.compareAndSet(reqMs, NO_PENDING_TIME)
            return false
        }
        val current = session ?: activeSession.get() ?: return false
        if (current.released || current.pack != effectivePack) return false
        val sampleId = current.sampleIds.get(SoundCue.BOOT_CHIME.ordinal)
        if (sampleId == 0 || !current.loadedSampleIds.contains(sampleId)) return false

        if (!pendingBootChimeRequestedAtMs.compareAndSet(reqMs, NO_PENDING_TIME)) return false
        return play(SoundCue.BOOT_CHIME)
    }

    private fun clearPendingLoadIfMatches(generation: Long) {
        synchronized(stateLock) {
            if (pendingLoadGeneration == generation) {
                pendingLoadGeneration = NO_GENERATION
            }
        }
    }

    private fun ensureStaleCacheCleaned() {
        if (!staleCacheCleaned.compareAndSet(false, true)) return
        runCatching {
            if (!cacheDir.isDirectory) return
            val children = cacheDir.listFiles() ?: return
            val staleNames = SoundEngineLogic.staleCacheFiles(
                fileNames = children.map { it.name },
                currentVersion = cacheVersion,
            ).toHashSet()
            for (child in children) {
                if (child.isFile && child.name in staleNames) {
                    runCatching { child.delete() }
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed pruning stale SFX cache", it)
        }
    }

    private fun ensureCachedWav(pack: SoundPackId, cue: SoundCue): File? {
        if (!cacheDir.exists() && !cacheDir.mkdirs() && !cacheDir.isDirectory) return null
        val target = File(cacheDir, SoundEngineLogic.cacheFileName(pack, cue, cacheVersion))
        if (isValidCachedWav(target)) return target

        val samples = runCatching { PcmSynth.synthesize(cue, pack) }
            .onFailure { Log.w(TAG, "PcmSynth failed for $pack/$cue", it) }
            .getOrNull() ?: return null
        if (samples.isEmpty()) return null

        val wavBytes = WavEncoder.encodePcm16(
            samples = samples,
            sampleRate = PcmSynth.DEFAULT_SAMPLE_RATE,
            channels = 1,
        )
        val tmp = File(cacheDir, "${target.name}.tmp")
        return runCatching {
            tmp.writeBytes(wavBytes)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                target.writeBytes(wavBytes)
                tmp.delete()
            }
            target
        }.onFailure {
            runCatching { tmp.delete() }
            Log.w(TAG, "Failed writing SFX cache ${target.name}", it)
        }.getOrNull()
    }

    private fun isValidCachedWav(file: File): Boolean {
        if (!file.isFile) return false
        val length = file.length()
        if (length <= WavEncoder.HEADER_BYTES) return false
        val header = ByteArray(WavEncoder.HEADER_BYTES)
        val bytesRead = runCatching {
            file.inputStream().use { input ->
                var offset = 0
                while (offset < header.size) {
                    val n = input.read(header, offset, header.size - offset)
                    if (n <= 0) break
                    offset += n
                }
                offset
            }
        }.getOrDefault(0)
        if (bytesRead < WavEncoder.HEADER_BYTES) return false
        return SoundEngineLogic.isValidWavHeader(
            headerBytes = header,
            fileLengthBytes = length,
            expectedSampleRate = PcmSynth.DEFAULT_SAMPLE_RATE,
            expectedChannels = 1,
        )
    }

    private fun buildSoundPool(maxStreams: Int): SoundPool {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        return SoundPool.Builder()
            .setMaxStreams(maxStreams)
            .setAudioAttributes(attributes)
            .build()
    }

    private fun playOnSession(
        session: PoolSession,
        sampleId: Int,
        params: SoundEngineLogic.PlayParams,
    ): Boolean {
        if (session.released) return false
        val readLock = session.lock.readLock()
        if (!readLock.tryLock()) return false
        try {
            if (session.released) return false
            val streamId = runCatching {
                session.pool.play(
                    sampleId,
                    params.leftGain,
                    params.rightGain,
                    params.priority,
                    0,
                    params.rate,
                )
            }.onFailure {
                Log.w(TAG, "SoundPool.play failed", it)
            }.getOrDefault(0)
            if (streamId != 0) {
                session.recordStream(streamId)
                return true
            }
            return false
        } finally {
            readLock.unlock()
        }
    }

    private fun stopSessionStreams(session: PoolSession?) {
        if (session == null || session.released) return
        val readLock = session.lock.readLock()
        if (!readLock.tryLock()) return
        try {
            if (session.released) return
            for (streamId in session.drainRecentStreams()) {
                if (streamId != 0) {
                    runCatching { session.pool.stop(streamId) }
                }
            }
        } finally {
            readLock.unlock()
        }
    }

    private fun detachAuditionSession(): PoolSession? = synchronized(stateLock) {
        val old = auditionSession.getAndSet(null)
        old?.released = true
        old
    }

    /**
     * Releases [session]'s native `SoundPool` at most once on [AUDIO_SYNTH], waiting only for
     * any already-in-flight non-blocking `play()` calls via the write lock.
     */
    private fun retireSession(session: PoolSession?) {
        if (session == null) return
        session.released = true
        if (!session.releaseScheduled.compareAndSet(false, true)) return
        val writeLock = session.lock.writeLock()
        writeLock.lock()
        try {
            session.loadedSampleIds.clear()
            runCatching { session.pool.release() }
                .onFailure { Log.w(TAG, "SoundPool.release failed for ${session.pack}", it) }
        } finally {
            writeLock.unlock()
        }
    }

    private fun invalidateSystemProbe() {
        lastSystemProbeMs = Long.MIN_VALUE
    }

    private fun effectiveExternalMusicActive(): Boolean {
        val ownAmbient = runCatching { isOwnAmbientPlaying() }.getOrDefault(false)
        return SoundEngineLogic.isExternalMusicActive(
            systemMusicActive = cachedSystemMusicActive,
            ownAmbientPlaying = ownAmbient,
        )
    }

    private fun refreshSystemProbeIfNeeded(
        nowMs: Long,
        queryRinger: Boolean,
        queryMusic: Boolean,
    ) {
        if (!queryRinger && !queryMusic) return
        if (!SoundEngineLogic.shouldRefreshSystemProbe(lastSystemProbeMs, nowMs)) return
        val am = audioManager
        if (am != null) {
            if (queryRinger) {
                val rawMode = runCatching { am.ringerMode }
                    .getOrDefault(SoundEngineLogic.ANDROID_RINGER_MODE_NORMAL)
                cachedRingerState = SoundEngineLogic.mapRingerMode(rawMode)
            }
            if (queryMusic) {
                cachedSystemMusicActive = runCatching { am.isMusicActive }.getOrDefault(false)
            }
        } else {
            cachedRingerState = AudioPolicy.RingerState.NORMAL
            cachedSystemMusicActive = false
        }
        lastSystemProbeMs = nowMs
    }

    private companion object {
        const val TAG = "GGSound"
        const val NO_GENERATION = -1L
        const val NO_PENDING_TIME = Long.MIN_VALUE

        val AUDIO_SYNTH: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "AUDIO_SYNTH").apply { isDaemon = true }
        }
    }
}
