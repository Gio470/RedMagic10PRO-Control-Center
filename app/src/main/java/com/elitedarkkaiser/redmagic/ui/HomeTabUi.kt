package com.elitedarkkaiser.redmagic.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.elitedarkkaiser.redmagic.SystemStats
import com.elitedarkkaiser.redmagic.xposed.ModuleStatus

/**
 * Home: what the phone is doing right now, in the order you would ask.
 *
 * Whether the app can work at all ([CompatStrip], quiet unless it cannot), then the two readings
 * this phone is bought for -- heat and the fan -- as dials rather than as text, then the processor
 * and the memory with their recent history rather than only their current figure.
 *
 * The readings were small numbers in small cards of equal weight, which made the screen a list of
 * facts with nothing to look at first. A temperature is a position in a range before it is a
 * number, and a load is a shape over time before it is a percentage; drawing them that way is most
 * of what this screen is for.
 */
object HomeTabUi {

    data class Refs(
        /** Owns its own state; fed from the status poll and from this tab. */
        val compat: CompatStrip,
        /** Heat and fan. Hidden wholesale without root -- the nodes they read need it. */
        val vitals: LinearLayout,
        val tempGauge: ArcGauge,
        val fanGauge: ArcGauge,

        val processor: LinearLayout,
        val cpuUsageValue: TextView,
        val cpuSparkline: Sparkline,
        val cpuDetail: TextView,
        val cpuClusters: LinearLayout,

        val memory: LinearLayout,
        val ramValue: TextView,
        val ramBar: UsageBar,
        val ramDetail: TextView,
        val swapValue: TextView,
        val swapBar: UsageBar
    )

    data class Result(val view: LinearLayout, val refs: Refs)

    fun create(deps: HomeTabDeps): Result {
        val container = deps.scrollTabContainer()
        val context = container.context
        val m3 = M3(context)

        // ---- Can this app work here ----------------------------------------------------------
        val compat = CompatStrip(context, m3).apply {
            isClickable = true
            m3.springPress(this, pressedScale = 0.985f)
        }
        container.addView(compat)

        val isPro = deps.isRedMagic10Pro()
        compat.setDevice(isPro, if (isPro) "RedMagic 10 Pro" else deps.deviceCompatModelSummary())
        val rooted = deps.hasRootAccess()
        compat.setRoot(rooted, if (rooted) "Root access" else "No root")

        // Reads a file over root, so off the main thread. Read once here and again on a recheck
        // rather than on the status poll: it only changes across a reboot.
        fun refreshModule() {
            Thread {
                val summary = ModuleStatus.read()
                compat.post {
                    compat.setModule(
                        summary.healthy,
                        when {
                            summary.healthy -> "Xposed module"
                            !summary.loaded -> "Xposed module not loaded"
                            else -> "Xposed module has failed hooks"
                        }
                    )
                }
            }.start()
        }
        compat.setOnClickListener {
            deps.recheckDeviceCompatibility()
            refreshModule()
        }
        refreshModule()

        // ---- Heat and fan --------------------------------------------------------------------
        val tempGauge = ArcGauge(context).apply {
            set(0f, "--", "Temperature") { it.onSurfaceVariant }
        }
        val fanGauge = ArcGauge(context).apply {
            set(0f, "--", "RPM") { it.onSurfaceVariant }
        }

        val vitals = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = if (rooted && isPro) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = m3.dp(M3.Space.sm) }

            addView(
                gaugeCard(context, m3, "Temperature", tempGauge, M3.HomeTint.Tertiary) { deps.openTab("hardware") },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(View(context), LinearLayout.LayoutParams(m3.dp(M3.Space.sm), 1))
            addView(
                gaugeCard(context, m3, "Fan", fanGauge, M3.HomeTint.Secondary) { deps.openTab("hardware") },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        container.addView(vitals)

        // ---- Processor -----------------------------------------------------------------------
        val cpuUsageValue = TextView(context)
        val cpuSparkline = Sparkline(context)
        val cpuDetail = TextView(context)
        val cpuClusters = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val processor = card(context, m3, M3.HomeTint.Primary).apply {
            visibility = if (rooted) View.VISIBLE else View.GONE
            addView(headline(context, m3, "Processor", cpuUsageValue, M3.HomeTint.Primary))
            addView(cpuSparkline)
            addView(cpuClusters)
            addView(footnote(context, m3, cpuDetail))
        }
        container.addView(processor)

        // ---- Memory --------------------------------------------------------------------------
        val ramValue = TextView(context)
        val ramBar = UsageBar(context)
        val ramDetail = TextView(context)
        val swapValue = TextView(context)
        val swapBar = UsageBar(context) { it.tertiary }

        val memory = card(context, m3, M3.HomeTint.Neutral).apply {
            visibility = if (rooted) View.VISIBLE else View.GONE
            addView(headline(context, m3, "Memory", ramValue, M3.HomeTint.Neutral))
            addView(ramBar)
            // The percentage is the headline and the gigabytes are the detail: "51%" answers the
            // question, "8.2 GB of 16 GB" answers the follow-up.
            addView(ramDetail.apply {
                text = "--"
                m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
                setPadding(0, m3.dp(M3.Space.xs), 0, 0)
            })
            addView(meterRow(context, m3, "Swap", swapValue, swapBar))
        }
        container.addView(memory)

        return Result(
            view = container,
            refs = Refs(
                compat = compat,
                vitals = vitals,
                tempGauge = tempGauge,
                fanGauge = fanGauge,
                processor = processor,
                cpuUsageValue = cpuUsageValue,
                cpuSparkline = cpuSparkline,
                cpuDetail = cpuDetail,
                cpuClusters = cpuClusters,
                memory = memory,
                ramValue = ramValue,
                ramBar = ramBar,
                ramDetail = ramDetail,
                swapValue = swapValue,
                swapBar = swapBar
            )
        )
    }

    // ---- Live updates, kept here so the views and what writes to them stay in one file ---------

    /**
     * How hot, as a position between comfortable and worth worrying about.
     *
     * The range is Fahrenheit because that is what the hardware read returns; what the gauge prints
     * is whatever the user's unit setting formatted. 86F is a phone doing nothing and 122F is one
     * throttling, which puts the interesting part of the range across the middle of the dial rather
     * than bunched at one end.
     */
    fun applyTemperature(gauge: ArcGauge, tempF: Float?, display: String) {
        if (tempF == null) {
            gauge.set(0f, "--", "Temperature") { it.onSurfaceVariant }
            return
        }
        val fraction = ((tempF - TEMP_MIN_F) / (TEMP_MAX_F - TEMP_MIN_F)).coerceIn(0f, 1f)
        val caption = when {
            tempF >= TEMP_HOT_F -> "Hot"
            tempF >= TEMP_WARM_F -> "Warm"
            else -> "Cool"
        }
        gauge.set(fraction, display, caption) { m3 ->
            when {
                tempF >= TEMP_HOT_F -> m3.error
                tempF >= TEMP_WARM_F -> m3.tertiary
                else -> m3.primary
            }
        }
    }

    /**
     * The fan, as its level rather than its RPM.
     *
     * RPM is the number worth printing and the wrong one to draw an arc from: nothing on the phone
     * says what the maximum is, so a dial scaled to a guess would be a dial that lies. The level is
     * bounded by the node that sets it, so the arc is honest and the RPM sits in the middle of it.
     */
    fun applyFan(gauge: ArcGauge, level: Int?, rpm: Int?, enabled: Boolean) {
        if (!enabled || level == null) {
            gauge.set(0f, "--", "Off") { it.onSurfaceVariant }
            return
        }
        gauge.set(
            level.coerceIn(0, FAN_MAX_LEVEL) / FAN_MAX_LEVEL.toFloat(),
            rpm?.toString() ?: "--",
            "RPM"
        ) { it.primary }
    }

    /**
     * One cluster: how many cores, what they are clocked at, and how close that is to their
     * ceiling. The bar is what makes a row of frequencies readable -- "1.9 GHz" says nothing about
     * whether that is a core asleep or a core flat out without its maximum beside it, and even then
     * it is arithmetic the bar has already done.
     */
    fun clusterRow(context: Context, m3: M3, index: Int, cluster: SystemStats.CpuCluster): LinearLayout {
        val title = TextView(context).apply {
            // The kernel groups cores by their maximum clock, so cluster 0 is the fastest one --
            // "big"/"little" is the shape of that, not something the kernel actually labels.
            text = "Cluster $index · ${cluster.cores} core${if (cluster.cores == 1) "" else "s"}"
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurface)
        }
        val value = TextView(context).apply {
            m3.styleText(this, M3.Type.bodyMedium, m3.onSurfaceVariant)
        }
        val bar = UsageBar(context) { it.secondary }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, m3.dp(M3.Space.sm), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(title, LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
                addView(value)
            })
            addView(bar)
            updateClusterRow(this, cluster)
        }
    }

    /** Writes a cluster's live figures into a row [clusterRow] built. */
    fun updateClusterRow(row: LinearLayout, cluster: SystemStats.CpuCluster) {
        val header = row.getChildAt(0) as? LinearLayout ?: return
        (header.getChildAt(1) as? TextView)?.text =
            "${SystemStats.formatFreq(cluster.currentKHz)} / ${SystemStats.formatFreq(cluster.maxKHz)}"
        val percent = if (cluster.maxKHz > 0L) {
            (cluster.currentKHz * 100 / cluster.maxKHz).toInt()
        } else {
            0
        }
        (row.getChildAt(1) as? UsageBar)?.setPercent(percent)
    }

    // ---- Building blocks ----------------------------------------------------------------------

    /** Iconify's home card: a tinted container, 28dp corners, 20dp in, 16dp between cards. */
    private fun card(context: Context, m3: M3, tint: M3.HomeTint): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(m3.dp(20), m3.dp(20), m3.dp(20), m3.dp(20))
            background = m3.cardShape(m3.homeCardColors(tint).first, HOME_CARD_CORNER)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = m3.dp(M3.Space.md) }
        }

    /** A card's name on the left and its headline figure, large, on the right. */
    private fun headline(
        context: Context,
        m3: M3,
        title: String,
        valueView: TextView,
        tint: M3.HomeTint
    ): LinearLayout {
        val fg = m3.homeCardColors(tint).second
        valueView.text = "--"
        // The figure the card exists to show, in the card's own content colour.
        m3.styleText(valueView, M3.Type.titleLarge, fg)
        valueView.isSingleLine = true

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = title
                homeTitle(m3, this, fg)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(valueView)
        }
    }

    /** The card that holds one [ArcGauge]: its name above, the dial filling the rest. */
    private fun gaugeCard(
        context: Context,
        m3: M3,
        title: String,
        gauge: ArcGauge,
        tint: M3.HomeTint,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(context).apply {
        val (bg, fg) = m3.homeCardColors(tint)
        orientation = LinearLayout.VERTICAL
        setPadding(m3.dp(20), m3.dp(20), m3.dp(20), m3.dp(20))
        background = m3.filled(bg, HOME_CARD_CORNER, fg, card = true)
        addView(TextView(context).apply {
            text = title
            homeTitle(m3, this, fg)
        })
        addView(gauge, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, m3.dp(128)
        ).apply { topMargin = m3.dp(M3.Space.xs) })
        setOnClickListener { onClick() }
        // Iconify's home cards press further than its rows.
        m3.springPress(this, pressedScale = 0.92f)
    }

    /** The line of specifications under the Processor card's live figures. */
    private fun footnote(context: Context, m3: M3, valueView: TextView): TextView =
        valueView.apply {
            text = "--"
            m3.styleText(this, M3.Type.bodySmall, m3.onSurfaceVariant)
            setPadding(0, m3.dp(M3.Space.md), 0, 0)
        }

    /** A secondary reading inside a card: its name and figure on a row, its bar beneath. */
    private fun meterRow(
        context: Context,
        m3: M3,
        title: String,
        valueView: TextView,
        bar: UsageBar
    ): LinearLayout {
        if (valueView.text.isNullOrEmpty()) valueView.text = "--"
        m3.styleText(valueView, M3.Type.bodyMedium, m3.onSurfaceVariant)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, m3.dp(M3.Space.md), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = title
                    m3.styleText(this, M3.Type.titleMedium, m3.onSurface)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(valueView)
            })
            addView(bar)
        }
    }

    /** Iconify's home card title: titleLarge, ExtraBold, pulled in by half a point. */
    private fun homeTitle(m3: M3, view: TextView, color: Int) {
        m3.styleText(view, M3.Type.titleLarge, color)
        view.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, 800, false)
        view.letterSpacing = -0.5f / 22f
    }

    private const val HOME_CARD_CORNER = M3.Shape.extraLarge

    private const val TEMP_MIN_F = 86f
    private const val TEMP_MAX_F = 131f
    private const val TEMP_WARM_F = 104f
    private const val TEMP_HOT_F = 118f

    /** What the fan's level node accepts, which is what the Cooling slider spans. */
    private const val FAN_MAX_LEVEL = 5
}
