package com.example

import com.example.data.model.Track
import com.example.ui.components.keyedEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueKeysTest {
    private fun track(id: String) = Track(id = id, contentUri = "", title = id, artist = "", album = "", durationMs = 0)

    @Test
    fun `a song queued twice gets distinct keys`() {
        val keys = keyedEntries(listOf(track("a"), track("b"), track("a"))).map { it.first }
        assertEquals(listOf("a#0", "b#0", "a#1"), keys)
    }

    @Test
    fun `keys follow the song, not its position`() {
        // After moving "b" to the front, "b" keeps its key, so a dragged row isn't re-created.
        val keys = keyedEntries(listOf(track("b"), track("a"), track("c"))).map { it.first }
        assertEquals(listOf("b#0", "a#0", "c#0"), keys)
    }
}
