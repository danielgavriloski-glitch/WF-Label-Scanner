package com.mbidesign.terminal

import kotlin.math.abs

/** Basic active challenge; not a certified presentation-attack detector. */
class Liveness(private val turnSign: Int, private val blinkFirst: Boolean) {
    enum class Phase { CENTER, TURN, RETURN, BLINK, DONE }
    var phase = Phase.CENTER; private set
    private var steady = 0
    private var blink = 0
    private var openedAt = 0L
    private var began = 0L
    private var blinkDone = false
    private var turnDone = false
    fun reset() {
        phase = Phase.CENTER; steady = 0; blink = 0; began = 0; openedAt = 0
        blinkDone = false; turnDone = false
    }
    fun observe(identity: Boolean, yaw: Float, leftEye: Float?, rightEye: Float?, now: Long): Boolean {
        if (!identity) { reset(); return false }
        if (began == 0L) began = now
        if (now - began > 30000) { reset(); return false }
        val open = leftEye != null && rightEye != null && leftEye > .65f && rightEye > .65f
        val closed = leftEye != null && rightEye != null && leftEye < .25f && rightEye < .25f
        when (phase) {
            Phase.CENTER -> {
                steady = if (abs(yaw) < 12 && open) steady + 1 else 0
                if (steady >= 3) { steady = 0; phase = if (blinkFirst) Phase.BLINK else Phase.TURN }
            }
            Phase.TURN -> {
                steady = if (yaw * turnSign > 20 && abs(yaw) < 40) steady + 1 else 0
                if (steady >= 2) { steady = 0; phase = Phase.RETURN }
            }
            Phase.RETURN -> {
                steady = if (abs(yaw) < 10 && open) steady + 1 else 0
                if (steady >= 3) { steady = 0; turnDone = true; phase = if (blinkDone) Phase.DONE else Phase.BLINK }
            }
            Phase.BLINK -> {
                if (abs(yaw) > 12) { blink = 0; return false }
                if (blink > 0 && now - openedAt > 5000) blink = 0
                if (blink == 0 && open) { blink = 1; openedAt = now }
                else if (blink == 1 && closed) blink = 2
                else if (blink == 2 && open) { blinkDone = true; phase = if (turnDone) Phase.DONE else Phase.TURN }
            }
            Phase.DONE -> Unit
        }
        return phase == Phase.DONE
    }
    fun instruction(): String = when (phase) {
        Phase.CENTER -> "Гледај право во камерата"
        // Input is unmirrored, while the front-camera preview is mirrored.
        Phase.TURN -> if (turnSign > 0) "Заврти ја главата налево ←" else "Заврти ја главата надесно →"
        Phase.RETURN -> "Врати ја главата право"
        Phase.BLINK -> "Затвори ги очите кратко, па отвори ги"
        Phase.DONE -> "Лицето е потврдено"
    }
}
