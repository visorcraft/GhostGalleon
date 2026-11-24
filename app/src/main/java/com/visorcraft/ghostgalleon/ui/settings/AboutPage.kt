package com.visorcraft.ghostgalleon.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.visorcraft.ghostgalleon.BuildConfig
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.DeckDialogs
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx
import com.visorcraft.ghostgalleon.ui.useTabularNumerals

/** Pure host-tested visual contrast and tab rules for [AboutPage]. */
internal object AboutPageLogic {

    const val LICENSE_TAB_COUNT = 4
    const val HERO_SHEEN_BASE_ALPHA = 0x24

    fun clampTabIndex(index: Int, count: Int = LICENSE_TAB_COUNT): Int =
        if (count <= 0) 0 else index.coerceIn(0, count - 1)

    fun heroSheenAlpha(accent: Int): Int =
        (ThemePack.accentGlowColor(accent, alpha = HERO_SHEEN_BASE_ALPHA) ushr 24) and 0xFF

    fun tabUnderlineColor(accent: Int): Int =
        SettingsUiLogic.accessibleOnAccent(accent)

    fun creditLicenseTextColor(accent: Int, tokens: ThemeTokens): Int =
        SettingsUiLogic.accentOnSunken(accent, tokens)

    /** 2 columns on landscape/tablet displays; single full-width stack on narrow panels. */
    fun featureColumnCount(screenWidthDp: Int): Int = if (screenWidthDp >= 500) 2 else 1

    /** Stack hero emblem above brand text on ultra-narrow viewports (< 400dp). */
    fun shouldStackHero(screenWidthDp: Int): Boolean = screenWidthDp < 400
}

/**
 * About page: hero card with the dynamic version, equal-height feature
 * cards, project link, and the Licenses / Credits dialogs backed by the
 * bundled raw texts (license_ghost_galleon, licenses_third_party,
 * acknowledgments, runtime_components). Self-contained - SettingsActivity
 * just adds [build]'s view to its ABOUT page body.
 */
object AboutPage {

    private const val PROJECT_URL = "https://github.com/visorcraft/GhostGalleon"

    private data class Credit(
        @StringRes val nameRes: Int,
        val version: String,
        @StringRes val licenseRes: Int,
    )

    // Direct bundled dependencies (transitives are all AndroidX Apache-2.0;
    // the full resolved list lives in raw/licenses_third_party.txt).
    private val bundledLibraries = listOf(
        Credit(R.string.credit_kotlin, "1.9.24", R.string.license_apache_2),
        Credit(R.string.credit_appcompat, "1.7.0", R.string.license_apache_2),
        Credit(R.string.credit_recyclerview, "1.3.2", R.string.license_apache_2),
        Credit(R.string.credit_documentfile, "1.0.1", R.string.license_apache_2),
        Credit(R.string.credit_coroutines, "1.6.4", R.string.license_apache_2),
        Credit(R.string.credit_annotations, "13.0", R.string.license_apache_2),
        Credit(R.string.credit_listenablefuture, "1.0", R.string.license_apache_2),
    )

    // System components / external services relied on at execution; none
    // are bundled (Grexa's "Runtime components" model).
    private val runtimeComponents = listOf(
        Credit(R.string.about_runtime_android_framework, "API 34", R.string.license_apache_2),
        Credit(R.string.about_runtime_art, "", R.string.license_apache_2),
        Credit(R.string.about_runtime_saf, "", R.string.license_apache_2),
        Credit(R.string.about_runtime_codecs, "", R.string.license_apache_2),
        Credit(R.string.about_runtime_sgdb, "", R.string.about_service_terms),
        Credit(R.string.about_runtime_emulators, "", R.string.about_various),
    )

    private fun resolveTokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) ThemePack.resolve(app.settings) else ThemePack.GHOST
    }

    private fun pill(context: Context, fill: Int, radiusDp: Int, stroke: Int = 0) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = context.dpF(radiusDp)
            if (stroke != 0) setStroke(context.strokePx(1), stroke)
        }

    private fun text(
        context: Context, value: String, sp: Float, color: Int, bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun header(context: Context, accentText: Int, value: String): TextView =
        text(
            context,
            value.uppercase(context.resources.configuration.locales[0]),
            13f,
            accentText,
            bold = true,
        ).apply {
            letterSpacing = 0.15f
        }

    private fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(context)
        val pad = context.dp(20)
        setPadding(pad, pad, pad, pad)
    }

    private fun chip(
        context: Context,
        value: String,
        sp: Float = 13f,
        textColor: Int,
        fill: Int,
        stroke: Int = 0,
        focusable: Boolean = false,
        focusAccent: Int = 0,
        tabular: Boolean = false,
    ): TextView = text(context, value, sp, textColor, bold = true).apply {
        if (tabular) useTabularNumerals()
        gravity = Gravity.CENTER
        val idle = pill(context, fill, 14, stroke)
        background = if (focusable && focusAccent != 0) {
            TileBackgrounds.pressableRow(
                context = context,
                accent = focusAccent,
                idleBackground = idle,
                cornerRadiusDp = 14,
                strokeDp = 2,
            )
        } else {
            idle
        }
        isFocusable = focusable
        if (focusable) {
            minHeight = context.dp(UiDimens.TOUCH_MIN_DP)
            setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
        } else {
            setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
        }
    }

    private fun haloEmblem(
        context: Context,
        accent: Int,
        emblemSizeDp: Int,
        haloSizeDp: Int,
        peakAlpha: Int,
        bindIcon: ImageView.() -> Unit,
    ): FrameLayout = FrameLayout(context).apply {
        background = TileBackgrounds.accentGlow(
            context = context,
            accent = accent,
            cornerRadiusDp = haloSizeDp / 2,
            radial = true,
            peakAlpha = peakAlpha,
        )
        addView(
            ImageView(context).apply(bindIcon),
            FrameLayout.LayoutParams(
                context.dp(emblemSizeDp),
                context.dp(emblemSizeDp),
                Gravity.CENTER,
            ),
        )
    }

    /** Full About page body (sections stacked vertically). */
    fun build(context: Context, accent: Int): View {
        val tokens = resolveTokens(context)
        val cardSurface = ThemePack.cardFill(tokens)
        val sunkenSurface = ThemePack.surfaceSunken(tokens)
        val hairline = ThemePack.hairlineDivider(tokens)
        val accentOnBlack = ThemePack.accentTextColor(accent, 0xFF000000.toInt())
        val accentOnCard = ThemePack.accentTextColor(accent, cardSurface)
        val accentOnSunken = ThemePack.accentTextColor(accent, sunkenSurface)
        val onAccent = SettingsUiLogic.accessibleOnAccent(accent)
        val activeFocusRing = SettingsUiLogic.activePillFocusRing(accent)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addSection(title: String?, sectionCard: View, topMarginDp: Int = 0) {
            if (title != null) {
                root.addView(header(context, accentOnBlack, title), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = context.dp(24)
                    bottomMargin = context.dp(10)
                })
            }
            root.addView(sectionCard, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (topMarginDp > 0) topMargin = context.dp(topMarginDp)
            })
        }

        // Hero card: subtle accent sheen, radial glow emblem, name, tagline, tabular version/license/API pills.
        val hero = card(context).apply {
            background = LayerDrawable(
                arrayOf(
                    TileBackgrounds.card(context),
                    TileBackgrounds.accentGlow(
                        context = context,
                        accent = accent,
                        cornerRadiusDp = tokens.cardRadiusDp,
                        radial = false,
                        peakAlpha = AboutPageLogic.HERO_SHEEN_BASE_ALPHA,
                    ),
                ),
            )
        }
        val dm = context.resources.displayMetrics
        val screenWidthDp = (dm.widthPixels / dm.density).toInt()
        val stackHero = AboutPageLogic.shouldStackHero(screenWidthDp)

        val heroRow = LinearLayout(context).apply {
            orientation = if (stackHero) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (stackHero) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        heroRow.addView(
            haloEmblem(
                context = context,
                accent = accent,
                emblemSizeDp = 92,
                haloSizeDp = 108,
                peakAlpha = 0x48,
            ) {
                setImageResource(R.mipmap.ic_launcher)
            },
            LinearLayout.LayoutParams(context.dp(108), context.dp(108)),
        )
        val heroText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (stackHero) {
                setPaddingRelative(0, context.dp(12), 0, 0)
            } else {
                setPaddingRelative(context.dp(16), 0, 0, 0)
            }
        }
        heroText.addView(text(
            context,
            context.getString(R.string.app_name),
            26f,
            UiDimens.ALPHA_PRIMARY,
            bold = true,
        ))
        heroText.addView(text(
            context,
            context.getString(R.string.about_subtitle),
            14f,
            accentOnCard,
        ).apply {
            setPadding(0, context.dp(4), 0, 0)
        })
        heroText.addView(text(
            context,
            context.getString(R.string.about_description),
            13f,
            UiDimens.ALPHA_SECONDARY,
        ).apply {
            setPadding(0, context.dp(4), 0, context.dp(10))
        })
        val pillsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val versionChip = chip(
            context = context,
            value = context.getString(R.string.about_version, BuildConfig.VERSION_NAME),
            textColor = accentOnSunken,
            fill = sunkenSurface,
            stroke = ThemePack.accentHairline(accent),
            tabular = true,
        )
        pillsRow.addView(versionChip)
        pillsRow.addView(
            chip(
                context = context,
                value = context.getString(R.string.license_gpl_v3),
                textColor = UiDimens.ALPHA_PRIMARY,
                fill = sunkenSurface,
                stroke = hairline,
                tabular = true,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(UiDimens.SPACE_SM) },
        )
        pillsRow.addView(
            chip(
                context = context,
                value = context.getString(R.string.about_android_api, 34),
                textColor = UiDimens.ALPHA_PRIMARY,
                fill = sunkenSurface,
                stroke = hairline,
                tabular = true,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(UiDimens.SPACE_SM) },
        )
        val pillsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(12))
            addView(pillsRow)
        }
        heroText.addView(
            pillsScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        heroText.addView(
            text(
                context,
                BuildConfig.GIT_SHA,
                11.5f,
                UiDimens.ALPHA_MUTED,
            ).apply {
                typeface = Typeface.MONOSPACE
                useTabularNumerals()
                background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 8)
                setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(UiDimens.SPACE_SM) },
        )
        heroRow.addView(
            heroText,
            if (stackHero) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            } else {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        hero.addView(heroRow)
        addSection(null, hero, topMarginDp = 24)

        // What's inside: adaptive 2-column (wide) or 1-column (narrow) equal-height feature cards.
        data class Feature(@StringRes val titleRes: Int, @StringRes val bodyRes: Int)
        val features = listOf(
            Feature(R.string.about_grid_mode, R.string.about_grid_mode_body),
            Feature(R.string.about_game_mode, R.string.about_game_mode_body),
            Feature(R.string.about_library, R.string.about_library_body),
            Feature(R.string.about_dual_screen, R.string.about_dual_screen_body),
        )
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val featureCols = AboutPageLogic.featureColumnCount(screenWidthDp)
        features.chunked(featureCols).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            pair.forEachIndexed { colIndex, feature ->
                val cell = card(context)
                val titleRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                titleRow.addView(
                    View(context).apply {
                        background = TileBackgrounds.activeAccentBar(context, accent, cornerRadiusDp = 2)
                    },
                    LinearLayout.LayoutParams(context.dp(3), context.dp(16)).apply {
                        marginEnd = context.dp(8)
                    },
                )
                titleRow.addView(
                    text(
                        context,
                        context.getString(feature.titleRes),
                        15f,
                        UiDimens.ALPHA_PRIMARY,
                        bold = true,
                    ),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                cell.addView(titleRow)
                cell.addView(text(
                    context,
                    context.getString(feature.bodyRes),
                    13f,
                    UiDimens.ALPHA_MUTED,
                ).apply {
                    setLineSpacing(context.dpF(2), 1f)
                    setPadding(0, context.dp(8), 0, 0)
                })
                row.addView(cell, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
                ).apply {
                    if (colIndex > 0) marginStart = context.dp(UiDimens.SPACE_MD)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (rowIndex > 0) topMargin = context.dp(UiDimens.SPACE_MD)
            })
        }
        val featuresCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(grid)
        }
        addSection(context.getString(R.string.about_whats_inside), featuresCard)

        // Project card: glow-framed brand ship + repo link + pressable Visit pill.
        val project = card(context)
        val projectRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        projectRow.addView(
            haloEmblem(
                context = context,
                accent = accent,
                emblemSizeDp = 52,
                haloSizeDp = 66,
                peakAlpha = 0x42,
            ) {
                setImageResource(R.drawable.ic_brand_ship)
            },
            LinearLayout.LayoutParams(context.dp(66), context.dp(66)),
        )
        val projectText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(context.dp(14), 0, context.dp(UiDimens.SPACE_LG), 0)
        }
        projectText.addView(text(
            context,
            context.getString(R.string.about_project_body),
            15f,
            UiDimens.ALPHA_PRIMARY,
            bold = true,
        ))
        projectText.addView(text(
            context,
            context.getString(R.string.project_url_label),
            13f,
            accentOnCard,
        ).apply {
            setPadding(0, context.dp(UiDimens.SPACE_XS), 0, 0)
        })
        projectRow.addView(projectText, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val visitBtn = chip(
            context = context,
            value = context.getString(R.string.about_visit),
            textColor = onAccent,
            fill = accent,
            focusable = true,
            focusAccent = activeFocusRing,
        ).apply {
            id = View.generateViewId()
            setOnClickListener {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
            }
        }
        projectRow.addView(visitBtn)
        project.addView(projectRow)
        addSection(null, project, topMarginDp = 16)

        // Licenses & Credits card: gamepad-focusable entry points to the two dialogs.
        val legal = card(context)
        legal.addView(text(
            context,
            context.getString(R.string.about_legal_title),
            17f,
            UiDimens.ALPHA_PRIMARY,
            bold = true,
        ))
        legal.addView(text(
            context,
            context.getString(R.string.about_legal_body),
            13f,
            UiDimens.ALPHA_MUTED,
        ).apply {
            setLineSpacing(context.dpF(2), 1f)
            setPadding(0, context.dp(6), 0, context.dp(UiDimens.SPACE_MD))
        })
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val licensesBtn = chip(
            context = context,
            value = context.getString(R.string.about_licenses),
            textColor = accentOnSunken,
            fill = sunkenSurface,
            stroke = hairline,
            focusable = true,
            focusAccent = accent,
        ).apply {
            id = View.generateViewId()
            setOnClickListener { showLicensesDialog(context, accent) }
        }
        val creditsBtn = chip(
            context = context,
            value = context.getString(R.string.about_credits),
            textColor = accentOnSunken,
            fill = sunkenSurface,
            stroke = hairline,
            focusable = true,
            focusAccent = accent,
        ).apply {
            id = View.generateViewId()
            setOnClickListener { showCreditsDialog(context, accent) }
        }
        visitBtn.nextFocusDownId = licensesBtn.id
        licensesBtn.nextFocusUpId = visitBtn.id
        creditsBtn.nextFocusUpId = visitBtn.id
        licensesBtn.nextFocusRightId = creditsBtn.id
        creditsBtn.nextFocusLeftId = licensesBtn.id
        buttons.addView(licensesBtn)
        buttons.addView(
            creditsBtn,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dp(UiDimens.SPACE_MD) },
        )
        legal.addView(buttons)
        addSection(null, legal, topMarginDp = 16)

        // Footer.
        root.addView(text(
            context,
            context.getString(R.string.about_built_with),
            12f,
            UiDimens.ALPHA_SUBTLE,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, context.dp(24), 0, context.dp(UiDimens.SPACE_SM))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        return root
    }

    private fun loadRaw(context: Context, resId: Int): String = runCatching {
        context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }.getOrDefault(context.getString(R.string.about_text_unavailable))

    /** Licenses dialog: four gamepad-focusable tabs over the bundled texts, plus Copy. */
    fun showLicensesDialog(context: Context, accent: Int) {
        val tokens = resolveTokens(context)
        val idleFill = tokens.chipIdle
        val hairline = ThemePack.hairlineDivider(tokens)
        val onAccent = SettingsUiLogic.accessibleOnAccent(accent)
        val activeFocusRing = SettingsUiLogic.activePillFocusRing(accent)
        val underlineColor = AboutPageLogic.tabUnderlineColor(accent)

        val tabs = listOf(
            R.string.about_ghost_license to R.raw.license_ghost_galleon,
            R.string.about_third_party to R.raw.licenses_third_party,
            R.string.about_acknowledgments to R.raw.acknowledgments,
            R.string.about_runtime_components to R.raw.runtime_components,
        )
        var selected = 0

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(20)
            setPadding(pad, context.dp(UiDimens.SPACE_SM), pad, 0)
        }

        data class TabWidgets(val root: LinearLayout, val label: TextView, val underline: View)
        val tabWidgets = mutableListOf<TabWidgets>()
        val tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 16)
            setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        }
        val body = text(context, "", 12f, 0xE6FFFFFF.toInt()).apply {
            typeface = Typeface.MONOSPACE
            setLineSpacing(context.dpF(2), 1f)
            setTextIsSelectable(true)
            setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        }
        val bodyScroll = OverlayPrimitives.MaxHeightScrollView(
            context = context,
            maxHeightPx = OverlayPrimitives.maxModalHeight(
                context = context,
                heightFrac = 0.50f,
                minDp = 160,
            ),
        ).apply {
            background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 12)
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(14))
            addView(body)
        }

        fun restyle() {
            selected = AboutPageLogic.clampTabIndex(selected, tabs.size)
            tabs.forEachIndexed { index, _ ->
                val widgets = tabWidgets[index]
                val active = index == selected
                widgets.root.isSelected = active
                widgets.underline.visibility = if (active) View.VISIBLE else View.INVISIBLE
                if (active) {
                    widgets.root.background = TileBackgrounds.pressableRow(
                        context = context,
                        accent = activeFocusRing,
                        idleBackground = pill(context, accent, 12),
                        cornerRadiusDp = 12,
                        strokeDp = 2,
                    )
                    widgets.label.setTextColor(onAccent)
                } else {
                    widgets.root.background = TileBackgrounds.pressableRow(
                        context = context,
                        accent = accent,
                        idleBackground = pill(context, idleFill, 12, hairline),
                        cornerRadiusDp = 12,
                        strokeDp = 2,
                    )
                    widgets.label.setTextColor(UiDimens.ALPHA_PRIMARY)
                }
            }
            body.text = loadRaw(context, tabs[selected].second)
            bodyScroll.scrollTo(0, 0)
        }

        tabs.forEachIndexed { index, (labelRes, _) ->
            val labelView = text(
                context,
                context.getString(labelRes),
                12f,
                UiDimens.ALPHA_PRIMARY,
                bold = true,
            ).apply {
                maxLines = 1
                gravity = Gravity.CENTER
            }
            val underline = View(context).apply {
                background = TileBackgrounds.activeAccentBar(context, underlineColor, cornerRadiusDp = 2)
                visibility = View.INVISIBLE
            }
            val tabRoot = LinearLayout(context).apply {
                id = View.generateViewId()
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                minimumHeight = context.dp(38)
                setPadding(
                    context.dp(UiDimens.SPACE_MD),
                    context.dp(6),
                    context.dp(UiDimens.SPACE_MD),
                    context.dp(5),
                )
                addView(labelView)
                addView(
                    underline,
                    LinearLayout.LayoutParams(context.dp(22), context.dp(2.5f)).apply {
                        topMargin = context.dp(3)
                    },
                )
                setOnClickListener {
                    if (selected != index) {
                        selected = index
                        restyle()
                    }
                }
            }
            tabWidgets.add(TabWidgets(tabRoot, labelView, underline))
            tabRow.addView(tabRoot, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { if (index < tabs.lastIndex) marginEnd = context.dp(6) })
        }
        tabWidgets.forEachIndexed { index, widgets ->
            if (index > 0) widgets.root.nextFocusLeftId = tabWidgets[index - 1].root.id
            if (index < tabWidgets.lastIndex) widgets.root.nextFocusRightId = tabWidgets[index + 1].root.id
        }
        // Tabs can overflow the dialog width on narrow panels; scroll them.
        content.addView(
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                isHorizontalFadingEdgeEnabled = true
                setFadingEdgeLength(context.dp(12))
                addView(tabRow)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = context.dp(UiDimens.SPACE_MD) },
        )
        content.addView(
            bodyScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        restyle()

        DeckDialogs.customView(
            context = context,
            titleRes = R.string.about_licenses,
            view = content,
            positiveRes = R.string.action_copy,
            onPositive = {
                val clipboard = context.getSystemService(
                    Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(
                    context.getString(tabs[selected].first),
                    loadRaw(context, tabs[selected].second),
                ))
                Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
            },
            negativeRes = R.string.action_close,
        )
    }

    /** Credits dialog: runtime components + bundled library table. */
    fun showCreditsDialog(context: Context, accent: Int) {
        val tokens = resolveTokens(context)
        val licenseTextColor = AboutPageLogic.creditLicenseTextColor(accent, tokens)
        val hairline = ThemePack.hairlineDivider(tokens)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(20)
            setPadding(pad, context.dp(UiDimens.SPACE_SM), pad, context.dp(UiDimens.SPACE_SM))
        }

        fun addCreditGroup(items: List<Credit>) {
            items.forEachIndexed { index, credit ->
                if (index > 0) {
                    list.addView(
                        View(context).apply { setBackgroundColor(hairline) },
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            context.strokePx(1),
                        ),
                    )
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, context.dp(10), 0, context.dp(10))
                }
                val left = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                left.addView(text(
                    context,
                    context.getString(credit.nameRes),
                    14f,
                    UiDimens.ALPHA_PRIMARY,
                    bold = true,
                ))
                if (credit.version.isNotEmpty()) {
                    left.addView(text(context, credit.version, 12f, UiDimens.ALPHA_MUTED).apply {
                        useTabularNumerals()
                    })
                }
                row.addView(left, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = context.dp(UiDimens.SPACE_SM)
                })
                row.addView(text(
                    context,
                    context.getString(credit.licenseRes),
                    11.5f,
                    licenseTextColor,
                    bold = true,
                ).apply {
                    background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 10)
                    setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
                })
                list.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }
        }

        list.addView(text(
            context,
            context.getString(R.string.about_runtime_components),
            15f,
            UiDimens.ALPHA_PRIMARY,
            bold = true,
        ))
        list.addView(text(
            context,
            context.getString(R.string.about_runtime_components_body),
            12f,
            UiDimens.ALPHA_MUTED,
        ).apply {
            setPadding(0, context.dp(UiDimens.SPACE_XS), 0, context.dp(UiDimens.SPACE_SM))
        })
        addCreditGroup(runtimeComponents)

        list.addView(text(
            context,
            context.getString(R.string.about_bundled_libraries),
            15f,
            UiDimens.ALPHA_PRIMARY,
            bold = true,
        ).apply {
            setPadding(0, context.dp(14), 0, 0)
        })
        list.addView(text(
            context,
            context.getString(R.string.about_bundled_libraries_body),
            12f,
            UiDimens.ALPHA_MUTED,
        ).apply {
            setPadding(0, context.dp(UiDimens.SPACE_XS), 0, context.dp(UiDimens.SPACE_SM))
        })
        addCreditGroup(bundledLibraries)

        val scroll = OverlayPrimitives.MaxHeightScrollView(
            context = context,
            maxHeightPx = OverlayPrimitives.maxModalHeight(
                context = context,
                heightFrac = 0.58f,
                minDp = 160,
            ),
        ).apply {
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(context.dp(14))
            addView(list)
        }
        DeckDialogs.customView(
            context = context,
            titleRes = R.string.about_credits,
            view = scroll,
            negativeRes = R.string.action_close,
        )
    }
}
