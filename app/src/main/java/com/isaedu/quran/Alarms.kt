package com.isaedu.quran

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** Đặt lịch nhắc giờ Solah, nhắc trước vài phút và nhắc ôn học thuộc. Mỗi lần chuông kêu sẽ tự đặt lại lịch kế tiếp. */
object Alarms {
    const val CH_SOLAH = "solah"; const val CH_PRE = "solah_pre"; const val CH_HIFZ = "hifz"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SOLAH, "Giờ Solah", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_PRE, "Nhắc trước giờ Solah", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_HIFZ, "Nhắc ôn học thuộc", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** giờ Solah của một ngày, theo cài đặt hiện tại */
    fun timesFor(st: Store, date: LocalDate): Map<Prayer, Double> {
        val zone = TimeZone.getDefault()
        val ms = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3600_000L
        val tz = zone.getOffset(ms) / 3600_000.0
        return PrayerTimes.compute(date.year, date.monthValue, date.dayOfMonth, st.lat, st.lng, tz, st.method, st.asr, st.adjustments())
    }

    private fun millis(date: LocalDate, minuteOfDay: Int): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + minuteOfDay * 60_000L

    /** lần kế tiếp (sau thời điểm hiện tại) của từng loại nhắc */
    fun upcoming(st: Store): List<Triple<Int, Long, String>> {
        val now = System.currentTimeMillis(); val today = LocalDate.now(); val out = mutableListOf<Triple<Int, Long, String>>()
        for (pr in Prayer.values()) {
            if (!st.notify(pr)) continue
            for (k in 0..1) {
                val d = today.plusDays(k.toLong()); val mins = PrayerTimes.minutes(timesFor(st, d)[pr]!!)
                val at = millis(d, mins)
                if (at > now) { out += Triple(pr.ordinal, at, "${pr.name}|${PrayerTimes.hhmm(mins / 60.0)}"); break }
            }
            if (pr.isSolah && pr != Prayer.SUNRISE && st.preMinutes > 0) for (k in 0..1) {
                val d = today.plusDays(k.toLong()); val mins = PrayerTimes.minutes(timesFor(st, d)[pr]!!)
                val at = millis(d, mins - st.preMinutes)
                if (at > now) { out += Triple(100 + pr.ordinal, at, "PRE|${pr.name}|${PrayerTimes.hhmm(mins / 60.0)}"); break }
            }
        }
        if (st.hifzReminder) for (k in 0..1) {
            val at = millis(today.plusDays(k.toLong()), st.hifzTime)
            if (at > now) { out += Triple(900, at, "HIFZ"); break }
        }
        return out
    }

    fun scheduleAll(ctx: Context) {
        val st = Store(ctx); val am = ctx.getSystemService(AlarmManager::class.java)
        val codes = Prayer.values().flatMap { listOf(it.ordinal, 100 + it.ordinal) } + 900
        for (c in codes) am.cancel(pi(ctx, c, ""))
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        for ((code, at, tag) in upcoming(st)) {
            val p = pi(ctx, code, tag)
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    private fun pi(ctx: Context, code: Int, tag: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, AlarmReceiver::class.java).putExtra("tag", tag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun show(ctx: Context, id: Int, channel: String, title: String, text: String) {
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channel).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open)
            .setPriority(if (channel == CH_SOLAH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.channels(ctx)
        val tag = intent.getStringExtra("tag").orEmpty(); val f = tag.split("|")
        when {
            f[0] == "HIFZ" -> {
                val n = Store(ctx).due().size
                Alarms.show(ctx, 900, Alarms.CH_HIFZ, "📖 Đến giờ ôn Quran",
                    if (n > 0) "Hôm nay có $n āyah cần ôn lại. Mở app → Học thuộc → Ôn hôm nay." else "Dành vài phút học thêm một āyah mới nhé.")
            }
            f[0] == "PRE" && f.size >= 3 -> runCatching { Prayer.valueOf(f[1]) }.getOrNull()?.let { pr ->
                Alarms.show(ctx, 100 + pr.ordinal, Alarms.CH_PRE, "⏳ Sắp đến giờ ${pr.vi}", "${pr.vi} lúc ${f[2]} – còn ${Store(ctx).preMinutes} phút.")
            }
            f.size >= 2 -> runCatching { Prayer.valueOf(f[0]) }.getOrNull()?.let { pr ->
                if (pr == Prayer.SUNRISE) Alarms.show(ctx, pr.ordinal, Alarms.CH_PRE, "🌅 Mặt trời mọc ${f[1]}", "Đã hết giờ Fajr.")
                else Alarms.show(ctx, pr.ordinal, Alarms.CH_SOLAH, "🕌 Đến giờ ${pr.vi} ${pr.ar}", "${f[1]} · ${Store(ctx).place}")
            }
        }
        Alarms.scheduleAll(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) { Alarms.channels(ctx); Alarms.scheduleAll(ctx) }
}
