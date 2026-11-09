package com.isaedu.quran

import kotlin.math.*

/** Phương pháp tính giờ Solah (chỉ các phương pháp Sunni phổ biến). ishaMinutes > 0: Isha = Maghrib + số phút. */
enum class Method(val label: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0) {
    MWL("Muslim World League", 18.0, 17.0),
    JAKIM("JAKIM – Malaysia", 20.0, 18.0),
    KEMENAG("Kemenag – Indonesia", 20.0, 18.0),
    MUIS("MUIS – Singapore", 20.0, 18.0),
    EGYPT("Egyptian General Authority", 19.5, 17.5),
    KARACHI("Đại học Karachi", 18.0, 18.0),
    ISNA("ISNA – Bắc Mỹ", 15.0, 15.0),
    MAKKAH("Umm al-Qura – Makkah", 18.5, 0.0, 90),
}

/** Asr: Shafi'i/Maliki/Hanbali (bóng = 1 lần) hoặc Hanafi (bóng = 2 lần). */
enum class AsrMadhhab(val label: String, val factor: Int) { SHAFII("Shafi'i, Maliki, Hanbali", 1), HANAFI("Hanafi", 2) }

enum class Prayer(val vi: String, val ar: String) {
    FAJR("Fajr (Subuh)", "الفجر"), SUNRISE("Mặt trời mọc", "الشروق"), DHUHR("Dhuhr (Zuhur)", "الظهر"),
    ASR("Asr", "العصر"), MAGHRIB("Maghrib", "المغرب"), ISHA("Isha", "العشاء");
    val isSolah get() = this != SUNRISE
}

/**
 * Tính giờ Solah theo thuật toán thiên văn chuẩn (như PrayTimes.org):
 * vị trí Mặt Trời → góc dưới đường chân trời (Fajr/Isha), bóng nắng (Asr), giữa trưa (Dhuhr), lặn (Maghrib).
 * Kết quả: số giờ thập phân theo giờ địa phương (tz = chênh lệch giờ so với UTC).
 */
object PrayerTimes {
    private fun d2r(d: Double) = d * PI / 180
    private fun r2d(r: Double) = r * 180 / PI
    private fun fix(a: Double, b: Double): Double { val x = a - b * floor(a / b); return if (x < 0) x + b else x }

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0); val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** trả về (độ xích vĩ, phương trình thời gian) */
    private fun sun(jd: Double): Pair<Double, Double> {
        val dd = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * dd, 360.0)
        val q = fix(280.459 + 0.98564736 * dd, 360.0)
        val l = fix(q + 1.915 * sin(d2r(g)) + 0.020 * sin(d2r(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * dd
        val ra = fix(r2d(atan2(cos(d2r(e)) * sin(d2r(l)), cos(d2r(l)))) / 15, 24.0)
        val decl = r2d(asin(sin(d2r(e)) * sin(d2r(l))))
        return decl to (q / 15 - ra)
    }

    fun compute(y: Int, m: Int, d: Int, lat: Double, lng: Double, tz: Double,
                method: Method, asr: AsrMadhhab, adjust: Map<Prayer, Int> = emptyMap()): Map<Prayer, Double> {
        val jd = julian(y, m, d) - lng / (15 * 24)
        fun mid(t: Double) = fix(12 - sun(jd + t).second, 24.0)
        fun angle(a: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t).first
            val c = (-sin(d2r(a)) - sin(d2r(decl)) * sin(d2r(lat))) / (cos(d2r(decl)) * cos(d2r(lat)))
            val tt = r2d(acos(c.coerceIn(-1.0, 1.0))) / 15
            return mid(t) + if (ccw) -tt else tt
        }
        fun asrTime(f: Int, t: Double): Double {
            val decl = sun(jd + t).first
            val a = -r2d(atan(1 / (f + tan(d2r(abs(lat - decl))))))
            return angle(a, t, false)
        }
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0).map { it / 24 }
        var r = DoubleArray(6)
        repeat(2) {
            r = doubleArrayOf(
                angle(method.fajr, t[0], true), angle(0.833, t[1], true), mid(t[2]),
                asrTime(asr.factor, t[3]), angle(0.833, t[4], false),
                if (method.ishaMinutes > 0) 0.0 else angle(method.isha, t[5], false))
            t = r.map { it / 24 }
        }
        val off = tz - lng / 15
        val out = linkedMapOf<Prayer, Double>()
        out[Prayer.FAJR] = r[0] + off; out[Prayer.SUNRISE] = r[1] + off; out[Prayer.DHUHR] = r[2] + off
        out[Prayer.ASR] = r[3] + off; out[Prayer.MAGHRIB] = r[4] + off
        out[Prayer.ISHA] = if (method.ishaMinutes > 0) r[4] + off + method.ishaMinutes / 60.0 else r[5] + off
        for ((p, v) in out) out[p] = v + (adjust[p] ?: 0) / 60.0
        return out
    }

    /** giờ thập phân → số phút trong ngày (làm tròn) */
    fun minutes(h: Double): Int = (h * 60).roundToInt()
    fun hhmm(h: Double): String { val mm = ((minutes(h) % 1440) + 1440) % 1440; return "%02d:%02d".format(mm / 60, mm % 60) }

    /** hướng Qibla (độ, theo chiều kim đồng hồ từ hướng Bắc thật) */
    fun qibla(lat: Double, lng: Double): Double {
        val kLat = d2r(21.4225); val kLng = d2r(39.8262); val p = d2r(lat); val dl = kLng - d2r(lng)
        return fix(r2d(atan2(sin(dl), cos(p) * tan(kLat) - sin(p) * cos(dl))), 360.0)
    }
}
