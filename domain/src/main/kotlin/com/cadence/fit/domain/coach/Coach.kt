package com.cadence.fit.domain.coach

/**
 * Which coach photo the interface should show, chosen by what the coach page is
 * doing rather than at random. See [CoachDirector.presence].
 */
enum class CoachExpression(val displayName: String) {
    IDLE("Idle"),
    SPEAKING("Speaking"),
    EXPLAINING("Explaining"),
    ENCOURAGING("Encouraging"),
}

/**
 * One slot in the approved-photo set.
 *
 * [drawableName] is the Android resource name the UI looks up at runtime, so a
 * photo dropped into `app/src/main/res/drawable-nodpi/` appears with no code
 * change. [approved] stays `false` until a brand-approved photo of the coach is
 * actually in the project: an empty slot is rendered as a clearly marked
 * placeholder, never filled with a stand-in or a generic stock photo.
 */
data class CoachPhotoSlot(
    val expression: CoachExpression,
    val drawableName: String,
    /** What the approved photo needs to show, in words. */
    val scene: String,
    val approved: Boolean = false,
)

/**
 * A statement a coach page may show.
 *
 * [verified] is the whole point: only facts supplied or approved by the app owner
 * may be presented as facts. Unverified entries are phrased as requests for copy
 * and are listed under "Awaiting approval" instead of being rendered as claims.
 */
data class CoachFact(
    val label: String,
    val text: String,
    val verified: Boolean,
    val source: String,
)

data class CoachProfile(
    val id: String,
    val name: String,
    val role: String,
    val facts: List<CoachFact>,
    val photos: List<CoachPhotoSlot>,
) {
    val confirmedFacts: List<CoachFact> get() = facts.filter { it.verified }

    val pendingFacts: List<CoachFact> get() = facts.filterNot { it.verified }

    /** True only when at least one real, approved photo is present. */
    val hasApprovedPhotos: Boolean get() = photos.any { it.approved }

    val approvedPhotos: List<CoachPhotoSlot> get() = photos.filter { it.approved }

    fun photoFor(expression: CoachExpression): CoachPhotoSlot =
        photos.firstOrNull { it.expression == expression } ?: photos.first()
}

object CoachRoster {

    /**
     * Shown on every coach page. Coach copy is Cadence's reading of the user's own
     * logged data; it is not the coach's words and it is not medical advice.
     */
    const val DISCLAIMER: String =
        "Coach pages are informational. The lines here are written by Cadence from " +
            "your own logged sets, not quotes or advice attributed to the coach, and " +
            "none of it is medical advice. Cadence does not assess body composition or " +
            "injuries."

    /** Explains why the portrait changes, so the switching never looks decorative. */
    const val PHOTO_NOTE: String =
        "The portrait follows what this page is doing: idle when there is nothing to " +
            "show, speaking when a suggestion is ready, explaining while you read a " +
            "topic, and encouraging while a session is in progress."

    fun slotName(coachId: String, expression: CoachExpression): String =
        "coach_${coachId.replace('-', '_')}_${expression.name.lowercase()}"

    private fun slots(coachId: String): List<CoachPhotoSlot> = listOf(
        CoachPhotoSlot(
            expression = CoachExpression.IDLE,
            drawableName = slotName(coachId, CoachExpression.IDLE),
            scene = "Relaxed mid-shot portrait, looking at the camera, calm expression",
        ),
        CoachPhotoSlot(
            expression = CoachExpression.SPEAKING,
            drawableName = slotName(coachId, CoachExpression.SPEAKING),
            scene = "Talking to camera mid-shot, mouth open, hands relaxed",
        ),
        CoachPhotoSlot(
            expression = CoachExpression.EXPLAINING,
            drawableName = slotName(coachId, CoachExpression.EXPLAINING),
            scene = "Explaining a movement, one hand raised to gesture, looking slightly off-camera",
        ),
        CoachPhotoSlot(
            expression = CoachExpression.ENCOURAGING,
            drawableName = slotName(coachId, CoachExpression.ENCOURAGING),
            scene = "Encouraging after a set, warm smile, looking at the camera",
        ),
    )

    /**
     * David Laid as Cadence's brand ambassador.
     *
     * Only two things appear as confirmed facts because only two things were
     * supplied: the name, and the ambassador role. Biography, credentials,
     * achievements, endorsements and any programme content are deliberately left
     * as pending requests rather than invented.
     */
    val davidLaid: CoachProfile = CoachProfile(
        id = "david-laid",
        name = "David Laid",
        role = "Brand ambassador",
        facts = listOf(
            CoachFact(
                label = "Name",
                text = "David Laid",
                verified = true,
                source = "Supplied when the Coaches section was requested",
            ),
            CoachFact(
                label = "Role",
                text = "Featured as Cadence's brand ambassador",
                verified = true,
                source = "Supplied when the Coaches section was requested",
            ),
            CoachFact(
                label = "Biography",
                text = "No approved biography supplied. Add one or two sentences of approved copy.",
                verified = false,
                source = "Awaiting approval",
            ),
            CoachFact(
                label = "Training background",
                text = "No verified background or credentials supplied. Add approved copy before " +
                    "this page describes experience or qualifications.",
                verified = false,
                source = "Awaiting approval",
            ),
            CoachFact(
                label = "Programmes",
                text = "No coach-authored programme supplied. Nothing is published under this " +
                    "name until approved content exists.",
                verified = false,
                source = "Awaiting approval",
            ),
            CoachFact(
                label = "Links",
                text = "No official link supplied. Add an approved profile or channel if it " +
                    "should appear here.",
                verified = false,
                source = "Awaiting approval",
            ),
        ),
        photos = slots("david-laid"),
    )

    val all: List<CoachProfile> = listOf(davidLaid)

    fun byId(coachId: String): CoachProfile? = all.firstOrNull { it.id == coachId }
}
