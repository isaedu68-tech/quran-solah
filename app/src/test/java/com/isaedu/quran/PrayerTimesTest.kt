package com.isaedu.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class PrayerTimesTest {
    @Test fun hoChiMinh_MWL_Shafii() {
        val t = PrayerTimes.compute(2026, 11, 10, 10.7769, 106.7009, 7.0, Method.MWL, AsrMadhhab.SHAFII)
        val s = t.mapValues { PrayerTimes.hhmm(it.value) }
        assertEquals("04:34", s[Prayer.FAJR]); assertEquals("05:47", s[Prayer.SUNRISE]); assertEquals("11:37", s[Prayer.DHUHR])
        assertEquals("14:57", s[Prayer.ASR]); assertEquals("17:27", s[Prayer.MAGHRIB]); assertEquals("18:36", s[Prayer.ISHA])
    }
    @Test fun hanafiAsrLater() {
        val a = PrayerTimes.compute(2026, 11, 10, 10.7769, 106.7009, 7.0, Method.MWL, AsrMadhhab.SHAFII)[Prayer.ASR]!!
        val b = PrayerTimes.compute(2026, 11, 10, 10.7769, 106.7009, 7.0, Method.MWL, AsrMadhhab.HANAFI)[Prayer.ASR]!!
        assert(b > a + 0.5)
    }
    @Test fun qiblaHcm() { assertEquals(288.74, PrayerTimes.qibla(10.7769, 106.7009), 0.5) }
}
