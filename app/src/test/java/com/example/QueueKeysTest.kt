package com.example

import com.example.data.model.Track
import com.example.service.shuffleOrderWithInsert
import com.example.service.shuffleOrderWithMove
import com.example.ui.components.keyedEntries
import com.example.ui.components.playNextTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `play next lands right after the current song`() {
        // Queue a,b,C,d,e with C (index 2) playing.
        assertEquals(3, playNextTarget(from = 4, current = 2)) // e -> a,b,C,e,d
        assertEquals(2, playNextTarget(from = 0, current = 2)) // a -> b,C,a,d,e
        assertNull(playNextTarget(from = 2, current = 2)) // the current song
        assertNull(playNextTarget(from = 3, current = 2)) // already next
        assertNull(playNextTarget(from = 1, current = -1)) // nothing playing
    }

    @Test
    fun `with shuffle on, play next is next in play order`() {
        // List a,b,c,d (0..3), shuffled play order c,a,d,b; c (2) is playing.
        val order = listOf(2, 0, 3, 1)
        // Play Next inserts x at list index 3, right after c; d moves from 3 to 4.
        assertEquals(listOf(2, 3, 0, 4, 1), shuffleOrderWithInsert(order, inserted = 3, after = 2).toList())
        // Add to Queue appends x at list index 4: it plays last.
        assertEquals(listOf(2, 0, 3, 1, 4), shuffleOrderWithInsert(order, inserted = 4, after = null).toList())
        // The queue's Play Next moves b (1) to list index 2, after c: list a,c,b,d; c is now 1.
        assertEquals(listOf(1, 2, 0, 3), shuffleOrderWithMove(order, from = 1, to = 2, current = 2).toList())
    }
}
