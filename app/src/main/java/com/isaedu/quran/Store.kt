package com.isaedu.quran

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate

/** Cài đặt + tiến độ học thuộc, lưu trong SharedPreferences (chỉ trên máy). */
class Store(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("quran", Context.MODE_PRIVATE)

    var lat: Double get() = p.getString("lat", "10.7769")!!.toDouble(); set(v) = p.edit().putString("lat", v.toString()).apply()
    var lng: Double get() = p.getString("lng", "106.7009")!!.toDouble(); set(v) = p.edit().putString("lng", v.toString()).apply()
    var place: String get() = p.getString("place", "TP. Hồ Chí Minh")!!; set(v) = p.edit().putString("place", v).apply()
    var method: Method get() = runCatching { Method.valueOf(p.getString("method", "MWL")!!) }.getOrDefault(Method.MWL)
        set(v) = p.edit().putString("method", v.name).apply()
    var asr: AsrMadhhab get() = runCatching { AsrMadhhab.valueOf(p.getString("asr", "SHAFII")!!) }.getOrDefault(AsrMadhhab.SHAFII)
        set(v) = p.edit().putString("asr", v.name).apply()
    fun adjust(pr: Prayer) = p.getInt("adj_" + pr.name, 0)
    fun setAdjust(pr: Prayer, v: Int) = p.edit().putInt("adj_" + pr.name, v).apply()
    fun adjustments() = Prayer.values().associateWith { adjust(it) }
    fun notify(pr: Prayer) = p.getBoolean("ntf_" + pr.name, pr.isSolah)
    fun setNotify(pr: Prayer, v: Boolean) = p.edit().putBoolean("ntf_" + pr.name, v).apply()
    var preMinutes: Int get() = p.getInt("pre", 10); set(v) = p.edit().putInt("pre", v).apply()
    var hifzReminder: Boolean get() = p.getBoolean("hifz_on", true); set(v) = p.edit().putBoolean("hifz_on", v).apply()
    var hifzTime: Int get() = p.getInt("hifz_time", 20 * 60); set(v) = p.edit().putInt("hifz_time", v).apply()   // phút trong ngày

    // học thuộc
    var repeat: Int get() = p.getInt("repeat", 3); set(v) = p.edit().putInt("repeat", v).apply()
    var speed: Float get() = p.getFloat("speed", 1f); set(v) = p.edit().putFloat("speed", v).apply()
    var hideText: Boolean get() = p.getBoolean("hide", false); set(v) = p.edit().putBoolean("hide", v).apply()
    var showVi: Boolean get() = p.getBoolean("vi", true); set(v) = p.edit().putBoolean("vi", v).apply()
    var hifzSura: Int get() = p.getInt("h_sura", 114); set(v) = p.edit().putInt("h_sura", v).apply()
    var hifzFrom: Int get() = p.getInt("h_from", 1); set(v) = p.edit().putInt("h_from", v).apply()
    var hifzTo: Int get() = p.getInt("h_to", 6); set(v) = p.edit().putInt("h_to", v).apply()
    var lastSura: Int get() = p.getInt("last_sura", 1); set(v) = p.edit().putInt("last_sura", v).apply()
    var lastAya: Int get() = p.getInt("last_aya", 1); set(v) = p.edit().putInt("last_aya", v).apply()

    /** tiến độ: "sura:aya" → [mức ôn, ngày đến hạn (epochDay)] */
    private fun mem(): JSONObject = JSONObject(p.getString("mem", "{}")!!)
    private fun saveMem(o: JSONObject) = p.edit().putString("mem", o.toString()).apply()
    fun isMemorized(s: Int, a: Int) = mem().has("$s:$a")
    fun memorizedCount(s: Int): Int { val o = mem(); return o.keys().asSequence().count { it.startsWith("$s:") } }
    fun totalMemorized() = mem().length()
    fun markMemorized(s: Int, a: Int, on: Boolean) {
        val o = mem()
        if (on) o.put("$s:$a", "0,${LocalDate.now().toEpochDay() + 1}") else o.remove("$s:$a")
        saveMem(o)
    }
    /** các āyah đến hạn ôn hôm nay (đã sắp xếp theo thứ tự Quran) */
    fun due(): List<Pair<Int, Int>> {
        val o = mem(); val today = LocalDate.now().toEpochDay()
        return o.keys().asSequence().filter { o.getString(it).split(",")[1].toLong() <= today }
            .map { val (s, a) = it.split(":"); s.toInt() to a.toInt() }.sortedWith(compareBy({ it.first }, { it.second })).toList()
    }
    /** ôn xong: nhớ → lên mức (giãn ngày ôn), quên → về mức 0 (ôn lại ngày mai) */
    fun review(s: Int, a: Int, remembered: Boolean) {
        val o = mem(); val k = "$s:$a"; if (!o.has(k)) return
        val stage = o.getString(k).split(",")[0].toInt()
        val ns = if (remembered) (stage + 1).coerceAtMost(INTERVALS.size - 1) else 0
        o.put(k, "$ns,${LocalDate.now().toEpochDay() + INTERVALS[ns]}"); saveMem(o)
    }
    companion object { val INTERVALS = intArrayOf(1, 3, 7, 14, 30, 60, 120) }
}
