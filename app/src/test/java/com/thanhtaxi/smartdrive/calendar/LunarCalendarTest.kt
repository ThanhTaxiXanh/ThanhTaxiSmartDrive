package com.thanhtaxi.smartdrive.calendar

import org.junit.Assert.*
import org.junit.Test

class LunarCalendarTest {
    @Test fun tet2026() {
        assertEquals(LunarDate(1, 1, 2026, false), LunarCalendar.solarToLunar(17, 2, 2026))
    }
    @Test fun tet2025And2024() {
        assertEquals(LunarDate(1,1,2025,false), LunarCalendar.solarToLunar(29,1,2025))
        assertEquals(LunarDate(1,1,2024,false), LunarCalendar.solarToLunar(10,2,2024))
    }
    @Test fun reverseConversionAndInvalidLeap() {
        assertArrayEquals(intArrayOf(17,2,2026), LunarCalendar.lunarToSolar(1,1,2026,false))
        assertNull(LunarCalendar.lunarToSolar(1,1,2026,true))
    }
}
