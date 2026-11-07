package ru.maximv.typovoice.history

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.maximv.typovoice.core.history.HistoryEntry
import ru.maximv.typovoice.core.history.HistoryRepository

class HistoryRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `entries persist newest first and survive reload`() = runTest {
        val repo = HistoryRepository(tmp.root)
        repo.add(HistoryEntry(source = "APP", text = "первый"))
        repo.add(HistoryEntry(source = "TILE", text = "второй"))
        assertEquals(listOf("второй", "первый"), repo.entries.value.map { it.text })

        val reloaded = HistoryRepository(tmp.root).apply { load() }
        assertEquals(listOf("второй", "первый"), reloaded.entries.value.map { it.text })
    }

    @Test
    fun `failed audio is kept, then removed on delete`() = runTest {
        val repo = HistoryRepository(tmp.root)
        val recording = tmp.newFile("rec.ogg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val path = repo.keepAudio(recording)!!
        assertFalse(recording.exists())
        val entry = HistoryEntry(source = "APP", error = "сеть", audioPath = path)
        repo.add(entry)
        assertTrue(repo.entries.value.single().canRetry)

        repo.delete(entry.id)
        assertTrue(repo.entries.value.isEmpty())
        assertFalse(java.io.File(path).exists())
    }

    @Test
    fun `replace updates a retried entry`() = runTest {
        val repo = HistoryRepository(tmp.root)
        val entry = HistoryEntry(source = "APP", error = "сеть")
        repo.add(entry)
        repo.replace(entry.copy(text = "готово", error = null))
        assertEquals("готово", repo.entries.value.single().text)
        assertFalse(repo.entries.value.single().failed)
    }
}
