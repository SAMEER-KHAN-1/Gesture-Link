package com.gesturelink.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationLogTest {

    private fun entry(n: Int) = NotificationEntry("message $n", n.toLong())

    @Test
    fun `adding to an empty log gives just that entry`() {
        assertEquals(listOf(entry(1)), emptyList<NotificationEntry>().withNewest(entry(1)))
    }

    @Test
    fun `the newest entry goes first`() {
        val log = listOf(entry(1)).withNewest(entry(2)).withNewest(entry(3))

        assertEquals(listOf(entry(3), entry(2), entry(1)), log)
    }

    @Test
    fun `the oldest entries are dropped past the limit`() {
        val log = listOf(entry(3), entry(2), entry(1)).withNewest(entry(4), limit = 3)

        assertEquals(listOf(entry(4), entry(3), entry(2)), log)
    }

    @Test
    fun `the default limit is the max notifications constant`() {
        var log = emptyList<NotificationEntry>()
        for (n in 1..MAX_NOTIFICATIONS + 10) log = log.withNewest(entry(n))

        assertEquals(MAX_NOTIFICATIONS, log.size)
        assertEquals(entry(MAX_NOTIFICATIONS + 10), log.first())
    }

    @Test
    fun `the original list is not modified`() {
        val original = listOf(entry(1))

        original.withNewest(entry(2))

        assertEquals(listOf(entry(1)), original)
    }
}
