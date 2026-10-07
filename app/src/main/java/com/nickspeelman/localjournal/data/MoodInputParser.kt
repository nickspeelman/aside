package com.nickspeelman.localjournal.data


/**
 * Converts the compact check-in format used by notification replies into a MoodEntry.
 * In-app entry UI collects rating separately and uses [parseContent].
 *
 * Example: 4 at the beach #friends
 *
 * A free-text/malformed response that does not begin with a rating is intentionally preserved as
 * an unrated entry. It remains available in History but is excluded from mood calculations.
 */
object MoodInputParser {
    const val INPUT_LABEL = "1-5 Note #Tags, e.g. '4 at the beach #friends'"

    fun parse(input: String): MoodEntry {
        val trimmed = input.trim()
        val parts = if (trimmed.isEmpty()) emptyList() else trimmed.split(Regex("\\s+"))
        val firstPart = parts.firstOrNull().orEmpty()
        val numericRating = firstPart.toIntOrNull()

        // Notification choice UIs can place the cursor directly after the selected rating.
        // Accept "4great day" and "4#home" as a defensive fallback while preserving the
        // established behavior for fully numeric values such as 15 or -5.
        val compactRating = firstPart
            .takeIf {
                it.length > 1 &&
                    it.first() in '1'..'5' &&
                    !it[1].isDigit()
            }
            ?.first()
            ?.digitToInt()

        val rating = when {
            numericRating != null -> numericRating.coerceIn(1, 5)
            compactRating != null -> compactRating
            else -> null
        }

        val content = when {
            compactRating != null -> buildString {
                append(firstPart.drop(1))
                if (parts.size > 1) {
                    if (isNotEmpty()) append(' ')
                    append(parts.drop(1).joinToString(" "))
                }
            }
            numericRating != null -> parts.drop(1).joinToString(" ")
            else -> trimmed // No rating: preserve the entire response as content.
        }

        return parseContent(rating, content)
    }

    /**
     * Builds an entry when the rating is collected separately from the note field. This is the
     * preferred path for in-app entry UI; the compact parser above remains for notification replies
     * and backward compatibility.
     */
    fun parseContent(rating: Int?, content: String): MoodEntry {
        val preservedContent = content.trim()
        val hashtags = HashtagUtils.extractFromContent(preservedContent).joinToString(",")

        return MoodEntry(
            rating = rating?.coerceIn(1, 5),
            // Keep hashtags in the note exactly where the user wrote them. The separate hashtags
            // column is an index for filtering/analytics, not a second user-facing text field.
            note = preservedContent,
            hashtags = hashtags
        )
    }

}
