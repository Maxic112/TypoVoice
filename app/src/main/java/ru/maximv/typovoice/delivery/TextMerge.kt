package ru.maximv.typovoice.delivery

/** User-controlled spacing around dictated text. */
data class Spacing(
    /** Add a space before the text when the character before the cursor is not whitespace. */
    val before: Boolean = true,
    /** Always end the inserted text with a space, ready for the next phrase. */
    val after: Boolean = false,
)

/** Computes what to insert so dictated text joins the surrounding text according to [Spacing]. */
object TextMerge {

    data class Result(val text: String, val cursor: Int)

    /** Full field content after inserting at the cursor (replacing a selection); used by SET_TEXT. */
    fun insert(existing: String, selectionStart: Int, selectionEnd: Int, inserted: String, spacing: Spacing): Result {
        val start = selectionStart.coerceIn(0, existing.length)
        val end = selectionEnd.coerceIn(start, existing.length)
        val before = existing.substring(0, start)
        val after = existing.substring(end)

        var piece = pastePiece(existing, start, inserted, spacing)
        // Don't double the space if the text after the cursor already starts with one.
        if (piece.endsWith(' ') && after.firstOrNull()?.isWhitespace() == true) piece = piece.dropLast(1)

        return Result(before + piece + after, before.length + piece.length)
    }

    /** Text to paste at [cursor]; the field itself keeps whatever is around it. */
    fun pastePiece(existing: String, cursor: Int, inserted: String, spacing: Spacing): String {
        var piece = inserted.trim()
        if (piece.isEmpty()) return piece
        val charBefore = existing.getOrNull(cursor.coerceIn(0, existing.length) - 1)
        if (spacing.before && charBefore != null && !charBefore.isWhitespace() && !isClosingPunctuation(piece.first())) {
            piece = " $piece"
        }
        if (spacing.after) piece = "$piece "
        return piece
    }

    private fun isClosingPunctuation(c: Char) = c in ".,!?;:)]}»…"
}
