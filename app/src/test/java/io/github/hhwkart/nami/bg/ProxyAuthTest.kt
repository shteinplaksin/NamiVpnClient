package io.github.hhwkart.nami.bg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyAuthTest {

    @Test
    fun generate_producesValidCredentials() {
        assertTrue(ProxyAuth.generate())
        assertTrue(ProxyAuth.valid)
        assertTrue(ProxyAuth.username.isNotBlank())
        assertTrue(ProxyAuth.password.isNotBlank())
        // UUID-based: password is a hex UUID, username prefixed
        assertTrue(ProxyAuth.username.startsWith("neko-"))
        assertEquals(32, ProxyAuth.password.length)
        assertTrue(ProxyAuth.password.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun generate_newCredentialsEveryStart() {
        val seen = HashSet<Pair<String, String>>()
        repeat(100) {
            assertTrue(ProxyAuth.generate())
            val creds = ProxyAuth.username to ProxyAuth.password
            assertFalse("credentials reused across restarts: $creds", seen.contains(creds))
            seen.add(creds)
        }
        assertTrue(seen.size == 100)
    }

    @Test
    fun reset_clearsCredentials() {
        assertTrue(ProxyAuth.generate())
        ProxyAuth.reset()
        assertFalse(ProxyAuth.valid)
        assertEquals("", ProxyAuth.username)
        assertEquals("", ProxyAuth.password)
    }

    @Test
    fun newUser_injectsCredentialsIntoInboundUser() {
        // credentials must be non-empty and survive a read
        assertTrue(ProxyAuth.generate())
        val (user, pass) = ProxyAuth.username to ProxyAuth.password
        assertTrue(user.isNotBlank())
        assertTrue(pass.isNotBlank())
        assertEquals(user, ProxyAuth.username)
        assertEquals(pass, ProxyAuth.password)
    }
}
