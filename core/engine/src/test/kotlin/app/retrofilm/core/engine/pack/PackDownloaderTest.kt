package app.retrofilm.core.engine.pack

import java.io.File
import java.net.URL
import java.security.MessageDigest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val body = ByteArray(10_000) { (it * 31).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
    private val downloader = PackDownloader(UrlPolicy(setOf("localhost", "127.0.0.1"), httpsOnly = false))

    @Before fun start() = server.start()

    @After fun stop() = server.shutdown()

    private fun dest() = File(tmp.root, "luts/a.png")

    @Test
    fun checksumMismatchDeletesAndFails() {
        server.enqueue(MockResponse().setBody(Buffer().write(body)))
        val r = downloader.download(server.url("/a.png").toString(), dest(), "0".repeat(64), body.size.toLong())
        assertEquals(DownloadResult.Failed(DownloadFailure.CHECKSUM), r)
        assertFalse(dest().exists())
        assertFalse(File(dest().path + ".part").exists())
    }

    @Test
    fun resumesWithRangeAndRenamesAtomically() {
        dest().parentFile!!.mkdirs()
        val part = File(dest().path + ".part")
        part.writeBytes(body.copyOfRange(0, 4000))
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 4000-9999/10000")
                .setBody(Buffer().write(body.copyOfRange(4000, body.size)))
        )
        val r = downloader.download(server.url("/a.png").toString(), dest(), sha, body.size.toLong())
        assertTrue(r is DownloadResult.Ok)
        assertEquals("bytes=4000-", server.takeRequest().getHeader("Range"))
        assertTrue(dest().readBytes().contentEquals(body))
        assertFalse(part.exists())
    }

    @Test
    fun wrongContentRangeRestartsFromZero() {
        dest().parentFile!!.mkdirs()
        val part = File(dest().path + ".part")
        part.writeBytes(body.copyOfRange(0, 4000))
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-9999/10000")
                .setBody(Buffer().write(body))
        )
        server.enqueue(MockResponse().setBody(Buffer().write(body)))
        val r = downloader.download(server.url("/a.png").toString(), dest(), sha, body.size.toLong())
        assertTrue(r is DownloadResult.Ok)
        assertEquals("bytes=4000-", server.takeRequest().getHeader("Range"))
        assertNull(server.takeRequest().getHeader("Range"))
        assertTrue(dest().readBytes().contentEquals(body))
    }

    @Test
    fun contentRangeParsing() {
        assertEquals(4000L, PackDownloader.contentRangeStart("bytes 4000-9999/10000"))
        assertNull(PackDownloader.contentRangeStart(null))
        assertNull(PackDownloader.contentRangeStart("bytes */10000"))
    }

    @Test
    fun plainUserAgentAndNoRangeOnFreshDownload() {
        server.enqueue(MockResponse().setBody(Buffer().write(body)))
        downloader.download(server.url("/a.png").toString(), dest(), sha, body.size.toLong())
        val req = server.takeRequest()
        assertEquals(PackDownloader.USER_AGENT, req.getHeader("User-Agent"))
        assertNull(req.getHeader("Range"))
    }

    @Test
    fun networkErrorKeepsPartForResume() {
        server.enqueue(MockResponse().setResponseCode(500))
        val r = downloader.download(server.url("/a.png").toString(), dest(), sha, body.size.toLong())
        assertEquals(DownloadResult.Failed(DownloadFailure.NETWORK), r)
    }

    @Test
    fun notEnoughSpaceFailsWithStorage() {
        val d = PackDownloader(UrlPolicy(setOf("localhost"), httpsOnly = false), freeSpace = { 0L })
        val r = d.download(server.url("/a.png").toString(), dest(), sha, body.size.toLong())
        assertEquals(DownloadResult.Failed(DownloadFailure.STORAGE), r)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun redirectToForeignHostRejected() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://evil.example/a.png"))
        val r = downloader.download(server.url("/a.png").toString(), dest(), sha, body.size.toLong())
        assertEquals(DownloadResult.Failed(DownloadFailure.NETWORK), r)
    }

    @Test
    fun productionPolicyIsHttpsSingleHostPinnedTag() {
        val p = UrlPolicy.PRODUCTION
        assertTrue(p.isAllowed(URL("https://raw.githubusercontent.com/Jack4B/Retro-Film-LUTs/luts-v1/c/a.png")))
        assertFalse(p.isAllowed(URL("http://raw.githubusercontent.com/x")))
        assertFalse(p.isAllowed(URL("https://github.com/x")))
        val e = PackCatalog.parse(
            """{"grid":144,"luts":[{"id":"bw/a","displayName":"A","file":"luts/bw/a.png","sha256":"",""" +
                """"bytes":1,"unpackedLength":8957952}]}"""
        ).single()
        assertEquals(
            "https://raw.githubusercontent.com/Jack4B/Retro-Film-LUTs/luts-v1/luts/bw/a.png",
            PackCatalog.urlFor(e)
        )
        assertFalse(e.downloadable)
        assertEquals("bw~a", e.localName)
    }
}
