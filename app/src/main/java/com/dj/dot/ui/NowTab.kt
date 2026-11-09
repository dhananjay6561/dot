package com.dj.dot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dj.dot.DotApp
import com.dj.dot.data.Song

private val mixes = listOf(
    Triple(SMART_CHILL, "Chill", DotIcons.Chill),
    Triple(SMART_GROOVE, "Groove", DotIcons.Groove),
    Triple(SMART_HYPE, "Hype", DotIcons.Hype),
)

/** The first library page: what's next, the mood mixes, what you play most and what's new. */
@Composable
fun NowTab() {
    val dot = DotApp.instance
    val app = LocalAppState.current
    val lib by dot.library.data.collectAsStateWithLifecycle()
    val state by dot.engine.state.collectAsStateWithLifecycle()
    val counts by dot.store.playCounts.collectAsStateWithLifecycle()
    val tempo by dot.store.bpm.collectAsStateWithLifecycle()
    val listenMs by dot.store.listenMs.collectAsStateWithLifecycle()

    val upNext = remember(state.queue, state.index) { state.queue.drop(state.index + 1).take(12) }
    val mixSongs = remember(lib, tempo) { mixes.associate { (id) -> id to moodSongs(lib, tempo, id) } }
    val top = remember(lib, counts) { topSongs(lib, counts, limit = 12) }
    val recent = remember(lib) { recentSongs(lib, limit = 12) }
    val minutes = remember(listenMs) { listenMs.values.sum() / 60_000 }

    when {
        !lib.ready -> EmptyState("Scanning", "Looking for music on your phone")
        lib.songs.isEmpty() -> EmptyState("No music", "Copy songs into the Music folder and they'll show up here.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (upNext.isNotEmpty()) {
                item { SectionHeader("Up next") { app.nowPlaying = true } }
                item {
                    SongCards(upNext) { i -> dot.engine.jumpTo(state.index + 1 + i) }
                }
            }
            item { SectionHeader("Your mixes · learned as you listen") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mixes.size) { i ->
                        val (id, name, glyph) = mixes[i]
                        MixCard(name, glyph, mixSongs[id].orEmpty()) { app.push(Screen.Playlist(id)) }
                    }
                }
            }
            if (top.isNotEmpty()) {
                item { SectionHeader("Most played") { app.push(Screen.Playlist(SMART_TOP)) } }
                item { SongCards(top) { i -> dot.engine.playSongs(top, i) } }
            }
            item { SectionHeader("Recently added") { app.push(Screen.Playlist(SMART_RECENT)) } }
            item { SongCards(recent) { i -> dot.engine.playSongs(recent, i) } }
            if (minutes > 0) item { WrappedStrip(minutes, counts.count { it.value > 0 }) { app.push(Screen.Wrapped) } }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(title, Modifier.weight(1f))
        if (onAll != null) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Label("All", color = Dot.White)
            }
        }
    }
}

@Composable
private fun SongCards(songs: List<Song>, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, song ->
            Column(Modifier.width(128.dp).clip(RoundedCornerShape(14.dp)).clickable { onPlay(i) }) {
                Art(song, Modifier.size(128.dp), px = 384, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                Text(song.title, color = Dot.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Dot.Muted, style = SansSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A tall card for a mood mix: a 2x2 cover mosaic once it has four albums, its dot icon until then. */
@Composable
private fun MixCard(name: String, glyph: DotGlyph, songs: List<Song>, onOpen: () -> Unit) {
    val covers = remember(songs) { songs.distinctBy { it.albumId }.take(4) }
    Column(Modifier.width(140.dp).clip(RoundedCornerShape(18.dp)).background(Dot.Surface2).clickable(onClick = onOpen).padding(12.dp)) {
        Box(Modifier.size(116.dp).clip(RoundedCornerShape(12.dp)).background(Dot.Surface), contentAlignment = Alignment.Center) {
            if (covers.size == 4) {
                Column {
                    for (row in 0..1) Row { for (col in 0..1) Art(covers[row * 2 + col], Modifier.size(58.dp), px = 192, shape = RoundedCornerShape(0.dp)) }
                }
            } else {
                DotIcon(glyph, size = 44.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        DotTitle(name, size = 22.sp)
        Label(if (songs.isEmpty()) "Learning" else "${songs.size} songs")
    }
}

@Composable
private fun WrappedStrip(minutes: Long, songsPlayed: Int, onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Dot.Surface2).clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotIcon(DotIcons.Wrapped, size = 28.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            DotTitle("$minutes min", size = 24.sp)
            Label("$songsPlayed songs played · your Wrapped")
        }
    }
}
