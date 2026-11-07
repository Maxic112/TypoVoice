package ru.maximv.typovoice.delivery

import org.junit.Assert.assertEquals
import org.junit.Test

class TextMergeTest {
    private val defaults = Spacing()
    private val none = Spacing(before = false, after = false)
    private val both = Spacing(before = true, after = true)

    private fun merge(text: String, start: Int, end: Int = start, inserted: String, spacing: Spacing = defaults) =
        TextMerge.insert(text, start, end, inserted, spacing)

    @Test
    fun `empty field gets trimmed text`() {
        assertEquals(TextMerge.Result("Привет", 6), merge("", 0, inserted = " Привет "))
    }

    @Test
    fun `space before after a word, not after whitespace or before punctuation`() {
        assertEquals(TextMerge.Result("Да. Привет", 10), merge("Да.", 3, inserted = "Привет"))
        assertEquals(TextMerge.Result("Да. Привет", 10), merge("Да. ", 4, inserted = "Привет"))
        assertEquals(TextMerge.Result("слово.", 6), merge("слово", 5, inserted = "."))
    }

    @Test
    fun `space before can be switched off`() {
        assertEquals(TextMerge.Result("Да.Привет", 9), merge("Да.", 3, inserted = "Привет", spacing = none))
    }

    @Test
    fun `space after is added only when enabled and not doubled`() {
        // "one" + " two " + " three" would double the space: the trailing one is dropped.
        assertEquals(TextMerge.Result("один два три", 8), merge("один три", 4, inserted = "два", spacing = both))
        assertEquals(TextMerge.Result("Привет ", 7), merge("", 0, inserted = "Привет", spacing = both))
        assertEquals(TextMerge.Result("Приветтекст", 6), merge("текст", 0, inserted = "Привет", spacing = none))
    }

    @Test
    fun `replaces selection`() {
        assertEquals(TextMerge.Result("один два", 8), merge("один XXX", 5, 8, inserted = "два"))
    }

    @Test
    fun `paste piece follows the switches`() {
        assertEquals("Привет", TextMerge.pastePiece("", 0, " Привет ", defaults))
        assertEquals(" два", TextMerge.pastePiece("один", 4, "два", defaults))
        assertEquals("два", TextMerge.pastePiece("один", 4, "два", none))
        assertEquals(" два ", TextMerge.pastePiece("один", 4, "два", both))
        assertEquals("два ", TextMerge.pastePiece("один ", 5, "два", both))
        assertEquals("", TextMerge.pastePiece("один", 4, "  ", both))
    }
}
