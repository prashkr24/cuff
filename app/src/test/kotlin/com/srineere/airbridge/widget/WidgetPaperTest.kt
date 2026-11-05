package com.srineere.airbridge.widget

import com.srineere.airbridge.health.Goals
import com.srineere.airbridge.health.HealthSnapshot
import com.srineere.airbridge.health.Night
import com.srineere.airbridge.health.Reading
import com.srineere.airbridge.health.TileColour
import com.srineere.airbridge.ui.HealthData
import com.srineere.airbridge.ui.HealthStage
import com.srineere.airbridge.ui.sampleGoals
import com.srineere.airbridge.ui.sampleHealthData
import com.srineere.airbridge.health.trendOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WidgetPaperTest {

    private fun night(day: Long, minutes: Long) = Night(
        day = day,
        start = Instant.EPOCH,
        end = Instant.EPOCH.plus(Duration.ofMinutes(minutes)),
        asleep = Duration.ofMinutes(minutes),
        awake = Duration.ZERO,
        light = Duration.ofMinutes(minutes),
        deep = Duration.ZERO,
        rem = Duration.ZERO,
    )

    /** A fortnight of 420-minute nights, so the usual band is tight around 7h. */
    private fun withLastNight(minutes: Long, steps: Long? = 4_000): HealthData {
        val nights = (0L until 13L).map { night(it, 420) } + night(13L, minutes)
        return HealthData(
            stage = HealthStage.Granted,
            snapshot = HealthSnapshot(stepsToday = steps, nights = nights),
            sleepTrend = trendOf(nights.map { Reading(it.day, it.asleep.toMinutes().toDouble()) }),
            nights = nights,
        )
    }

    private val goals = Goals(steps = 8_000, sleepMinutes = 420)

    // --------------------------------------------------------- the sentence

    @Test
    fun `a long night is the first thing it says`() {
        val sentence = paperSentence(withLastNight(480), goals)
        assertTrue(sentence, sentence.startsWith("You slept"))
        assertTrue(sentence, sentence.contains("longer"))
    }

    @Test
    fun `a short night is said the same way, the other direction`() {
        val sentence = paperSentence(withLastNight(300), goals)
        assertTrue(sentence, sentence.contains("less than your usual night"))
    }

    @Test
    fun `an ordinary night falls through to the steps`() {
        // 420 is the middle of a fortnight of 420s, so nothing is notable
        // about it and the sentence moves on rather than inventing something.
        val sentence = paperSentence(withLastNight(420, steps = 4_000), goals)
        assertEquals("4,000 steps to go.", sentence)
    }

    @Test
    fun `a goal already met is worth saying, since nothing else will`() {
        val sentence = paperSentence(withLastNight(420, steps = 9_100), goals)
        assertTrue(sentence, sentence.contains("passed your step goal"))
    }

    @Test
    fun `with nothing notable at all it says so rather than nothing`() {
        val bare = HealthData(stage = HealthStage.Granted)
        assertEquals("Everything is where it usually is.", paperSentence(bare, Goals(steps = 0)))
    }

    @Test
    fun `the sentence never suggests, never explains, never compares`() {
        // A sentence is the easiest thing here to make dishonest, so the
        // vocabulary is pinned: no cause, no advice, no other people.
        val banned = listOf(
            "should", "try", "because", "caused", "recommend", "average person",
            "most people", "aim", "better than", "worse than",
        )
        val sentences = listOf(480L, 300L, 420L).flatMap { minutes ->
            listOf(4_000L, 9_100L).map { steps ->
                paperSentence(withLastNight(minutes, steps), goals)
            }
        } + paperSentence(HealthData(stage = HealthStage.Granted), Goals(steps = 0))
        sentences.forEach { sentence ->
            banned.forEach { word ->
                assertTrue("\"$sentence\" contains \"$word\"", !sentence.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `every sentence is one sentence and ends like one`() {
        listOf(480L, 300L, 420L).forEach { minutes ->
            val sentence = paperSentence(withLastNight(minutes), goals)
            assertTrue(sentence, sentence.endsWith("."))
            assertEquals(sentence, 1, sentence.count { it == '.' })
        }
    }

    // --------------------------------------------------------- the readings

    @Test
    fun `fewer than four readings is fewer than four panes, never a dash`() {
        val readings = paperReadings(withLastNight(420, steps = null), goals)
        assertTrue(readings.none { it.figure == "–" })
        assertTrue(readings.none { it.label == "Steps" })
    }

    @Test
    fun `the four it does show are in the order a person reads them`() {
        val readings = paperReadings(sampleHealthData(), sampleGoals)
        assertEquals(listOf("Sleep", "Steps", "Resting", "Burn"), readings.map { it.label })
    }

    @Test
    fun `the ring beside the sentence measures the night against its goal`() {
        val (fraction, colour) = paperRing(withLastNight(210), goals)!!
        assertEquals(0.5f, fraction, 0.001f)
        assertEquals(TileColour.Sleep, colour)
    }

    // -------------------------------------------------------- the catalogue

    @Test
    fun `paper is in the catalogue and nothing is left unbuilt`() {
        PaperFace.entries.forEach { face ->
            val design = WidgetDesign.OfPaper(face)
            assertTrue(design in WidgetCatalogue.all)
            assertEquals(design, WidgetCatalogue.byId(design.id))
        }
        assertTrue(WidgetCatalogue.notBuilt.toString(), WidgetCatalogue.notBuilt.isEmpty())
    }

    @Test
    fun `every family on the canvas now has something behind it`() {
        assertEquals(
            WidgetFamily.entries.toSet(),
            WidgetCatalogue.all.map { it.family }.toSet(),
        )
    }

    @Test
    fun `the day face needs the room, so it asks for the biggest cell only`() {
        assertEquals(setOf(WidgetSize.Tall), WidgetDesign.OfPaper(PaperFace.Day).sizes)
    }
}
