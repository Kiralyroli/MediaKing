package com.kiroland.mediacenter.data.addons

import com.kiroland.mediacenter.util.TestStrings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AddonEngineTest {
    init {
        TestStrings.install()
    }


    // A web player page of the common "player.setup({...});" kind, with a station ident first.
    private val page = """
        <html><script>
        player.setup( {
          "autostart": "false",
          "playlist": [
            { "file": "\/\/cdn.example\/ident\/bumper.mp4", "type": "mp4" },
            { "file": "\/\/cdn.example\/live\/ch1\/index.m3u8?t=abc", "type": "hls" }
          ]
        } );
        </script></html>
    """.trimIndent()

    private val requested = mutableListOf<Pair<String, Map<String, String>>>()
    private val engine = AddonEngine { url, headers -> requested += url to headers; page }

    private val addon = AddonParser.parse(
        """
        {
          "id": "example.player",
          "name": "Example",
          "headers": { "User-Agent": "Test" },
          "channels": [ { "id": "ch1", "name": "Channel One", "vars": { "video": "ch1 live" } } ],
          "resolve": [
            { "get": "https://player.example/player.php?video={video}", "headers": { "Referer": "https://example/" } },
            { "regex": "setup\\(\\s*(\\{.*?\\})\\s*\\);" },
            { "json": "playlist[file!~bumper].file" },
            { "replace": "^//", "with": "https://" }
          ]
        }
        """.trimIndent(),
    ).getOrThrow()

    @Test
    fun `runs the steps to a stream url`() = runBlocking {
        val url = engine.resolve(addon, addon.channels.single())
        assertEquals("https://cdn.example/live/ch1/index.m3u8?t=abc", url)
        val (requestUrl, headers) = requested.single()
        assertEquals("https://player.example/player.php?video=ch1+live", requestUrl)
        assertEquals(mapOf("User-Agent" to "Test", "Referer" to "https://example/"), headers)
    }

    @Test
    fun `fixed urls need no steps`() = runBlocking {
        val fixed = AddonParser.parse(
            """{ "id": "demo", "name": "Demo", "channels": [ { "id": "a", "name": "A", "url": "https://x/a.m3u8" } ] }""",
        ).getOrThrow()
        assertEquals("https://x/a.m3u8", engine.resolve(fixed, fixed.channels.single()))
        assertTrue(requested.isEmpty())
    }

    @Test
    fun `failures say which step went wrong`() = runBlocking {
        val broken = AddonEngine { _, _ -> "<html>no player here</html>" }
        try {
            broken.resolve(addon, addon.channels.single())
            fail()
        } catch (e: AddonException) {
            assertEquals("2. lépés: a regex nem talált egyezést", e.message)
        }
    }

    @Test
    fun `result must be an http url`() = runBlocking {
        val odd = AddonEngine { _, _ -> """player.setup({"playlist":[{"file":"rtmp://x/y","type":"hls"}]});""" }
        try {
            odd.resolve(addon, addon.channels.single())
            fail()
        } catch (e: AddonException) {
            assertTrue(e.message!!.startsWith("A kapott cím nem http(s)"))
        }
    }

    @Test
    fun `json path language`() {
        val root = Json.parseToJsonElement(
            """{"a":{"b":[{"t":"x","v":1},{"t":"hls","v":2}]},"list":[{"name":"first"},{"name":"second"}]}""",
        )
        fun eval(p: String) = (JsonPath.parse(p).evaluate(root) as? JsonPrimitive)?.content
        assertEquals("2", eval("a.b[t=hls].v"))
        assertEquals("1", eval("a.b[0].v"))
        assertEquals("1", eval("a.b[t!=hls].v"))
        assertEquals("2", eval("a.b[t~hl].v"))
        assertEquals("first", eval("list.name"))
        assertNull(eval("a.missing"))
    }

    @Test
    fun `invalid add-ons are refused with a reason`() {
        fun error(json: String) = AddonParser.parse(json).exceptionOrNull()?.message.orEmpty()
        assertTrue(error("not json").startsWith("Nem érvényes kiegészítő-fájl"))
        assertTrue(error("""{"id":"Bad Id","name":"x","channels":[{"id":"a","name":"A","url":"https://x"}]}""").startsWith("Az azonosító"))
        assertEquals("Nincs egyetlen csatorna sem (se channels, se playlist)", error("""{"id":"ok","name":"x","channels":[]}"""))
        assertEquals("A: nincs se url, se resolve lépés", error("""{"id":"ok","name":"x","channels":[{"id":"a","name":"A"}]}"""))
        assertTrue(
            error("""{"id":"ok","name":"x","channels":[{"id":"a","name":"A"}],"resolve":[{"get":"https://x","regex":"y"}]}""")
                .contains("pontosan egy művelet"),
        )
        assertTrue(
            error("""{"id":"ok","name":"x","channels":[{"id":"a","name":"A"}],"resolve":[{"get":"file:///etc/passwd"}]}""")
                .contains("csak http(s)"),
        )
        assertTrue(
            error("""{"id":"ok","name":"x","channels":[{"id":"a","name":"A"}],"resolve":[{"regex":"(unclosed"}]}""")
                .startsWith("Hibás regex"),
        )
    }
}
