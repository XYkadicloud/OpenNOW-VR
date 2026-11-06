package com.opencloudgaming.opennow.vr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class VrMatrix4Test {

    private fun assertEqualsMatrix(expected: FloatArray, actual: FloatArray, delta: Float = 1e-5f) {
        assertEquals(VrMatrix4.SIZE, actual.size)
        for (i in 0 until VrMatrix4.SIZE) {
            assertEquals("matrix element $i", expected[i], actual[i], delta)
        }
    }

    @Test
    fun identityHasOnesOnTheDiagonal() {
        val m = VrMatrix4.identity()
        assertEquals(VrMatrix4.SIZE, m.size)
        for (column in 0..3) {
            for (row in 0..3) {
                val expected = if (column == row) 1f else 0f
                assertEquals("[$column,$row]", expected, m[column * 4 + row], 0f)
            }
        }
    }

    @Test
    fun multiplyingByIdentityIsNeutralOnBothSides() {
        val rot = VrMatrix4.rotation(0f, 1f, 0f, 0.7f)
        assertEqualsMatrix(rot, VrMatrix4.multiply(VrMatrix4.identity(), rot))
        assertEqualsMatrix(rot, VrMatrix4.multiply(rot, VrMatrix4.identity()))
    }

    @Test
    fun multiplicationIsAssociative() {
        val a = VrMatrix4.rotation(1f, 0f, 0f, 0.3f)
        val b = VrMatrix4.rotation(0f, 1f, 0f, 0.8f)
        val c = VrMatrix4.rotation(0f, 0f, 1f, 0.5f)
        val left = VrMatrix4.multiply(VrMatrix4.multiply(a, b), c)
        val right = VrMatrix4.multiply(a, VrMatrix4.multiply(b, c))
        assertEqualsMatrix(left, right)
    }

    @Test
    fun multiplicationIsNotCommutative() {
        val a = VrMatrix4.rotation(1f, 0f, 0f, 0.9f)
        val b = VrMatrix4.rotation(0f, 1f, 0f, 0.9f)
        val ab = VrMatrix4.multiply(a, b)
        val ba = VrMatrix4.multiply(b, a)
        var different = false
        for (i in 0 until VrMatrix4.SIZE) {
            if (kotlin.math.abs(ab[i] - ba[i]) > 1e-4f) different = true
        }
        assertTrue("rotations about different axes must not commute", different)
    }

    @Test
    fun yRotationMatchesTheAnalyticColumnMajorForm() {
        val angle = 0.6f
        val m = VrMatrix4.rotation(0f, 1f, 0f, angle)
        assertEquals(cos(angle), m[0], 1e-6f)
        assertEquals(-sin(angle), m[2], 1e-6f)
        assertEquals(1f, m[5], 1e-6f)
        assertEquals(sin(angle), m[8], 1e-6f)
        assertEquals(cos(angle), m[10], 1e-6f)
        assertEquals(1f, m[15], 1e-6f)
    }

    @Test
    fun axisIsNormalisedBeforeUse() {
        val unit = VrMatrix4.rotation(0f, 1f, 0f, 0.4f)
        val scaled = VrMatrix4.rotation(0f, 10f, 0f, 0.4f)
        assertEqualsMatrix(unit, scaled)
    }

    @Test
    fun zeroLengthAxisReturnsIdentity() {
        assertEqualsMatrix(
            VrMatrix4.identity(),
            VrMatrix4.rotation(0f, 0f, 0f, 1f),
            delta = 0f,
        )
    }

    @Test
    fun rejectsNonSquare4x4Input() {
        assertThrows(IllegalArgumentException::class.java) {
            VrMatrix4.multiply(FloatArray(9), VrMatrix4.identity())
        }
        assertThrows(IllegalArgumentException::class.java) {
            VrMatrix4.multiply(VrMatrix4.identity(), FloatArray(17))
        }
    }
}
