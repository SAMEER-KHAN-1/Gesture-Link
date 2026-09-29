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
}
