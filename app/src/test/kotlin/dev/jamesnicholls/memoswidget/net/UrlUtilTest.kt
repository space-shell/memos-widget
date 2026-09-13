package dev.jamesnicholls.memoswidget.net

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilTest {

    @Test
    fun `empty stays empty`() {
        assertEquals("", UrlUtil.normaliseBaseUrl(""))
    }

    @Test
    fun `adds https scheme when missing`() {
        assertEquals("https://memos.example.com", UrlUtil.normaliseBaseUrl("memos.example.com"))
    }

    @Test
    fun `preserves http scheme and port`() {
        assertEquals(
            "http://192.168.1.10:5230",
            UrlUtil.normaliseBaseUrl("http://192.168.1.10:5230"),
        )
    }

    @Test
    fun `preserves subpath`() {
        assertEquals(
            "https://example.com/memos",
            UrlUtil.normaliseBaseUrl("https://example.com/memos/"),
        )
    }

    @Test
    fun `strips trailing slashes`() {
        assertEquals(
            "https://memos.example.com",
            UrlUtil.normaliseBaseUrl("https://memos.example.com///"),
        )
    }

    @Test
    fun `strips api v1 suffix`() {
        assertEquals(
            "https://memos.example.com",
            UrlUtil.normaliseBaseUrl("https://memos.example.com/api/v1/"),
        )
        assertEquals(
            "https://memos.example.com",
            UrlUtil.normaliseBaseUrl("memos.example.com/api/v1"),
        )
    }

    @Test
    fun `trims whitespace`() {
        assertEquals(
            "https://memos.example.com",
            UrlUtil.normaliseBaseUrl("  https://memos.example.com "),
        )
    }
}
