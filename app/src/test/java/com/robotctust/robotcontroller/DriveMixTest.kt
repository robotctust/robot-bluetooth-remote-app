package com.robotctust.robotcontroller

import com.robotctust.robotcontroller.ui.components.ARM_MAX_PWM
import com.robotctust.robotcontroller.ui.components.CAR_MAX_PWM
import com.robotctust.robotcontroller.ui.components.driveMix
import com.robotctust.robotcontroller.ui.components.percentToPwm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class DriveMixTest {
    /** 搖桿推到底、偏離正前方 deg 度（正 = 往右），回傳左右輪 %。 */
    private fun at(deg: Double, max: Int = 100, forward: Boolean = true): Pair<Int, Int> {
        val r = Math.toRadians(deg)
        val y = cos(r).toFloat() * if (forward) 1 else -1
        return driveMix(sin(r).toFloat(), y, max)
    }

    @Test fun deadZoneStops() = assertEquals(0 to 0, driveMix(0.05f, 0.05f, 100))

    @Test fun straightAndBack() {
        assertEquals(100 to 100, at(0.0))
        assertEquals(-100 to -100, at(0.0, forward = false))
        assertEquals(40 to 40, driveMix(0f, 0.4f, 100)) // 推一半以下：速度跟距離成正比
        assertEquals(30 to 30, at(0.0, max = 30))        // 滑桿 = 最高速度
    }

    @Test fun fineZoneIsGentle() {
        val (l, r) = at(30.0)
        assertEquals(100, l)
        assertEquals(70, r) // 30° 時內側輪只慢 30%（線性對應會是 33% 轉向 → 內側 33）
        val (l2, r2) = at(-15.0)
        assertTrue("15° 內側輪仍接近全速", l2 in 80..90 && r2 == 100)
    }

    @Test fun ninetyDegreesSpinsInPlace() {
        assertEquals(100 to -100, at(90.0))
        assertEquals(-100 to 100, at(-90.0))
        assertEquals(100 to -100, at(90.0, forward = false)) // 前後在 90° 連續
    }

    @Test fun percentMapsToFirmwarePwm() {
        assertEquals(0, percentToPwm(0, CAR_MAX_PWM))
        assertEquals(100, percentToPwm(1, CAR_MAX_PWM))     // 最小速度 = MIN_PWM，車子推得動
        assertEquals(200, percentToPwm(100, CAR_MAX_PWM))
        assertEquals(-200, percentToPwm(-100, CAR_MAX_PWM)) // 負數 = 反轉
        assertEquals(220, percentToPwm(100, ARM_MAX_PWM))
    }

    @Test fun backwardMirrorsForward() {
        assertEquals(-70 to -100, at(30.0, forward = false))
        assertEquals(-100 to -70, at(-30.0, forward = false))
    }
}
