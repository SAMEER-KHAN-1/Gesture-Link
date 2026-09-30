package com.gesturelink.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun `bytes under 1024 are shown as plain bytes`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("500 B", formatFileSize(500))
        assertEquals("1023 B", formatFileSize(1023))
    }

    @Test
    fun `exactly 1024 bytes is one kilobyte`() {
        assertEquals("1.0 KB", formatFileSize(1024))
    }

    @Test
    fun `fractional kilobytes are rounded to one decimal place`() {
        assertEquals("1.5 KB", formatFileSize(1536))
        assertEquals("2.4 KB", formatFileSize(2500))
    }

    @Test
    fun `megabytes and gigabytes use the right unit`() {
        assertEquals("1.0 MB", formatFileSize(1024L * 1024))
        assertEquals("1.0 GB", formatFileSize(1024L * 1024 * 1024))
    }

    @Test
    fun `terabyte-sized files are labelled TB, not GB`() {
        // Regression test: the original implementation capped its unit index one
        // step too low, so a 1TB file was mislabelled "1024.0 GB" instead of "1.0 TB".
        assertEquals("1.0 TB", formatFileSize(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `sizes beyond a terabyte stay in TB rather than an unlabelled unit`() {
        assertEquals("1024.0 TB", formatFileSize(1024L * 1024 * 1024 * 1024 * 1024))
    }

    @Test
    fun `uptime under an hour is shown in minutes`() {
        assertEquals("0m", formatUptime(0))
        assertEquals("0m", formatUptime(59))
        assertEquals("7m", formatUptime(7 * 60L))
        assertEquals("59m", formatUptime(3599))
    }

    @Test
    fun `uptime under a day is shown in hours and minutes`() {
        assertEquals("1h 0m", formatUptime(3600))
        assertEquals("5h 12m", formatUptime(5 * 3600L + 12 * 60))
        assertEquals("23h 59m", formatUptime(86399))
    }

    @Test
    fun `uptime of a day or more is shown in days and hours`() {
        assertEquals("1d 0h", formatUptime(86400))
        assertEquals("3d 4h", formatUptime(3 * 86400L + 4 * 3600 + 59 * 60))
    }

    @Test
    fun `a negative uptime is treated as zero`() {
        assertEquals("0m", formatUptime(-5))
    }
}
