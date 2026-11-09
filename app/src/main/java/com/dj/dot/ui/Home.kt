package com.dj.dot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dj.dot.DotApp
import com.dj.dot.data.LibraryData
import com.dj.dot.data.Song
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val tabs = listOf("Now", "Songs", "Albums", "Artists", "Folders", "Playlists")
private val sortNames = listOf("Title", "Artist", "Recent")

@Composable
fun HomeScreen() {
    val app = LocalAppState.current
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                DotTitle("Dot", size = 40.sp)
                Box(Modifier.padding(start = 4.dp, bottom = 11.dp).size(8.dp).clip(CircleShape).background(Dot.Red))
            }
            DotIconBtn(DotIcons.Wrapped, { app.push(Screen.Wrapped) }, size = 22.dp)
            DotIconBtn(DotIcons.Search, { app.push(Screen.Search) }, size = 22.dp)
            DotIconBtn(DotIcons.Settings, { app.push(Screen.Settings) }, size = 22.dp)
        }
        val tabRow = rememberLazyListState()
        // Five pills don't fit a narrow phone, so keep the selected one and its neighbour in view.
        LaunchedEffect(pager.currentPage) { tabRow.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0)) }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        LaunchedEffect(pager) {
            androidx.compose.runtime.snapshotFlow { pager.currentPage }.drop(1).collect { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick) }
        }
        LazyRow(
            state = tabRow,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp).fadingEdges(tabRow).tabIndicator(tabRow, pager),
        ) {
            items(tabs.size) { i ->
                Pill(tabs[i], { scope.launch { pager.animateScrollToPage(i) } }, selected = pager.currentPage == i, filled = false)
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), key = { it }) { page ->
            when (page) {
                0 -> NowTab()
                1 -> SongsTab()
                2 -> AlbumsTab()
                3 -> ArtistsTab()
                4 -> FoldersTab()
                else -> PlaylistsTab()
            }
        }
    }
}

/**
 * One white pill over the tabs that follows the pager as it is swiped. It inverts what it covers,
 * so a label reads black inside the pill and white outside it, even halfway between two tabs.
 */
private fun Modifier.tabIndicator(row: androidx.compose.foundation.lazy.LazyListState, pager: androidx.compose.foundation.pager.PagerState): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val items = row.layoutInfo.visibleItemsInfo
        val from = items.firstOrNull { it.index == pager.currentPage } ?: return@drawWithContent
        val fraction = pager.currentPageOffsetFraction
        val to = items.firstOrNull { it.index == pager.currentPage + if (fraction < 0f) -1 else 1 } ?: from
        val t = kotlin.math.abs(fraction)
        val start = row.layoutInfo.viewportStartOffset
        val x = androidx.compose.ui.util.lerp((from.offset - start).toFloat(), (to.offset - start).toFloat(), t)
        val w = androidx.compose.ui.util.lerp(from.size.toFloat(), to.size.toFloat(), t)
        drawRoundRect(
            Dot.White, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height),
            androidx.compose.ui.geometry.CornerRadius(size.height / 2), blendMode = androidx.compose.ui.graphics.BlendMode.Difference,
        )
    }

@Composable
private fun SongsTab() {
    val dot = DotApp.instance
    val app = LocalAppState.current
    val lib by dot.library.data.collectAsStateWithLifecycle()
    val sort by dot.settings.songSort.flow.collectAsStateWithLifecycle()
    val state by dot.engine.state.collectAsStateWithLifecycle()
    val songs = remember(lib, sort) {
        when (sort) {
            1 -> lib.songs.sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.track })
            2 -> lib.songs.sortedByDescending { it.dateAdded }
            else -> lib.songs
        }
    }
    val currentId = state.current?.id

    when {
        !lib.ready -> EmptyState("Scanning", "Looking for music on your phone")
        songs.isEmpty() -> EmptyState("No music", "Copy songs into the Music folder and they'll show up here.")
        else -> Box(Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(countOf(songs.size, "song"), Modifier.weight(1f))
                        Pill(sortNames[sort], { dot.settings.songSort.set((sort + 1) % sortNames.size) })
                        Spacer(Modifier.width(8.dp))
                        Pill("Shuffle", { dot.engine.playSongs(songs, shuffleAll = true) }, accent = true, icon = Icons.Rounded.Shuffle)
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, s.id == currentId, onClick = { dot.engine.playSongs(songs, i) }, onMore = { app.menu = SongMenu(s) })
                }
            }
            FastScroller(
                listState, songs.size, itemOffset = 1,
                labelFor = { i ->
                    val song = songs[i]
                    when (sort) {
                        1 -> initial(song.artist)
                        2 -> monthLabel.format(java.util.Date(song.dateAdded * 1000))
                        else -> initial(song.title)
                    }
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

private val monthLabel = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault())

private fun initial(text: String): String {
    val c = text.trimStart().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c.isLetter()) c.toString() else "#"
}

@Composable
private fun AlbumsTab() {
    val app = LocalAppState.current
    val lib by DotApp.instance.library.data.collectAsStateWithLifecycle()
    if (lib.ready && lib.albums.isEmpty()) return EmptyState("No albums", "Albums appear once music is found.")
    LazyVerticalGrid(
        GridCells.Adaptive(156.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(lib.albums, key = { it.id }) { album ->
            Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { app.push(Screen.Album(album.id)) }) {
                Art(album.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), px = 420, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                Text(album.title, color = Dot.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Label(album.artist)
            }
        }
    }
}

@Composable
private fun ArtistsTab() {
    val app = LocalAppState.current
    val lib by DotApp.instance.library.data.collectAsStateWithLifecycle()
    if (lib.ready && lib.artists.isEmpty()) return EmptyState("No artists", "Artists appear once music is found.")
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            items(lib.artists, key = { it.name }) { artist ->
                ListRow(
                    title = artist.name,
                    subtitle = "${countOf(artist.songs.size, "song")} · ${countOf(artist.albumCount, "album")}",
                    onClick = { app.push(Screen.Artist(artist.name)) },
                ) { Art(artist.songs.first(), Modifier.size(52.dp), px = 156, shape = CircleShape) }
            }
        }
        FastScroller(listState, lib.artists.size, itemOffset = 0, labelFor = { initial(lib.artists[it].name) }, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun FoldersTab() {
    val app = LocalAppState.current
    val lib by DotApp.instance.library.data.collectAsStateWithLifecycle()
    if (lib.ready && lib.folders.isEmpty()) return EmptyState("No folders", "Folders appear once music is found.")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(lib.folders, key = { it.path }) { folder ->
            ListRow(
                title = folder.name,
                subtitle = "${countOf(folder.songs.size, "song")} · ${folder.path.removePrefix("/storage/emulated/0")}",
                onClick = { app.push(Screen.Folder(folder.path)) },
            ) { IconTile(DotIcons.Folder) }
        }
    }
}

@Composable
private fun PlaylistsTab() {
    val dot = DotApp.instance
    val app = LocalAppState.current
    val playlists by dot.store.playlists.collectAsStateWithLifecycle()
    val favs by dot.store.favourites.collectAsStateWithLifecycle()
    val bpm by dot.store.bpm.collectAsStateWithLifecycle()
    val lib by dot.library.data.collectAsStateWithLifecycle()
    val moods = remember(bpm, lib) { bpm.filterKeys { it in lib.byId }.values.groupingBy { moodOf(it) }.eachCount() }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Pill("New playlist", { creating = true }, accent = true, icon = Icons.Rounded.Add)
            }
        }
        item { ListRow("Favourites", countOf(favs.size, "song"), { app.push(Screen.Playlist(SMART_FAVOURITES)) }) { IconTile(DotIcons.Heart, red = true) } }
        item { ListRow("Recently added", "Latest 100", { app.push(Screen.Playlist(SMART_RECENT)) }) { IconTile(DotIcons.Recent) } }
        item { ListRow("Most played", "Your top 100", { app.push(Screen.Playlist(SMART_TOP)) }) { IconTile(DotIcons.Top) } }
        item { Label("Mood mixes · learned as you listen", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
        item { ListRow("Chill", moodLine(moods[SMART_CHILL] ?: 0, "slow and easy", "slow"), { app.push(Screen.Playlist(SMART_CHILL)) }) { IconTile(DotIcons.Chill) } }
        item { ListRow("Groove", moodLine(moods[SMART_GROOVE] ?: 0, "steady mid-tempo", "mid-tempo"), { app.push(Screen.Playlist(SMART_GROOVE)) }) { IconTile(DotIcons.Groove) } }
        item { ListRow("Hype", moodLine(moods[SMART_HYPE] ?: 0, "fast and loud", "fast"), { app.push(Screen.Playlist(SMART_HYPE)) }) { IconTile(DotIcons.Hype, red = true) } }
        if (playlists.isNotEmpty()) item { Label("Your playlists", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
        items(playlists, key = { it.id }) { p ->
            ListRow(p.name, countOf(p.songIds.size, "song"), { app.push(Screen.Playlist(p.id)) }) { IconTile(null, text = p.name.take(1)) }
        }
    }
    if (creating) NameDialog("New playlist", "", onDismiss = { creating = false }) { dot.store.create(it); creating = false }
}

const val SMART_FAVOURITES = "smart:favourites"
const val SMART_RECENT = "smart:recent"
const val SMART_TOP = "smart:top"
const val SMART_CHILL = "smart:chill"
const val SMART_GROOVE = "smart:groove"
const val SMART_HYPE = "smart:hype"

/** A mood mix's subtitle: its size and feel, or what to play while it's still empty. */
private fun moodLine(count: Int, feel: String, pace: String) =
    if (count == 0) "Still learning · play a few $pace ones" else "${countOf(count, "song")} · $feel"

/** Tempo bands for the mood mixes. */
fun moodOf(bpm: Int): String = when {
    bpm < 98 -> SMART_CHILL
    bpm <= 125 -> SMART_GROOVE
    else -> SMART_HYPE
}

/** Songs in a mood mix, sorted into it by their learned tempo. */
fun moodSongs(lib: LibraryData, tempo: Map<Long, Int>, mood: String): List<Song> =
    lib.songs.filter { s -> tempo[s.id]?.let { moodOf(it) == mood } == true }

/** Most played first; songs never played are left out. */
fun topSongs(lib: LibraryData, counts: Map<Long, Int>, limit: Int = 100): List<Song> =
    counts.entries.filter { it.value > 0 }.sortedByDescending { it.value }.mapNotNull { lib.byId[it.key] }.take(limit)

fun recentSongs(lib: LibraryData, limit: Int = 100): List<Song> = lib.songs.sortedByDescending { it.dateAdded }.take(limit)

@Composable
fun ListRow(title: String, subtitle: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Dot.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Dot.Muted, style = MonoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun IconTile(icon: DotGlyph?, red: Boolean = false, text: String = "") {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Dot.Surface2), contentAlignment = Alignment.Center) {
        if (icon != null) DotIcon(icon, color = if (red) Dot.Red else Dot.White, size = 24.dp)
        else DotTitle(text, size = 22.sp)
    }
}
