package com.cadence.fit.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachDirectorTest {

    @Test
    fun `an empty account leaves the coach idle rather than inventing something to say`() {
        val presence = CoachDirector.presence(CoachContext())

        assertEquals(CoachExpression.IDLE, presence.expression)
        assertTrue(presence.headline.isNotBlank())
        // Only the always-available consistency topic survives on a blank account.
        val topics = CoachDirector.topics(CoachContext())
        assertEquals(listOf("consistency"), topics.map { it.id })
    }

    @Test
    fun `a ready suggestion makes the coach speak before the user opens anything`() {
        val presence = CoachDirector.presence(
            CoachContext(
                suggestionHeadline = "Add a little weight to Bench Press",
                suggestionExerciseId = "barbell-bench-press",
                suggestionExerciseName = "Bench Press",
            ),
        )

        assertEquals(CoachExpression.SPEAKING, presence.expression)
        assertEquals("Add a little weight to Bench Press", presence.headline)
        assertTrue(presence.detail.contains("Nothing changes until you accept"))
    }

    @Test
    fun `an in-progress session outranks a suggestion`() {
        val presence = CoachDirector.presence(
            CoachContext(
                hasActiveSession = true,
                workingSetsThisSession = 3,
                suggestionHeadline = "Add reps",
                suggestionExerciseId = "barbell-bench-press",
            ),
        )

        assertEquals(CoachExpression.ENCOURAGING, presence.expression)
        assertEquals("Mid-session", presence.headline)
        assertTrue(presence.detail.contains("3 working sets"))
    }

    @Test
    fun `an opened topic outranks an active session because the page is explaining`() {
        val presence = CoachDirector.presence(
            CoachContext(hasActiveSession = true, workingSetsThisSession = 5, topicOpen = true),
        )

        assertEquals(CoachExpression.EXPLAINING, presence.expression)
        assertTrue(presence.detail.contains("logged sets"))
    }

    @Test
    fun `work logged today encourages even when nothing else is pending`() {
        val presence = CoachDirector.presence(CoachContext(sessionsCompletedToday = 1))

        assertEquals(CoachExpression.ENCOURAGING, presence.expression)
        assertEquals("Work logged", presence.headline)
        assertTrue(presence.detail.contains("1 session"))
    }

    @Test
    fun `a best set this week is described as measured work, not a claim about the coach`() {
        val presence = CoachDirector.presence(CoachContext(recordsThisWeek = 2))

        assertEquals(CoachExpression.ENCOURAGING, presence.expression)
        assertTrue(presence.detail.contains("2 best sets"))
        assertFalse(presence.detail.contains("says"))
    }

    @Test
    fun `the portrait description names the coach and the state for screen readers`() {
        val presence = CoachDirector.presence(CoachContext(topicOpen = true))

        assertEquals("David Laid, explaining", presence.photoDescription)
    }

    @Test
    fun `suggestion and plateau topics carry their measured basis and destination`() {
        val topics = CoachDirector.topics(
            CoachContext(
                suggestionHeadline = "Add a little weight",
                suggestionExerciseId = "barbell-bench-press",
                suggestionExerciseName = "Bench Press",
                plateauHeadline = "Bench Press has not moved in 4 sessions",
            ),
        )

        val progression = topics.first { it.id == "progression" }
        assertEquals("Next step · Bench Press", progression.title)
        assertEquals(CoachTopicDestination.EXERCISE, progression.destination)
        assertEquals("barbell-bench-press", progression.exerciseId)
        assertTrue(progression.dataLine!!.contains("last logged session"))

        val plateau = topics.first { it.id == "plateau" }
        assertEquals(CoachTopicDestination.PROGRESS, plateau.destination)
        assertTrue(plateau.detail.contains("never"))
    }

    @Test
    fun `a suggestion without an exercise cannot link anywhere`() {
        val topics = CoachDirector.topics(CoachContext(suggestionHeadline = "Add a set"))

        val progression = topics.first { it.id == "progression" }
        assertEquals(CoachTopicDestination.NONE, progression.destination)
        assertNull(progression.exerciseId)
    }

    @Test
    fun `consistency reports the real weekly numbers`() {
        val topics = CoachDirector.topics(
            CoachContext(sessionsThisWeek = 2, targetSessionsPerWeek = 4),
        )

        val consistency = topics.first { it.id == "consistency" }
        assertEquals("2 of 4 sessions this week", consistency.summary)
        assertEquals("2 sessions logged this week", consistency.dataLine)
        assertEquals(CoachTopicDestination.PROGRESS, consistency.destination)
    }

    @Test
    fun `recovery only appears when nights are actually available`() {
        val without = CoachDirector.topics(CoachContext())
        assertTrue(without.none { it.id == "recovery" })

        val with = CoachDirector.topics(CoachContext(sleepNightsAvailable = 3))
        val recovery = with.first { it.id == "recovery" }
        assertEquals("3 recorded nights available", recovery.summary)
        assertEquals(CoachTopicDestination.SLEEP, recovery.destination)
        assertTrue(recovery.detail.contains("never changes a planned weight"))
    }

    @Test
    fun `only supplied facts are presented as confirmed`() {
        val coach = CoachRoster.davidLaid

        assertEquals(listOf("Name", "Role"), coach.confirmedFacts.map { it.label })
        assertEquals(
            listOf("Biography", "Training background", "Programmes", "Links"),
            coach.pendingFacts.map { it.label },
        )
        // Nothing pending is allowed to read as a claim about the coach.
        coach.pendingFacts.forEach { fact ->
            assertFalse("${fact.label} should be a request, not a claim", fact.verified)
            assertEquals("Awaiting approval", fact.source)
        }
        assertTrue(coach.pendingFacts.any { it.text.contains("No coach-authored programme") })
    }

    @Test
    fun `every expression has exactly one correctly named photo slot and none is approved yet`() {
        val coach = CoachRoster.davidLaid

        assertEquals(coach.photos.size, CoachExpression.entries.size)
        CoachExpression.entries.forEach { expression ->
            val matches = coach.photos.filter { it.expression == expression }
            assertEquals(1, matches.size)
            assertEquals("coach_david_laid_${expression.name.lowercase()}", matches.single().drawableName)
            assertFalse(matches.single().approved)
            assertTrue(matches.single().scene.isNotBlank())
        }
        assertEquals(coach.photos.size, coach.photos.map { it.drawableName }.distinct().size)
        assertFalse(coach.hasApprovedPhotos)
        assertTrue(coach.approvedPhotos.isEmpty())
    }

    @Test
    fun `photo lookup always resolves, even when a slot is missing`() {
        val onlyIdle = CoachRoster.davidLaid.copy(
            photos = listOf(CoachRoster.davidLaid.photoFor(CoachExpression.IDLE)),
        )

        assertEquals(CoachExpression.IDLE, onlyIdle.photoFor(CoachExpression.ENCOURAGING).expression)
    }

    @Test
    fun `the roster resolves the ambassador by id`() {
        assertEquals(CoachRoster.davidLaid, CoachRoster.byId("david-laid"))
        assertNull(CoachRoster.byId("someone-else"))
    }

    @Test
    fun `disclaimer keeps coaching copy separate from medical advice and from quotes`() {
        assertTrue(CoachRoster.DISCLAIMER.contains("not quotes"))
        assertTrue(CoachRoster.DISCLAIMER.contains("medical advice"))
        assertTrue(CoachRoster.PHOTO_NOTE.contains("idle"))
    }
}
