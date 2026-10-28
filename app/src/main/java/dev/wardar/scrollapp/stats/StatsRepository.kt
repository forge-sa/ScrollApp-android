package dev.wardar.scrollapp.stats

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.wardar.scrollapp.Platform
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.Executors

data class DayStat(
    val day: LocalDate,
    val platform: Platform,
    val watchMs: Long,
    val videos: Int,
    val adsSkipped: Int,
)

/**
 * Per-day, per-platform totals. Writes and reads share one thread, so a read always sees
 * every write queued before it.
 */
class StatsRepository(context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val helper = Helper(context.applicationContext)

    fun addWatchTime(platform: Platform, ms: Long) = add(platform, watchMs = ms)
    fun addVideo(platform: Platform) = add(platform, videos = 1)
    fun addAdSkipped(platform: Platform) = add(platform, adsSkipped = 1)

    private fun add(platform: Platform, watchMs: Long = 0, videos: Int = 0, adsSkipped: Int = 0) {
        val day = LocalDate.now().toString()
        executor.execute {
            val db = helper.writableDatabase
            db.execSQL("INSERT OR IGNORE INTO daily(day, platform) VALUES (?, ?)", arrayOf(day, platform.name))
            db.execSQL(
                "UPDATE daily SET watch_ms = watch_ms + ?, videos = videos + ?, ads_skipped = ads_skipped + ? " +
                    "WHERE day = ? AND platform = ?",
                arrayOf<Any>(watchMs, videos, adsSkipped, day, platform.name),
            )
        }
    }

    suspend fun load(): List<DayStat> = withContext(dispatcher) {
        helper.readableDatabase.rawQuery(
            "SELECT day, platform, watch_ms, videos, ads_skipped FROM daily ORDER BY day", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val platform = Platform.entries.firstOrNull { it.name == c.getString(1) } ?: continue
                    add(DayStat(LocalDate.parse(c.getString(0)), platform, c.getLong(2), c.getInt(3), c.getInt(4)))
                }
            }
        }
    }

    suspend fun clear() = withContext(dispatcher) {
        helper.writableDatabase.delete("daily", null, null)
        Unit
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "stats.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE daily (" +
                    "day TEXT NOT NULL, platform TEXT NOT NULL, " +
                    "watch_ms INTEGER NOT NULL DEFAULT 0, videos INTEGER NOT NULL DEFAULT 0, " +
                    "ads_skipped INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (day, platform))",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
