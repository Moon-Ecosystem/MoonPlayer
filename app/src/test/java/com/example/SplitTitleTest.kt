package com.example

import com.example.data.model.splitTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTitleTest {

    @Test
    fun `version notes at the end move to their own line`() {
        assertEquals("ウィーアー！" to "From THE FIRST TAKE", splitTitle("ウィーアー！ - From THE FIRST TAKE"))
        assertEquals("Nandemonaiya" to "movie ver.", splitTitle("Nandemonaiya - movie ver."))
        assertEquals("Sparkle" to "Original Version", splitTitle("Sparkle - Original Version"))
        assertEquals("Yesterday" to "Remastered 2009", splitTitle("Yesterday (Remastered 2009)"))
        assertEquals("Bohemian Rhapsody" to "Live", splitTitle("Bohemian Rhapsody [Live]"))
        assertEquals("夜に駆ける" to "TV Size", splitTitle("夜に駆ける（TV Size）"))
    }

    @Test
    fun `several notes and a bare feat are all collected in order`() {
        assertEquals(
            "Is There Still Anything That Love Can Do?" to "Weathering With You Movie Edit · feat. Toko Miura",
            splitTitle("Is There Still Anything That Love Can Do? (Weathering With You Movie Edit) feat. Toko Miura")
        )
        assertEquals("Song" to "feat. A · Radio Edit", splitTitle("Song (feat. A) - Radio Edit"))
    }

    @Test
    fun `ordinary titles stay whole`() {
        assertEquals("Hello - Goodbye" to null, splitTitle("Hello - Goodbye"))
        assertEquals("アイドル" to null, splitTitle("アイドル"))
        assertEquals("Is There Still Anything That Love Can Do?" to null, splitTitle("Is There Still Anything That Love Can Do?"))
        assertEquals("Song (Part 2)" to null, splitTitle("Song (Part 2)"))
        // Nothing would be left of the title.
        assertEquals("(Live)" to null, splitTitle("(Live)"))
    }
}
