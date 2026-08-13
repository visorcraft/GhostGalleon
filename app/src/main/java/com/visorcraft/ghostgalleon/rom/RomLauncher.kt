package com.visorcraft.ghostgalleon.rom

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.deck.launchOnOtherDisplay

/**
 * Android-free description of a launch intent, built from a platform's
 * [PlayerTemplate] plus a [RomEntry]. Host-tested; [RomLauncher.launch] is a
 * thin wrapper that turns this into a real Intent.
 */
data class LaunchPlan(
    val action: String?,
    val packageName: String,
    val className: String,
    // Intent data (URI-style players); null for PATH-style (RetroArch).
    val dataString: String?,
    val extras: Map<String, String>,
    val grantRead: Boolean,
    // Activity flags from the template (NEW_TASK always; CLEAR_TASK/CLEAR_TOP
    // where the registry carries them - prevents repeat-launch ANRs).
    val flags: Int,
)

/** Pure template interpreter. Returns null when the entry cannot fill the
 *  template (PATH-style player with no reconstructed filesystem path). */
object LaunchPlanBuilder {

    fun build(template: PlayerTemplate, entry: RomEntry): LaunchPlan? {
        val path = entry.path
        if (template.uriStyle == UriStyle.PATH && path == null) return null
        // Templates that embed {file.path} (without pathOrUri fallback) need a path.
        val needsPath = template.extras.values.any {
            it.contains("{file.path}") && !it.contains("{file.pathOrUri}")
        }
        if (needsPath && path.isNullOrBlank()) return null
        val pkg = template.component.substringBefore('/')
        val rawClass = template.component.substringAfter('/')
        val cls = if (rawClass.startsWith(".")) pkg + rawClass else rawClass
        val extras = template.extras.mapValues { (_, v) -> substitute(v, entry) }
            .filterNot { (k, v) ->
                v.isBlank() && k.equals("titleId", ignoreCase = true)
            }
        // Refuse empty boot/start extras (silent emulator no-ops).
        if (extras.values.any { it.isBlank() }) return null
        return LaunchPlan(
            action = template.action,
            packageName = pkg,
            className = cls,
            dataString = if (template.uriStyle == UriStyle.URI) entry.uri else null,
            extras = extras,
            grantRead = template.grantRead,
            flags = template.flags,
        )
    }

    private fun substitute(value: String, entry: RomEntry): String {
        val pathOrUri = entry.path?.takeIf { it.isNotBlank() } ?: entry.uri
        val titleId = VitaTitles.titleIdIn(entry.id.substringAfter(':'))
            ?: VitaTitles.titleIdIn(entry.path.orEmpty())
            ?: VitaTitles.titleIdIn(entry.name)
            ?: ""
        return value
            .replace("{file.titleId}", titleId)
            .replace("{file.name}", entry.name)
            .replace("{file.pathOrUri}", pathOrUri)
            .replace("{file.uri}", entry.uri)
            .replace("{file.path}", entry.path ?: "")
    }
}

/** Android-free session record from a successful ROM or app launch. */
object LaunchSession {
    fun forRom(
        key: String,
        template: PlayerTemplate,
        launchDisplayId: Int?,
        packageYield: Boolean = false,
        romOverride: SessionPolicy? =
            template.sessionPolicy.takeIf { it == SessionPolicy.YIELD_BOTH },
    ): SessionSurface = SessionSurface.forLaunch(
        key = key,
        playerId = template.id,
        packageName = template.component.substringBefore('/'),
        launchDisplayId = launchDisplayId,
        packageYield = packageYield,
        // Pack YIELD is an override; KEEP/omit stays null so forPlayerId still yields built-ins.
        romOverride = romOverride,
    )

    /**
     * Resolve stage plot (rom > pack > packageYield > player) and map launch face
     * to a topology display id. YIELD always uses [topologyLaunchId].
     */
    fun forRom(
        key: String,
        template: PlayerTemplate,
        entryId: String,
        stagePlots: Map<String, StagePlot>,
        packageYield: Map<String, Boolean>,
        interactiveId: Int?,
        companionId: Int?,
        topologyLaunchId: Int?,
    ): SessionSurface {
        val builtIn = SessionPolicy.forPlayerId(template.id)
        val romPlot = stagePlots[entryId]
        val packPlot = StagePlot(
            template.sessionPolicy.takeIf { it == SessionPolicy.YIELD_BOTH },
            template.launchFace,
        ).takeIf { it.policy != null || template.launchFace != LaunchFace.AUTO }
        val pkgYield = packageYield[template.component.substringBefore('/')] == true
        val plot = StagePlots.resolve(romPlot, packPlot, pkgYield, template.id)
        val launchId = StagePlots.launchDisplayId(
            plot.launchFace,
            plot.policy ?: builtIn,
            interactiveId = interactiveId,
            companionId = companionId,
            launchId = topologyLaunchId,
        )
        return forRom(
            key = key,
            template = template,
            launchDisplayId = launchId,
            packageYield = pkgYield,
            romOverride = plot.policy,
        )
    }

    fun forApp(
        key: String,
        launchDisplayId: Int?,
        packageYield: Boolean = false,
    ): SessionSurface =
        SessionSurface.forLaunch(
            key = key,
            playerId = null,
            packageName = key,
            launchDisplayId = launchDisplayId,
            packageYield = packageYield,
        )
}

/** Fires launch intents for ROM entries through the platform templates. */
object RomLauncher {

    /**
     * Launch [entry] on the non-interactive display. [playerId] forces a
     * specific registry player; otherwise [preferredPlayerId] (settings
     * default for the platform) is tried before the first installed player.
     * Returns the winning [PlayerTemplate], or null (with a Toast) when the
     * platform is unknown, the path is unavailable, or no suitable player
     * is installed.
     */
    fun launch(
        activity: Activity,
        state: DeckState,
        entry: RomEntry,
        playerId: String? = null,
        preferredPlayerId: String? = null,
        resolveLaunchDisplayId: (PlayerTemplate) -> Int? = { null },
    ): PlayerTemplate? {
        val platform = Platforms.byId(entry.platformId)
        if (platform == null) {
            toast(activity, R.string.rom_unknown_platform, entry.platformId)
            return null
        }
        val installed = { pkg: String -> isInstalled(activity, pkg) }
        val fileExists = { path: String -> java.io.File(path).isFile }
        val templates = if (playerId != null) {
            listOfNotNull(
                PlayerResolver.byId(platform, playerId)?.takeIf {
                    PlayerReadiness.isReady(it, installed, fileExists)
                },
            )
        } else {
            PlayerReadiness.readyPlayers(
                platform, preferredPlayerId, installed, fileExists,
            )
        }
        if (templates.isEmpty()) {
            val anyPkg = PlayerResolver.resolve(platform, preferredPlayerId, installed)
            if (anyPkg != null && PlayerReadiness.libretroCorePath(anyPkg) != null) {
                toast(activity, R.string.rom_core_missing, anyPkg.displayName)
            } else {
                toast(activity, R.string.rom_player_not_installed, platform.displayName)
            }
            return null
        }
        var lastBlock: PathGate.Decision.Blocked? = null
        var lastTemplate: PlayerTemplate? = null
        for (template in templates) {
            lastTemplate = template
            val plan = LaunchPlanBuilder.build(template, entry) ?: continue
            when (val gate = PathGate.decide(template.uriStyle, entry.path)) {
                is PathGate.Decision.Blocked -> {
                    lastBlock = gate
                    continue
                }
                PathGate.Decision.Ok -> {}
            }
            val intent = Intent()
                .setClassName(plan.packageName, plan.className)
                .apply {
                    plan.action?.let { action = it }
                    plan.dataString?.let { data = Uri.parse(it) }
                    plan.extras.forEach { (k, v) -> putExtra(k, v) }
                    addFlags(plan.flags)
                    if (plan.grantRead) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            return try {
                launchOnOtherDisplay(
                    activity,
                    state,
                    intent,
                    resolveLaunchDisplayId(template),
                )
                template
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        val blocked = lastBlock
        if (blocked != null) {
            val message = when (blocked.reason) {
                PathGate.BlockReason.PATH_UNAVAILABLE -> R.string.rom_path_unavailable
                PathGate.BlockReason.STORAGE_UNMOUNTED -> R.string.rom_storage_unmounted
                PathGate.BlockReason.FILE_UNREADABLE -> R.string.rom_file_unreadable
            }
            toast(activity, message)
        } else {
            toast(
                activity,
                R.string.rom_emulator_not_installed,
                lastTemplate?.displayName ?: platform.displayName,
            )
        }
        return null
    }

    private fun isInstalled(activity: Activity, packageName: String): Boolean =
        activity.packageManager.isInstalled(packageName)

    private fun toast(activity: Activity, messageRes: Int, vararg args: Any) {
        Toast.makeText(
            activity,
            activity.getString(messageRes, *args),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
