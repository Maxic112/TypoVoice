package ru.maximv.typovoice.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maximv.typovoice.core.update.UpdateManager.Companion.isNewer

class UpdateManagerTest {
    @Test
    fun `versions compare numerically`() {
        assertTrue(isNewer("0.2.0", "0.1.0"))
        assertTrue(isNewer("0.10.0", "0.9.3"))
        assertTrue(isNewer("1.0", "0.9.9"))
        assertTrue(isNewer("0.1.1", "0.1"))
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.1", "0.1.0"))
        assertFalse(isNewer("0.0.9", "0.1.0"))
        assertFalse(isNewer("0.1.0-beta", "0.1.0"))
    }
}
