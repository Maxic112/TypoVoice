package ru.maximv.typovoice.servers

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import ru.maximv.typovoice.core.api.SttApiException
import ru.maximv.typovoice.core.servers.AllServersFailedException
import ru.maximv.typovoice.core.servers.PoolState
import ru.maximv.typovoice.core.servers.SelectionPolicy
import ru.maximv.typovoice.core.servers.ServerConfig
import ru.maximv.typovoice.core.servers.ServerPool
import java.io.IOException

class ServerPoolTest {
    private val a = ServerConfig("a", "A", "http://a", "k")
    private val b = ServerConfig("b", "B", "http://b", "k")
    private val c = ServerConfig("c", "C", "http://c", "k")
    private val disabled = ServerConfig("d", "D", "http://d", "k", enabled = false)
    private val servers = listOf(a, b, disabled, c)
    private val pool = ServerPool()

    private fun ids(policy: SelectionPolicy, state: PoolState = PoolState(), fixed: String? = null) =
        pool.order(servers, policy, fixed, state).map { it.id }

    @Test
    fun `ordering per policy skips disabled servers`() {
        assertEquals(listOf("a", "b", "c"), ids(SelectionPolicy.FAILOVER))
        assertEquals(listOf("b"), ids(SelectionPolicy.FIXED, fixed = "b"))
        assertEquals(listOf("a"), ids(SelectionPolicy.FIXED, fixed = "missing"))
        assertEquals(listOf("c", "a", "b"), ids(SelectionPolicy.LAST_SUCCESSFUL, PoolState(lastSuccessfulId = "c")))
        assertEquals(listOf("b", "c", "a"), ids(SelectionPolicy.ROUND_ROBIN, PoolState(roundRobinCursor = 1)))
        assertEquals(setOf("a", "b", "c"), ids(SelectionPolicy.RANDOM).toSet())
    }

    @Test
    fun `fails over on network errors and remembers the winner`() = runTest {
        val tried = mutableListOf<String>()
        val (result, state) = pool.execute(servers, SelectionPolicy.ROUND_ROBIN, null, PoolState()) {
            tried += it.id
            if (it.id == "a") throw SttApiException.Network(IOException("down"))
            "ok-${it.id}"
        }
        assertEquals("ok-b", result)
        assertEquals(listOf("a", "b"), tried)
        assertEquals(PoolState(lastSuccessfulId = "b", roundRobinCursor = 2), state)
    }

    @Test
    fun `stops on errors another server cannot fix`() = runTest {
        val tried = mutableListOf<String>()
        try {
            pool.execute(servers, SelectionPolicy.FAILOVER, null, PoolState()) {
                tried += it.id
                throw SttApiException.TooLarge("too long")
            }
            fail()
        } catch (e: AllServersFailedException) {
            assertEquals("too long", e.message)
        }
        assertEquals(listOf("a"), tried)
    }

    @Test
    fun `quick retry waits Retry-After and tries the same server once more`() = runTest {
        val waits = mutableListOf<Long>()
        val quickPool = ServerPool(sleep = { waits += it })
        val tried = mutableListOf<String>()
        val (result, _) = quickPool.execute(listOf(a, b), SelectionPolicy.FAILOVER, null, PoolState(), quickRetry = true) {
            tried += it.id
            if (tried.size == 1) throw SttApiException.RateLimited("busy", retryAfterSeconds = 3)
            "ok-${it.id}"
        }
        assertEquals("ok-a", result)
        assertEquals(listOf("a", "a"), tried)
        assertEquals(listOf(3_000L), waits)
    }

    @Test
    fun `quick retry happens once, then the next server`() = runTest {
        val quickPool = ServerPool(sleep = {})
        val tried = mutableListOf<String>()
        val (result, _) = quickPool.execute(listOf(a, b), SelectionPolicy.FAILOVER, null, PoolState(), quickRetry = true) {
            tried += it.id
            if (it.id == "a") throw SttApiException.PostprocessFailed("llm down", rawText = null)
            "ok-${it.id}"
        }
        assertEquals("ok-b", result)
        assertEquals(listOf("a", "a", "b"), tried)
    }

    @Test
    fun `no quick retry when disabled or the wait is too long`() = runTest {
        val quickPool = ServerPool(sleep = { fail("should not wait") })
        val tried = mutableListOf<String>()
        quickPool.execute(listOf(a, b), SelectionPolicy.FAILOVER, null, PoolState(), quickRetry = true) {
            tried += it.id
            if (it.id == "a") throw SttApiException.RateLimited("busy", retryAfterSeconds = 60)
            "ok"
        }
        assertEquals(listOf("a", "b"), tried)
    }

    @Test
    fun `retryable only for errors that may go away`() {
        fun failed(e: Throwable) = AllServersFailedException(listOf(a to e))
        assertEquals(true, failed(SttApiException.Network(IOException("x"))).retryable)
        assertEquals(true, failed(SttApiException.ServerUnavailable("503")).retryable)
        assertEquals(false, failed(SttApiException.TooLarge("big")).retryable)
        assertEquals(false, failed(SttApiException.BadRequest("bad audio")).retryable)
        assertEquals(false, failed(SttApiException.InvalidKey("key")).retryable)
    }
}
