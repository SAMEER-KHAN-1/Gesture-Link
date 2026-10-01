package com.gesturelink.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `ordinary names are valid`() {
        assertTrue(isValidFileName("notes.txt"))
        assertTrue(isValidFileName("My Folder"))
        assertTrue(isValidFileName(".gitignore"))
    }

    @Test
    fun `blank and dot names are invalid`() {
        assertFalse(isValidFileName(""))
        assertFalse(isValidFileName("   "))
        assertFalse(isValidFileName("."))
        assertFalse(isValidFileName(".."))
    }

    @Test
    fun `names with path separators or reserved characters are invalid`() {
        for (bad in listOf("a/b", "a\\b", "..\\x", "what?", "star*", "a:b", "q\"q", "<a>", "a|b")) {
            assertFalse("'$bad' should be invalid", isValidFileName(bad))
        }
    }

    @Test
    fun `names ending in a dot or space are invalid`() {
        assertFalse(isValidFileName("name."))
        assertFalse(isValidFileName("name "))
    }

    @Test
    fun `control characters are invalid`() {
        assertFalse(isValidFileName("a\nb"))
    }

    @Test
    fun `removing a folder's bookmarks drops it and everything inside but not lookalikes`() {
        val bookmarks = listOf("C:\\Docs", "C:\\Docs\\Old", "C:\\Docs2", "C:\\Music")
        assertEquals(listOf("C:\\Docs2", "C:\\Music"), removeBookmarksUnder(bookmarks, "C:\\Docs"))
    }

    @Test
    fun `removing bookmarks for a folder that has none changes nothing`() {
        val bookmarks = listOf("C:\\A", "C:\\B")
        assertEquals(bookmarks, removeBookmarksUnder(bookmarks, "C:\\C"))
    }

    @Test
    fun `renaming a folder moves its bookmark and nested ones`() {
        val bookmarks = listOf("C:\\Docs", "C:\\Docs\\Old", "C:\\Music")
        assertEquals(
            listOf("C:\\Music", "C:\\Papers", "C:\\Papers\\Old"),
            renameBookmarksUnder(bookmarks, "C:\\Docs", "C:\\Papers"),
        )
    }

    @Test
    fun `renaming leaves lookalike sibling bookmarks alone`() {
        val bookmarks = listOf("C:\\Docs", "C:\\Docs2")
        assertEquals(
            listOf("C:\\Docs2", "C:\\Papers"),
            renameBookmarksUnder(bookmarks, "C:\\Docs", "C:\\Papers"),
        )
    }
}
