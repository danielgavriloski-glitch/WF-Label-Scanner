package com.mbidesign.terminal

import org.junit.Assert.*
import org.junit.Test

class LivenessTest {
    @Test fun stillPhotoCannotPass() {
        val c = Liveness(1,false)
        for (n in 1..100) assertFalse(c.observe(true,0f,1f,1f,n*100L))
        assertEquals(Liveness.Phase.TURN,c.phase)
    }
    @Test fun missingEyeMeasurementsNeverPass() {
        val c = Liveness(1,true)
        for (n in 1..100) assertFalse(c.observe(true,0f,null,null,n*100L))
        assertEquals(Liveness.Phase.CENTER,c.phase)
    }
    @Test fun mismatchResetsChallengeAndPreventsFaceSwap() {
        val c = Liveness(1,false)
        repeat(3) { c.observe(true,0f,1f,1f,100L+it*100) }
        assertEquals(Liveness.Phase.TURN,c.phase)
        assertFalse(c.observe(false,25f,1f,1f,500))
        assertEquals(Liveness.Phase.CENTER,c.phase)
    }
    @Test fun completeTurnAndBlinkRequired() {
        val c = Liveness(1,false); var now = 100L
        fun frame(yaw: Float, eye: Float): Boolean { now += 150; return c.observe(true,yaw,eye,eye,now) }
        repeat(3) { assertFalse(frame(0f,1f)) }
        repeat(2) { assertFalse(frame(-25f,1f)) }
        repeat(2) { assertFalse(frame(25f,1f)) }
        repeat(3) { assertFalse(frame(0f,1f)) }
        assertFalse(frame(0f,1f)); assertFalse(frame(0f,0f)); assertTrue(frame(0f,1f))
        assertEquals(Liveness.Phase.DONE,c.phase)
    }
    @Test fun blinkMustHaveOpenCloseOpenSequence() {
        val c = Liveness(-1,true)
        repeat(3) { c.observe(true,0f,1f,1f,100L+it*100) }
        assertFalse(c.observe(true,0f,0f,0f,600))
        assertFalse(c.observe(true,0f,1f,1f,700))
        assertEquals(Liveness.Phase.BLINK,c.phase)
    }
    @Test fun oldChallengeTimesOut() {
        val c = Liveness(1,false)
        repeat(3) { c.observe(true,0f,1f,1f,100L+it*100) }
        assertFalse(c.observe(true,25f,1f,1f,40000))
        assertEquals(Liveness.Phase.CENTER,c.phase)
    }
}
