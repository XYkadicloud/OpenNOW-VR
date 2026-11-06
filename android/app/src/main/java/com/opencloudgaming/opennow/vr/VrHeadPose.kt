package com.opencloudgaming.opennow.vr

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Converts a Cardboard HeadTransform quaternion into a view rotation matrix with an adjustable
 * sensitivity. Pure Kotlin so it can be unit tested.
 *
 * IMPORTANT: head pose is used ONLY to build the view matrix (i.e. looking around).
 * It must never drive selection, confirmation, clicking or any other control decision.
 * All in-VR control is physical gamepad input, per the user's explicit requirement.
 *
 * PERFORMANCE: [scaledRotationInto] is the zero-allocation path used by the render loop.
 */
object VrHeadPose {
    /**
     * @param quaternion (x, y, z, w) as produced by HeadTransform.getQuaternion(float[], 0)
     * @param sensitivity 0 = fixed view (identity), 1 = exact head rotation, >1 = amplified
     * @return column-major 4x4 rotation matrix suitable for eyeFromHead * rotation
     */
    fun scaledRotation(quaternion: FloatArray, sensitivity: Float): FloatArray =
        scaledRotationInto(FloatArray(VrMatrix4.SIZE), quaternion, sensitivity)

    /** Zero-allocation variant. Writes the rotation into [out] and returns it. */
    fun scaledRotationInto(
        out: FloatArray,
        quaternion: FloatArray,
        sensitivity: Float,
    ): FloatArray {
        require(quaternion.size >= 4) { "quaternion needs 4 components" }
        require(out.size == VrMatrix4.SIZE) { "4x4 matrix required" }
        if (sensitivity <= 0f) return VrMatrix4.identityInto(out)

        var x = quaternion[0]
        var y = quaternion[1]
        var z = quaternion[2]
        var w = quaternion[3]
        val length = sqrt(x * x + y * y + z * z + w * w)
        if (length < 1e-7f) return VrMatrix4.identityInto(out)
        x /= length
        y /= length
        z /= length
        w /= length

        // Shortest-arc convention: a quaternion and its negation describe the same rotation.
        if (w < 0f) {
            x = -x
            y = -y
            z = -z
            w = -w
        }

        val clampedW = w.coerceIn(-1f, 1f)
        val angle = 2f * acos(clampedW)
        val sinHalf = sqrt((1f - clampedW * clampedW).coerceAtLeast(0f))
        if (sinHalf < 1e-6f || angle < 1e-6f) return VrMatrix4.identityInto(out)

        return VrMatrix4.rotationInto(out, x / sinHalf, y / sinHalf, z / sinHalf, angle * sensitivity)
    }
}
