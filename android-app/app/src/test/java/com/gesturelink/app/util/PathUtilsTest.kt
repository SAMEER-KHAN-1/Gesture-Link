package com.gesturelink.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PathUtilsTest {

    @Test
    fun `a drive root has just itself as an ancestor`() {
        assertEquals(listOf("C:\\"), pathAncestors("C:\\"))
    }

    @Test
    fun `a nested folder lists every folder from the drive root down`() {
        assertEquals(
            listOf("C:\\", "C:\\Users", "C:\\Users\\sam", "C:\\Users\\sam\\Documents"),
            pathAncestors("C:\\Users\\sam\\Documents"),
        )
    }

    @Test
    fun `a trailing backslash does not add an extra entry`() {
        assertEquals(listOf("D:\\", "D:\\Music"), pathAncestors("D:\\Music\\"))
    }

    @Test
    fun `a non-drive path is kept as a single entry`() {
        assertEquals(listOf("\\\\server\\share"), pathAncestors("\\\\server\\share"))
        assertEquals(listOf(""), pathAncestors(""))
    }

    @Test
    fun `the last ancestor is the path itself so opening it lands in the right folder`() {
        val path = "E:\\Projects\\GestureLink"
        assertEquals(path, pathAncestors(path).last())
    }

    @Test
    fun `folder label is the last segment`() {
        assertEquals("Documents", folderLabel("C:\\Users\\sam\\Documents"))
        assertEquals("Music", folderLabel("D:\\Music\\"))
    }

    @Test
    fun `folder label for a drive root is the drive letter`() {
        assertEquals("C:", folderLabel("C:\\"))
    }

    @Test
    fun `toggling adds a missing bookmark`() {
        assertEquals(listOf("C:\\A", "C:\\B"), toggleBookmark(listOf("C:\\B"), "C:\\A"))
    }

    @Test
    fun `toggling removes an existing bookmark`() {
        assertEquals(listOf("C:\\B"), toggleBookmark(listOf("C:\\A", "C:\\B"), "C:\\A"))
    }

    @Test
    fun `toggling twice gets back to where it started`() {
        val start = listOf("C:\\A", "C:\\B")
        assertEquals(start, toggleBookmark(toggleBookmark(start, "C:\\C"), "C:\\C"))
    }
}
