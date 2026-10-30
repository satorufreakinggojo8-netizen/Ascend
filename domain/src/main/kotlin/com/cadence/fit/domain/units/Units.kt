package com.cadence.fit.domain.units

import kotlin.math.abs
import kotlin.math.roundToLong

const val KG_PER_LB: Double = 0.45359237
const val LB_PER_KG: Double = 1.0 / KG_PER_LB

/**
 * Weight is always *stored* in kilograms. This enum only controls conversion,
 * control increments and formatting, so switching units never rewrites history.
 */
enum class WeightUnit(val label: String, val abbreviation: String, val displayStep: Double) {
    KG("Kilograms", "kg", 0.25),
    LB("Pounds", "lb", 0.5),
    ;

    fun toKilograms(value: Double): Double = when (this) {
        KG -> value
        LB -> value * KG_PER_LB
    }

    fun fromKilograms(kilograms: Double): Double = when (this) {
        KG -> kilograms
        LB -> kilograms * LB_PER_KG
    }

    /** Coarse step for the large +/- controls while logging a set. */
    val increment: Double
        get() = when (this) {
            KG -> 2.5
            LB -> 5.0
        }

    /** Fine step for isolation work and small corrections. */
    val fineIncrement: Double
        get() = when (this) {
            KG -> 1.25
            LB -> 2.5
        }

    /** Snaps an amount expressed in this unit onto the display grid. */
    fun snap(value: Double): Double = (value / displayStep).roundToLong() * displayStep

    /** Rounds a stored kilogram value into a displayable amount in this unit. */
    fun display(kilograms: Double): Double = snap(fromKilograms(kilograms))

    companion object {
        val default: WeightUnit = KG

        fun fromTag(tag: String?): WeightUnit =
            entries.firstOrNull { it.name.equals(tag, ignoreCase = true) } ?: default
    }
}

/** Formatting helpers for weights and volumes. */
object Weights {

    fun format(kilograms: Double, unit: WeightUnit): String = formatAmount(unit.display(kilograms))

    fun formatWithUnit(kilograms: Double, unit: WeightUnit): String =
        "${format(kilograms, unit)} ${unit.abbreviation}"

    /**
     * Body weight is not a barbell load: it keeps tenths and is not snapped to the
     * plate grid, so a reading of 78.6 kg is not shown as 78.5 kg.
     */
    fun formatBodyWeight(kilograms: Double, unit: WeightUnit): String =
        "${formatAmount(unit.fromKilograms(kilograms))} ${unit.abbreviation}"

    /** Formats an amount that is already expressed in the user's unit. */
    fun formatAmount(amount: Double): String {
        if (!amount.isFinite()) return "--"
        val roundedToWhole = amount.roundToLong()
        if (abs(amount - roundedToWhole) < 0.005) return roundedToWhole.toString()
        val text = String.format(java.util.Locale.ROOT, "%.2f", amount)
        return text.trimEnd('0').trimEnd('.')
    }

    /** Compact volume display, e.g. `12,450 kg`. */
    fun formatVolume(kilograms: Double, unit: WeightUnit, withUnit: Boolean = true): String {
        val amount = unit.fromKilograms(kilograms).roundToLong()
        val text = groupDigits(amount)
        return if (withUnit) "$text ${unit.abbreviation}" else text
    }

    fun groupDigits(value: Long): String {
        val negative = value < 0
        val digits = abs(value).toString()
        val builder = StringBuilder()
        digits.forEachIndexed { index, character ->
            if (index > 0 && (digits.length - index) % 3 == 0) builder.append(',')
            builder.append(character)
        }
        return if (negative) "-$builder" else builder.toString()
    }

    /**
     * Converts an amount typed in one unit into the same physical load expressed
     * in another, snapped onto the target unit's display grid.
     *
     * Switching the unit mid-entry must not reinterpret the digits: 100 kg is
     * 220.5 lb, not 100 lb.
     */
    fun convertAmount(amount: Double, from: WeightUnit, to: WeightUnit): Double =
        if (from == to) amount else to.snap(to.fromKilograms(from.toKilograms(amount)))

    /** Parses free text from a weight field. Returns kilograms or null. */
    fun parseToKilograms(text: String, unit: WeightUnit): Double? {
        val raw = text.trim().replace(',', '.').removeSuffix(unit.abbreviation).trim()
        if (raw.isEmpty()) return null
        val value = raw.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0) return null
        return unit.toKilograms(value)
    }
}

/** Duration formatting shared by the rest timer, history and sleep views. */
object Durations {

    /** `1:05`, `12:30` – for countdowns and short efforts. */
    fun clock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val remainder = safe % 60
        return "$minutes:${remainder.toString().padStart(2, '0')}"
    }

    /** `7h 40m`, `45m` – for sleep and session lengths. */
    fun hoursMinutes(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val remainder = safe % 60
        return when {
            hours == 0 -> "${remainder}m"
            remainder == 0 -> "${hours}h"
            else -> "${hours}h ${remainder}m"
        }
    }

    fun shortDuration(minutes: Int): String = hoursMinutes(minutes)
}
