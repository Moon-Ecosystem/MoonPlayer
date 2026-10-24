package com.example.data.stats

import com.example.data.db.HistoryEntity
import java.util.Calendar
import java.util.TimeZone

/** The period the stats page covers; [days] null means everything recorded. */
enum class StatsRange(val label: String, val days: Int?) {
    WEEK("7D", 7),
    MONTH("30D", 30),
    YEAR("YEAR", 365),
    ALL("ALL", null);

    /** Start of the range: midnight [days] - 1 days before [now], so today counts as a full day. */
    fun since(now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val d = days ?: return 0L
        return startOfDay(now, zone).apply { add(Calendar.DAY_OF_YEAR, -(d - 1)) }.timeInMillis
    }
}

enum class StatsKind(val label: String) { ARTIST("ARTIST"), ALBUM("ALBUM"), TRACK("SONG"), PLAYLIST("PLAYLIST") }

/** An artist, album, playlist or song with its plays and time actually listened. */
data class Ranked(
    val name: String,
    val detail: String,
    val plays: Int,
    val listenedMs: Long,
    // Songs only: plays that reached the end (80%+) and plays given up early.
    val finished: Int = 0,
    val skipped: Int = 0,
    // What picks its plays out of history: the name, or the track id for songs.
    val key: String = name
)

/** One artist, album, song or playlist's own stats, for its detail page. */
data class StatsDetail(
    val kind: StatsKind,
    val key: String,
    // The usual breakdowns, worked out from this item's plays only.
    val stats: ListeningStats,
    // All listening in the range, for this item's share of it.
    val allListenedMs: Long,
    val firstPlayed: Long?,
    val lastPlayed: Long?,
    // Plays whose time is known (not recorded before time was tracked), for the average.
    val timedPlays: Int
)

fun detailStats(
    rows: List<HistoryEntity>,
    kind: StatsKind,
    key: String,
    range: StatsRange,
    now: Long,
    zone: TimeZone = TimeZone.getDefault()
): StatsDetail {
    val mine = rows.filter {
        when (kind) {
            StatsKind.ARTIST -> it.artist == key
            StatsKind.ALBUM -> it.album == key
            StatsKind.TRACK -> it.trackId == key
            StatsKind.PLAYLIST -> it.playlist == key
        }
    }
    return StatsDetail(
        kind = kind,
        key = key,
        stats = listeningStats(mine, range, now, zone),
        allListenedMs = rows.sumOf { it.listenedMs.coerceAtLeast(0L) },
        firstPlayed = mine.minOfOrNull { it.playedAt },
        lastPlayed = mine.maxOfOrNull { it.playedAt },
        timedPlays = mine.count { it.listenedMs >= 0 }
    )
}

data class Bar(val label: String, val ms: Long)

data class ListeningStats(
    val plays: Int,
    val listenedMs: Long,
    val artists: List<Ranked>,
    val albums: List<Ranked>,
    val tracks: List<Ranked>,
    val playlists: List<Ranked>,
    // Listening per day (7D, 30D) or per month (YEAR, ALL).
    val trend: List<Bar>,
    val trendPerMonth: Boolean,
    val byHour: List<Bar>,
    val byWeekday: List<Bar>
)

private const val SKIP_MS = 30_000L

/**
 * Works out the stats page from the plays in the range. Time is what was actually played;
 * plays from before that was recorded (listenedMs -1) count as plays with no time.
 *
 * A play is finished at 80% of the song, and skipped when stopped before 30 s or half the song,
 * whichever is shorter. Plays that may still be going (started less than a song's length ago)
 * are left out of both, so the song playing now isn't counted as a skip.
 */
fun listeningStats(
    rows: List<HistoryEntity>,
    range: StatsRange,
    now: Long,
    zone: TimeZone = TimeZone.getDefault()
): ListeningStats {
    fun HistoryEntity.time() = listenedMs.coerceAtLeast(0L)

    fun rank(key: (HistoryEntity) -> String?, detail: (List<HistoryEntity>) -> String): List<Ranked> =
        rows.filter { !key(it).isNullOrBlank() }
            .groupBy { key(it)!! }
            .map { (name, plays) -> Ranked(name, detail(plays), plays.size, plays.sumOf { it.time() }) }
            .sortedWith(compareByDescending<Ranked> { it.listenedMs }.thenByDescending { it.plays })

    fun mostCommon(plays: List<HistoryEntity>, field: (HistoryEntity) -> String) =
        plays.groupingBy(field).eachCount().maxByOrNull { it.value }?.key.orEmpty()

    val tracks = rows.groupBy { it.trackId }.map { (_, plays) ->
        val known = plays.filter { it.listenedMs >= 0 && it.durationMs > 0 && it.playedAt + it.durationMs <= now }
        Ranked(
            name = plays.last().title,
            detail = plays.last().artist,
            plays = plays.size,
            listenedMs = plays.sumOf { it.time() },
            finished = known.count { it.listenedMs >= it.durationMs * 8 / 10 },
            skipped = known.count { it.listenedMs < minOf(SKIP_MS, it.durationMs / 2) },
            key = plays.last().trackId
        )
    }.sortedWith(compareByDescending<Ranked> { it.plays }.thenByDescending { it.listenedMs })

    val cal = Calendar.getInstance(zone)
    fun fieldOf(at: Long, field: Int): Int = cal.apply { timeInMillis = at }.get(field)

    val trendPerMonth = range == StatsRange.YEAR || range == StatsRange.ALL
    val trend = if (trendPerMonth) {
        // Months from the range's start (or the first play) to now: 12 for YEAR, at most 24 for ALL.
        val first = if (range == StatsRange.ALL) rows.firstOrNull()?.playedAt ?: now else range.since(now, zone)
        val months = monthsBetween(first, now, zone).takeLast(if (range == StatsRange.YEAR) 12 else 24)
        months.map { start ->
            val end = Calendar.getInstance(zone).apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis
            Bar(monthLetter(fieldOf(start, Calendar.MONTH)), rows.filter { it.playedAt in start until end }.sumOf { it.time() })
        }
    } else {
        val days = range.days ?: 7
        (0 until days).map { i ->
            val start = startOfDay(now, zone).apply { add(Calendar.DAY_OF_YEAR, -(days - 1 - i)) }
            val dayStart = start.timeInMillis
            val label = if (days <= 7) weekdayLetter(start.get(Calendar.DAY_OF_WEEK)) else start.get(Calendar.DAY_OF_MONTH).toString()
            start.add(Calendar.DAY_OF_YEAR, 1)
            Bar(label, rows.filter { it.playedAt in dayStart until start.timeInMillis }.sumOf { it.time() })
        }
    }

    val hourMs = LongArray(24)
    val weekdayMs = LongArray(7) // Monday first
    rows.forEach {
        hourMs[fieldOf(it.playedAt, Calendar.HOUR_OF_DAY)] += it.time()
        weekdayMs[(fieldOf(it.playedAt, Calendar.DAY_OF_WEEK) + 5) % 7] += it.time()
    }

    return ListeningStats(
        plays = rows.size,
        listenedMs = rows.sumOf { it.time() },
        artists = rank({ it.artist }) { "" },
        albums = rank({ it.album }) { plays -> mostCommon(plays) { it.artist } },
        tracks = tracks,
        playlists = rank({ it.playlist }) { "" },
        trend = trend,
        trendPerMonth = trendPerMonth,
        byHour = hourMs.mapIndexed { h, ms -> Bar(h.toString(), ms) },
        byWeekday = weekdayMs.mapIndexed { i, ms -> Bar("MTWTFSS"[i].toString(), ms) }
    )
}

private fun startOfDay(at: Long, zone: TimeZone): Calendar = Calendar.getInstance(zone).apply {
    timeInMillis = at
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/** Start of each month from the one holding [from] to the one holding [to]. */
private fun monthsBetween(from: Long, to: Long, zone: TimeZone): List<Long> {
    val month = startOfDay(from, zone).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val starts = mutableListOf<Long>()
    while (month.timeInMillis <= to) {
        starts += month.timeInMillis
        month.add(Calendar.MONTH, 1)
    }
    return starts
}

private fun monthLetter(month: Int) = "JFMAMJJASOND"[month].toString()

// Calendar.SUNDAY is 1.
private fun weekdayLetter(dayOfWeek: Int) = "SMTWTFS"[dayOfWeek - 1].toString()
