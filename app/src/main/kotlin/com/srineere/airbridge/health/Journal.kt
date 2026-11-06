package com.srineere.airbridge.health

import android.content.Context
import kotlin.math.abs

/**
 * One thing that might have happened before a night.
 *
 * Fixed, and few. A journal you can add anything to is a journal where
 * nothing has five nights with it and five without, so nothing is ever
 * said — the whole value here is in asking the same short list often
 * enough for an answer to mean something.
 */
enum class JournalTag(val id: String, val label: String) {
    Caffeine("caffeine", "Caffeine after 16:00"),
    Alcohol("alcohol", "Alcohol"),
    LateFood("latefood", "Ate within 2h of bed"),
    Screen("screen", "Screen in bed"),
    Stress("stress", "Stressful day"),
    HardTraining("training", "Trained hard"),
    ;

    companion object {
        fun of(id: String): JournalTag? = entries.firstOrNull { it.id == id }
    }
}

/** What was answered for one day: yes, no, or not asked. */
enum class JournalAnswer { Yes, No }

/**
 * The tags, as this app keeps them.
 *
 * Health Connect has no record for "I had coffee at five", so this list is
 * CUFF's own and the screen says so. One key a day rather than one
 * key for everything: answering tonight rewrites tonight, and a file that
 * rewrites six months every time a pill is tapped is a file that will
 * eventually be half-written when something goes wrong.
 */
object Journal {
    private const val PREFS = "air_health"
    private const val PREFIX = "jday:"

    /** How far back the screen looks, and the furthest a finding can reach. */
    const val WINDOW_DAYS = 90

    /**
     * Nights with, and nights without, before anything is said.
     *
     * Five each. Below that a single bad night moves the mean by more than
     * most real effects do, and the finding would be a coin toss wearing a
     * sentence.
     */
    const val NEEDED = 5

    /** The furthest after a tag that it is still looked for. */
    const val MAX_LAG = 3

    /**
     * Six in the evening: the line between answering about tonight and
     * answering about last night.
     *
     * A judgement rather than a fact. Late enough that the day is done
     * being lived, early enough that somebody answering before dinner is
     * answering about the night ahead.
     */
    const val EVENING_HOUR = 18

    /**
     * Which night a tag answered now is about.
     *
     * The tags describe the hours before a night, so they belong to the
     * night that follows them: caffeine at five on Tuesday is about Tuesday
     * night, which Health Connect files under Wednesday. Answer in the
     * evening and it is tonight; answer in the morning and it is the night
     * that just ended -- the same key either way, which is the point.
     */
    fun nightFor(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Long =
        now.toLocalDate().toEpochDay() + if (now.hour >= EVENING_HOUR) 1 else 0

    fun read(context: Context, day: Long): Map<JournalTag, JournalAnswer> =
        decode(prefs(context).getString(PREFIX + day, null))

    fun answer(context: Context, day: Long, tag: JournalTag, answer: JournalAnswer?) {
        val now = read(context, day).toMutableMap()
        if (answer == null) now.remove(tag) else now[tag] = answer
        prefs(context).edit().apply {
            if (now.isEmpty()) remove(PREFIX + day) else putString(PREFIX + day, encode(now))
        }.apply()
    }

    /** Every day that has been answered, newest first. */
    fun history(context: Context): Map<Long, Map<JournalTag, JournalAnswer>> =
        prefs(context).all
            .filterKeys { it.startsWith(PREFIX) }
            .mapNotNull { (key, value) ->
                val day = key.removePrefix(PREFIX).toLongOrNull() ?: return@mapNotNull null
                val answers = decode(value as? String)
                if (answers.isEmpty()) null else day to answers
            }
            .toMap()

    /** Forgets one tag everywhere, which is what deleting it has to mean. */
    fun forget(context: Context, tag: JournalTag) {
        val editor = prefs(context).edit()
        history(context).forEach { (day, answers) ->
            if (tag !in answers) return@forEach
            val left = answers - tag
            if (left.isEmpty()) editor.remove(PREFIX + day)
            else editor.putString(PREFIX + day, encode(left))
        }
        editor.apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun encode(answers: Map<JournalTag, JournalAnswer>): String =
        answers.entries.joinToString(",") {
            it.key.id + "=" + if (it.value == JournalAnswer.Yes) "1" else "0"
        }

    /** Fail soft: a tag this version does not know costs that answer, not the day. */
    internal fun decode(stored: String?): Map<JournalTag, JournalAnswer> {
        if (stored.isNullOrBlank()) return emptyMap()
        return stored.split(",").mapNotNull { part ->
            val bits = part.split("=")
            if (bits.size != 2) return@mapNotNull null
            val tag = JournalTag.of(bits[0]) ?: return@mapNotNull null
            tag to if (bits[1] == "1") JournalAnswer.Yes else JournalAnswer.No
        }.toMap()
    }
}

/** A difference that has earned the right to be printed. */
data class JournalFinding(
    val tag: JournalTag,
    /** 0 for the same night, up to [Journal.MAX_LAG] nights after. */
    val lag: Int,
    val withMean: Double,
    val withoutMean: Double,
    val withCount: Int,
    val withoutCount: Int,
) {
    /** Negative when the nights after it scored worse. */
    val gap: Double get() = withMean - withoutMean
}

/** A tag that has not earned a finding yet, and how far off it is. */
data class JournalCounting(val tag: JournalTag, val yes: Int, val no: Int) {
    val short: Int get() = maxOf(Journal.NEEDED - yes, Journal.NEEDED - no, 0)
    val have: Int get() = minOf(yes, no)
}

/**
 * What the tags have and have not shown.
 *
 * Yes and no, never a blank, which is the rule the screen enforces and the
 * reason this takes a map of answers rather than a list of the yeses: an
 * unanswered tag and a "no" look identical in a correlation and mean
 * opposite things. That is how every journal that records only the yeses
 * ends up claiming caffeine helps.
 *
 * A finding has to clear three bars, not one. Five nights each way, so the
 * means are not built on a weekend. A gap wider than the night-to-night
 * swing of the scores themselves, so an ordinary wobble does not become a
 * sentence. And the lag that showed it is named, because "the same night"
 * and "three nights later" are different claims about the same tag.
 */
fun analyseJournal(
    answers: Map<Long, Map<JournalTag, JournalAnswer>>,
    scoreByDay: Map<Long, Int>,
): Pair<List<JournalFinding>, List<JournalCounting>> {
    // The swing of the scores themselves: half a standard deviation, the
    // same smallest-worthwhile-change every band on this tab is drawn from.
    val scores = scoreByDay.values.map { it.toDouble() }
    val swing = if (scores.size < 3) Double.MAX_VALUE else {
        val mean = scores.average()
        val variance = scores.sumOf { (it - mean) * (it - mean) } / scores.size
        kotlin.math.sqrt(variance) / 2
    }

    val findings = mutableListOf<JournalFinding>()
    val counting = mutableListOf<JournalCounting>()

    JournalTag.entries.forEach { tag ->
        val asked = answers.filterValues { tag in it }
        val yes = asked.count { it.value[tag] == JournalAnswer.Yes }
        val no = asked.size - yes
        if (yes < Journal.NEEDED || no < Journal.NEEDED) {
            if (asked.isNotEmpty()) counting += JournalCounting(tag, yes, no)
            return@forEach
        }

        // Every lag is scanned and the widest honest one wins, rather than
        // the first that happens to clear: a tag that shows on the same
        // night and faintly three nights later is one finding, not two.
        var best: JournalFinding? = null
        (0..Journal.MAX_LAG).forEach { lag ->
            val withIt = mutableListOf<Double>()
            val withoutIt = mutableListOf<Double>()
            asked.forEach { (day, row) ->
                val score = scoreByDay[day + lag]?.toDouble() ?: return@forEach
                if (row[tag] == JournalAnswer.Yes) withIt += score else withoutIt += score
            }
            if (withIt.size < Journal.NEEDED || withoutIt.size < Journal.NEEDED) return@forEach
            val candidate = JournalFinding(
                tag, lag, withIt.average(), withoutIt.average(), withIt.size, withoutIt.size,
            )
            if (abs(candidate.gap) <= swing) return@forEach
            if (best == null || abs(candidate.gap) > abs(best!!.gap)) best = candidate
        }
        if (best == null) counting += JournalCounting(tag, yes, no) else findings += best!!
    }
    // Widest first: the one most worth reading is the one at the top.
    return findings.sortedByDescending { abs(it.gap) } to counting.sortedByDescending { it.have }
}
