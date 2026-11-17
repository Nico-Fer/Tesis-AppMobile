package com.steadypath.app.domain.comparison

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AngleFrameTest {

    @Test
    fun `hasValidDimensions es true con al menos una dimension valida`() {
        val frame = AngleFrame(floatArrayOf(0f, 1f), booleanArrayOf(false, true), 0L)
        assertTrue(frame.hasValidDimensions())
    }

    @Test
    fun `hasValidDimensions es false si todas son invalidas`() {
        val frame = AngleFrame(floatArrayOf(0f, 1f), booleanArrayOf(false, false), 0L)
        assertFalse(frame.hasValidDimensions())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `angulos y mascara de distinto tamano fallan fuerte`() {
        AngleFrame(floatArrayOf(0f, 1f), booleanArrayOf(true), 0L)
    }
}
