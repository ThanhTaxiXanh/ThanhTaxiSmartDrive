package com.thanhtaxi.smartdrive.calendar

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

data class LunarDate(val day: Int, val month: Int, val year: Int, val leap: Boolean)

/** Adapted from the user-provided Ho Ngoc Duc Vietnamese lunar-calendar algorithm, UTC+7. */
object LunarCalendar {
    private const val RAD = PI / 180.0
    private const val EPOCH = 2415021.076998695
    private const val SYNODIC = 29.530588853
    private const val TZ = 7.0
    private val moonCache = HashMap<Int, Int>()
    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        return dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
    }
    private fun jdToDate(jd: Int): IntArray {
        val b: Int
        val c: Int
        if (jd > 2299160) {
            val a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - b * 146097 / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - 1461 * d / 4
        val m = (5 * e + 2) / 153
        return intArrayOf(e - (153 * m + 2) / 5 + 1, m + 3 - 12 * (m / 10),
            b * 100 + d - 4800 + m / 10)
    }
    private fun newMoon(k: Double): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * RAD)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00239e-3 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * RAD)
        c1 += 0.0021 * sin(2 * m * RAD)
        c1 -= 0.4068 * sin(mpr * RAD)
        c1 += 0.0161 * sin(2 * mpr * RAD)
        c1 -= 0.0004 * sin(3 * mpr * RAD)
        c1 += 0.0104 * sin(2 * f * RAD)
        c1 -= 0.0051 * sin((m + mpr) * RAD)
        c1 -= 0.0074 * sin((m - mpr) * RAD)
        c1 += 0.0004 * sin((2 * f + m) * RAD)
        c1 -= 0.0004 * sin((2 * f - m) * RAD)
        c1 -= 0.0006 * sin((2 * f + mpr) * RAD)
        c1 += 0.0010 * sin((2 * f - mpr) * RAD)
        c1 += 0.0005 * sin((2 * mpr + m) * RAD)
        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd1 + c1 - deltaT
    }
    private fun newMoonDay(k: Int): Int =
        moonCache.getOrPut(k) { floor(newMoon(k.toDouble()) + 0.5 + TZ / 24.0 + 1e-12).toInt() }
    private fun sunLongitude(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525.0
        val t2 = t * t
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        val dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(m * RAD) +
            (0.019993 - 0.000101 * t) * sin(2 * m * RAD) + 0.000290 * sin(3 * m * RAD)
        val l = (l0 + dl) * RAD
        return l - 2 * PI * floor(l / (2 * PI))
    }
    private fun sunIndex(jdn: Int): Int =
        floor(sunLongitude(jdn - 0.5 - TZ / 24.0) / PI * 6 + 1e-12).toInt()
    private fun lunarMonth11(year: Int): Int {
        val off = jdFromDate(31, 12, year) - EPOCH
        val k = floor(off / SYNODIC).toInt()
        var nm = newMoonDay(k)
        if (sunIndex(nm) >= 9) nm = newMoonDay(k - 1)
        return nm
    }
    private fun leapMonthOffset(a11: Int): Int {
        val k = floor((a11 - EPOCH) / SYNODIC + 0.5).toInt()
        var i = 1
        var arc = sunIndex(newMoonDay(k + i))
        while (i < 15) {
            val next = sunIndex(newMoonDay(k + i + 1))
            if (next == arc) break
            arc = next
            i++
        }
        return i
    }
    fun solarToLunar(dd: Int, mm: Int, yy: Int): LunarDate {
        require(yy in 1800..2199 && mm in 1..12 && dd in 1..31)
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - EPOCH) / SYNODIC).toInt()
        var monthStart = newMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = newMoonDay(k)
        var a11 = lunarMonth11(yy)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = lunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = lunarMonth11(yy + 1)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = (monthStart - a11) / 29
        var lunarMonth = diff + 11
        var leap = false
        if (b11 - a11 > 365) {
            val leapDiff = leapMonthOffset(a11)
            if (diff >= leapDiff) {
                lunarMonth = diff + 10
                if (diff == leapDiff) leap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear--
        return LunarDate(lunarDay, lunarMonth, lunarYear, leap)
    }
    fun lunarToSolar(day: Int, month: Int, year: Int, leap: Boolean): IntArray? {
        if (day !in 1..30 || month !in 1..12 || year !in 1800..2199) return null
        val a11 = lunarMonth11(if (month < 11) year - 1 else year)
        val b11 = lunarMonth11(if (month < 11) year else year + 1)
        val k = floor(0.5 + (a11 - EPOCH) / SYNODIC).toInt()
        var off = (month - 11 + 12) % 12
        if (b11 - a11 > 365) {
            val leapOff = leapMonthOffset(a11)
            var leapMonth = leapOff - 2
            if (leapMonth < 0) leapMonth += 12
            if (leap && month != leapMonth) return null
            if (leap || off >= leapOff) off++
        } else if (leap) return null
        val output = jdToDate(newMoonDay(k + off) + day - 1)
        val back = solarToLunar(output[0], output[1], output[2])
        return if (back == LunarDate(day, month, year, leap)) output else null
    }
}
