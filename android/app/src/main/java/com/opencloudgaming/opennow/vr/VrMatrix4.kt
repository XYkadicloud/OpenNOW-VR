package com.opencloudgaming.opennow.vr

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal OpenGL column-major 4x4 matrix helpers, in pure Kotlin.
 *
 * Kept independent of android.opengl.Matrix so the maths can be verified by JVM unit tests.
 * Layout matches OpenGL: element [column * 4 + row].
 */
object VrMatrix4 {
    const val SIZE = 16

    fun identity(): FloatArray = FloatArray(SIZE).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    /** Returns lhs * rhs in column-major order (same semantics as Matrix.multiplyMM). */
    fun multiply(lhs: FloatArray, rhs: FloatArray): FloatArray {
        require(lhs.size == SIZE && rhs.size == SIZE) { "4x4 matrices required" }
        val out = FloatArray(SIZE)
        for (column in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) sum += lhs[k * 4 + row] * rhs[column * 4 + k]
                out[column * 4 + row] = sum
            }
        }
        return out
    }

    /** Rotation matrix around an arbitrary axis, equivalent to glRotatef(angleRadians). */
    fun rotation(axisX: Float, axisY: Float, axisZ: Float, angleRadians: Float): FloatArray {
        val length = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
        if (length < 1e-7f) return identity()
        val x = axisX / length
        val y = axisY / length
        val z = axisZ / length
        val c = cos(angleRadians)
        val s = sin(angleRadians)
        val t = 1f - c
        return floatArrayOf(
            t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0f,
            t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0f,
            t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
