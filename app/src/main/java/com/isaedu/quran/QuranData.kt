package com.isaedu.quran

import android.content.Context

data class Sura(val index: Int, val ayas: Int, val ar: String, val name: String, val en: String, val meccan: Boolean)
data class Aya(val sura: Int, val aya: Int, val text: String, val vi: String)

/** Dữ liệu Quran trong assets (tải lúc đóng gói): văn bản Uthmani của Tanzil, bản dịch tiếng Việt của Rowwad (QuranEnc). */
object QuranData {
    const val BISMILLAH = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ"
    lateinit var suras: List<Sura>; private set
    private lateinit var text: Map<Int, List<String>>
    private lateinit var vi: Map<Int, Map<Int, String>>
    @Volatile var loaded = false; private set

    @Synchronized fun load(ctx: Context) {
        if (loaded) return
        suras = ctx.assets.open("suras.tsv").bufferedReader().readLines().filter { it.isNotBlank() }.map {
            val f = it.split("\t"); Sura(f[0].toInt(), f[1].toInt(), f[2], f[3], f[4], f[5] == "Meccan")
        }
        val t = HashMap<Int, MutableList<String>>()
        ctx.assets.open("quran.txt").bufferedReader().forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val f = line.split("|", limit = 3); if (f.size < 3) return@forEachLine
            t.getOrPut(f[0].toInt()) { mutableListOf() }.add(f[2])
        }
        text = t
        val v = HashMap<Int, HashMap<Int, String>>()
        runCatching {
            ctx.assets.open("vi.txt").bufferedReader().forEachLine { line ->
                val f = line.split("|", limit = 3); if (f.size == 3) v.getOrPut(f[0].toInt()) { HashMap() }[f[1].toInt()] = f[2]
            }
        }
        vi = v; loaded = true
    }

    fun sura(i: Int) = suras[i - 1]
    fun ayas(s: Int): List<Aya> = text[s].orEmpty().mapIndexed { i, tx -> Aya(s, i + 1, tx, vi[s]?.get(i + 1).orEmpty()) }
    fun aya(s: Int, a: Int) = Aya(s, a, text[s]?.getOrNull(a - 1).orEmpty(), vi[s]?.get(a).orEmpty())

    /** tách Bismillah ở đầu āyah 1 (mọi sura trừ Al-Fātiḥah và At-Tawbah) để hiển thị riêng */
    fun splitBismillah(a: Aya): Pair<String?, String> {
        if (a.aya != 1 || a.sura == 1 || a.sura == 9) return null to a.text
        val w = a.text.split(" ")
        return if (w.size > 4 && w[0].startsWith("بِسْمِ")) w.take(4).joinToString(" ") to w.drop(4).joinToString(" ") else null to a.text
    }
}
