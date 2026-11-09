package com.dj.dot.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dj.dot.DotApp
import com.dj.dot.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class WrappedStats(
    val totalMinutes: Long,
    val since: Long,
    val topSongs: List<Pair<Song, Long>>,
    val topArtists: List<Triple<String, Song, Long>>,
    val hours: List<Long>,
    val week: List<Pair<String, Long>>,
    val streak: Int,
    val songsHeard: Int,
)

private fun listenerType(hours: List<Long>): Pair<String, String> {
    if (hours.all { it == 0L }) return "Just started" to "Keep listening"
    val peak = hours.indices.maxBy { hours[it] }
    return when (peak) {
        in 5..10 -> "Early bird" to "Most of your music happens in the morning"
        in 11..16 -> "Daydreamer" to "You soundtrack your afternoons"
        in 17..21 -> "Evening soul" to "Your music comes alive after sunset"
        else -> "Night owl" to "You're up late with the music on"
    }
}

@Composable
fun WrappedScreen() {
    val dot = DotApp.instance
    val app = LocalAppState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lib by dot.library.data.collectAsStateWithLifecycle()
    val listen by dot.store.listenMs.collectAsStateWithLifecycle()
    val daily by dot.store.dailyMs.collectAsStateWithLifecycle()
    val hours by dot.store.hourMs.collectAsStateWithLifecycle()
    val shareLayer = rememberGraphicsLayer()

    val stats = remember(lib, listen, daily, hours) {
        val songs = listen.entries.mapNotNull { (id, ms) -> lib.byId[id]?.let { it to ms } }.sortedByDescending { it.second }
        val artists = songs.groupBy { it.first.artist }
            .map { (name, list) -> Triple(name, list.first().first, list.sumOf { it.second }) }
            .sortedByDescending { it.third }
        val cal = Calendar.getInstance()
        val dayKey = { c: Calendar -> "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) }
        val dayName = SimpleDateFormat("EEE", Locale.getDefault())
        val week = (6 downTo 0).map { back ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -back) }
            dayName.format(c.time).take(1) to (daily[dayKey(c)] ?: 0L)
        }
        var streak = 0
        val c = cal.clone() as Calendar
        if ((daily[dayKey(c)] ?: 0L) < 60_000) c.add(Calendar.DAY_OF_YEAR, -1)
        while ((daily[dayKey(c)] ?: 0L) >= 60_000) { streak++; c.add(Calendar.DAY_OF_YEAR, -1) }
        WrappedStats(
            totalMinutes = listen.values.sum() / 60_000,
            since = dot.store.trackingSince,
            topSongs = songs.take(5),
            topArtists = artists.take(5),
            hours = hours,
            week = week,
            streak = streak,
            songsHeard = songs.size,
        )
    }

    fun share() = scope.launch {
        val bitmap = shareLayer.toImageBitmap().asAndroidBitmap()
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "dot-wrapped.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your Dot Wrapped"))
    }

    var showList by rememberSaveable { mutableStateOf(false) }
    if (showList || stats.totalMinutes < 1) WrappedList(stats, shareLayer, onShare = { share() }, onBack = app::pop)
    else WrappedStories(stats, shareLayer, onShare = { share() }, onAll = { showList = true }, onClose = app::pop)
}

/** Every stat on one scrolling page, with the shareable summary card on top. */
@Composable
private fun WrappedList(stats: WrappedStats, shareLayer: GraphicsLayer, onShare: () -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            TopBar("Wrapped", onBack) {
                if (stats.totalMinutes > 0) IconBtn(Icons.Rounded.Share, onShare, size = 22.dp)
            }
        }
        if (stats.totalMinutes < 1) {
            item { EmptyState("Warming up", "Play some music. Your stats build up as you listen.") }
            return@LazyColumn
        }
        item { ShareableSummary(stats, shareLayer, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        item { SectionCard("Top artists") { stats.topArtists.forEachIndexed { i, (name, song, ms) -> RankRow(i + 1, name, "${ms / 60_000} min", song, round = true) } } }
        item { SectionCard("Top songs") { stats.topSongs.forEachIndexed { i, (song, ms) -> RankRow(i + 1, song.title, "${song.artist} · ${ms / 60_000} min", song) } } }
        item { SectionCard("Your listening clock") { ClockChart(stats.hours) } }
        item { SectionCard("Last 7 days") { WeekChart(stats.week) } }
    }
}

/** The summary card, drawn into a layer so it can be snapshotted and shared as an image. */
@Composable
private fun ShareableSummary(stats: WrappedStats, shareLayer: GraphicsLayer, modifier: Modifier = Modifier) {
    Box(
        modifier.drawWithContent {
            shareLayer.record { this@drawWithContent.drawContent() }
            drawLayer(shareLayer)
        },
    ) { SummaryCard(stats) }
}

@Composable
private fun SummaryCard(stats: WrappedStats) {
    val (type, line) = listenerType(stats.hours)
    val since = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(stats.since))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Dot.Black)
            .border(1.dp, Dot.Line, RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotTitle("Dot", size = 22.sp)
            Box(Modifier.padding(start = 3.dp, top = 8.dp).size(5.dp).clip(CircleShape).background(Dot.Red))
            Spacer(Modifier.weight(1f))
            Label("Wrapped")
        }
        Spacer(Modifier.height(22.dp))
        DotTitle(NumberFormat.getIntegerInstance().format(stats.totalMinutes), size = 72.sp)
        Label("Minutes listened since $since", color = Dot.White)
        Spacer(Modifier.height(22.dp))
        DottedDivider()
        Spacer(Modifier.height(18.dp))
        Row {
            StatBlock("Top artist", stats.topArtists.firstOrNull()?.first ?: "—", Modifier.weight(1f))
            StatBlock("Top song", stats.topSongs.firstOrNull()?.first?.title ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row {
            StatBlock("Songs played", stats.songsHeard.toString(), Modifier.weight(1f))
            StatBlock("Day streak", if (stats.streak > 0) countOf(stats.streak, "day") else "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Dot.Red).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                DotTitle(type, size = 24.sp)
                Text(line, color = Dot.White.copy(alpha = 0.85f), style = MonoSmall)
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Label(label)
        Text(value, color = Dot.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)).background(Dot.Surface2).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Dot.Red))
            Spacer(Modifier.width(8.dp))
            Label(title, color = Dot.White)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun RankRow(rank: Int, title: String, subtitle: String, song: Song, round: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        DotTitle("$rank", Modifier.width(30.dp), size = 22.sp, color = if (rank == 1) Dot.Red else Dot.White)
        Art(song, Modifier.size(44.dp), px = 132, shape = if (round) CircleShape else RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Dot.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Dot.Muted, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 24 dot columns, one per hour; the busiest hour is red. */
@Composable
private fun ClockChart(hours: List<Long>) {
    val max = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val peak = hours.indices.maxBy { hours[it] }
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val rows = 10
        val cw = size.width / 24
        val rh = size.height / rows
        val r = minOf(cw, rh) * 0.3f
        for (h in 0 until 24) {
            val lit = ((hours[h].toFloat() / max) * rows).toInt().let { if (hours[h] > 0) it.coerceAtLeast(1) else 0 }
            for (row in 0 until rows) {
                val color = when {
                    row < lit && h == peak -> Dot.Red
                    row < lit -> Dot.White
                    else -> Dot.Faint
                }
                drawCircle(color, r, Offset(h * cw + cw / 2, size.height - (row * rh + rh / 2)))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        listOf("12AM", "6AM", "12PM", "6PM").forEach { Label(it, Modifier.weight(1f)) }
    }
}

/** Seven dot bars for the last week, today on the right. */
@Composable
private fun WeekChart(week: List<Pair<String, Long>>) {
    val max = (week.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, (day, ms) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("${ms / 60_000}", color = if (ms > 0) Dot.White else Dot.Muted)
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.size(width = 14.dp, height = 96.dp)) {
                    val rows = 8
                    val rh = size.height / rows
                    val lit = ((ms.toFloat() / max) * rows).toInt().let { if (ms > 0) it.coerceAtLeast(1) else 0 }
                    for (row in 0 until rows) {
                        drawCircle(
                            when { row < lit && i == week.lastIndex -> Dot.Red; row < lit -> Dot.White; else -> Dot.Faint },
                            size.width * 0.32f, Offset(size.width / 2, size.height - (row * rh + rh / 2)),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Label(day, color = if (i == week.lastIndex) Dot.Red else Dot.Muted)
            }
        }
    }
}

private enum class Story { Minutes, Artist, Song, Clock, Type, Summary }

/** One full-screen Wrapped highlight. The last one is the shareable card with a way into every stat. */
@Composable
private fun StoryCard(story: Story, stats: WrappedStats, shareLayer: GraphicsLayer, onShare: () -> Unit, onAll: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (story) {
            Story.Minutes -> {
                val since = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(stats.since))
                Label("Since $since you've played", color = Dot.White)
                Spacer(Modifier.height(16.dp))
                DotTitle(NumberFormat.getIntegerInstance().format(stats.totalMinutes), size = 96.sp, align = TextAlign.Center)
                Label("minutes of music, all of it offline", align = TextAlign.Center)
            }
            Story.Artist -> stats.topArtists.firstOrNull()?.let { (name, song, ms) ->
                Label("Your top artist", color = Dot.White)
                Spacer(Modifier.height(24.dp))
                Art(song, Modifier.size(220.dp), px = 660, shape = CircleShape)
                Spacer(Modifier.height(24.dp))
                DotTitle(name, size = 34.sp, align = TextAlign.Center, maxLines = 2)
                Label("${ms / 60_000} min together", align = TextAlign.Center)
            }
            Story.Song -> stats.topSongs.firstOrNull()?.let { (song, ms) ->
                Label("Your song on repeat", color = Dot.White)
                Spacer(Modifier.height(24.dp))
                Art(song, Modifier.size(240.dp), px = 720, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.height(24.dp))
                DotTitle(song.title, size = 30.sp, align = TextAlign.Center, maxLines = 2)
                Text(song.artist, color = Dot.Muted, style = SansSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Label("${ms / 60_000} min", align = TextAlign.Center)
            }
            Story.Clock -> {
                val peak = stats.hours.indices.maxBy { stats.hours[it] }
                val hour = "${if (peak % 12 == 0) 12 else peak % 12} ${if (peak < 12) "AM" else "PM"}"
                Label("Your listening clock", color = Dot.White)
                Spacer(Modifier.height(24.dp))
                ClockChart(stats.hours)
                Spacer(Modifier.height(24.dp))
                DotTitle("Around $hour", size = 34.sp, align = TextAlign.Center)
                Label("is when the music is on most", align = TextAlign.Center)
            }
            Story.Type -> {
                val (type, line) = listenerType(stats.hours)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Dot.Red).padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Label("You're a", color = Dot.White)
                    Spacer(Modifier.height(12.dp))
                    DotTitle(type, size = 44.sp, align = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Text(line, color = Dot.White.copy(alpha = 0.85f), style = MonoSmall, textAlign = TextAlign.Center)
                }
            }
            Story.Summary -> {
                ShareableSummary(stats, shareLayer)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Share", onShare, accent = true, icon = Icons.Rounded.Share)
                    Pill("All stats", onAll)
                }
            }
        }
    }
}

private const val STORY_MS = 5_000

/**
 * Wrapped as tap-through stories: tap the right of the screen for the next card, the left for the
 * one before. Each card moves on by itself after a few seconds; the last one waits.
 */
@Composable
private fun WrappedStories(stats: WrappedStats, shareLayer: GraphicsLayer, onShare: () -> Unit, onAll: () -> Unit, onClose: () -> Unit) {
    val stories = remember(stats) {
        Story.entries.filter { (it != Story.Artist || stats.topArtists.isNotEmpty()) && (it != Story.Song || stats.topSongs.isNotEmpty()) }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(STORY_MS, easing = LinearEasing))
        if (index < stories.lastIndex) index++
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Dot.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(stories.size) {
                detectTapGestures { tap ->
                    if (tap.x < size.width / 3f) index = (index - 1).coerceAtLeast(0)
                    else if (index < stories.lastIndex) index++
                }
            },
    ) {
        AnimatedContent(
            stories[index.coerceAtMost(stories.lastIndex)],
            transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.94f)) togetherWith fadeOut(tween(150)) },
            label = "story",
        ) { story -> StoryCard(story, stats, shareLayer, onShare, onAll) }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryProgress(stories.size, index, { progress.value }, Modifier.weight(1f))
            IconBtn(Icons.Rounded.Close, onClose, description = "Close")
        }
    }
}

/** One dotted segment per card: past cards full, the current one filling, the rest faint. */
@Composable
private fun StoryProgress(count: Int, current: Int, progress: () -> Float, modifier: Modifier) {
    Canvas(modifier.height(8.dp)) {
        val gap = 6.dp.toPx()
        val segment = (size.width - gap * (count - 1)) / count
        val step = 5.dp.toPx()
        val dots = (segment / step).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val lit = when { i < current -> 1f; i == current -> progress(); else -> 0f }
            for (d in 0 until dots) {
                val on = (d + 0.5f) / dots <= lit
                drawCircle(if (on) Dot.White else Dot.Faint, 1.4.dp.toPx(), Offset(i * (segment + gap) + d * step + step / 2, size.height / 2))
            }
        }
    }
}
