package com.hondash.android.ui.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hondash.android.kpro.KproSnapshot
import androidx.compose.foundation.background as bg

const val TILES_PER_PAGE = 4
private const val GRID_COLS = 2
private const val GRID_ROWS = 2

/**
 * Swipeable pager containing one or more 2×3 tile pages.
 *
 * A trailing "+" slot is always rendered (on its own new page if the
 * current last page is full) so the user can add new tiles from anywhere.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TilePager(
    tiles: List<TileSpec>,
    snapshot: KproSnapshot,
    live: Boolean,
    onTileLongPress: (Int) -> Unit,
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSlots = tiles.size + 1
    val pageCount = ((totalSlots + TILES_PER_PAGE - 1) / TILES_PER_PAGE).coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            TileGridPage(
                page = page,
                tiles = tiles,
                snapshot = snapshot,
                live = live,
                onTileLongPress = onTileLongPress,
                onAddTile = onAddTile,
            )
        }
        if (pageCount > 1) {
            Spacer(Modifier.size(6.dp))
            PageDots(pageCount = pageCount, current = pagerState.currentPage)
        }
    }
}

@Composable
private fun TileGridPage(
    page: Int,
    tiles: List<TileSpec>,
    snapshot: KproSnapshot,
    live: Boolean,
    onTileLongPress: (Int) -> Unit,
    onAddTile: () -> Unit,
) {
    val start = page * TILES_PER_PAGE
    val end = minOf(start + TILES_PER_PAGE, tiles.size)
    val pageTiles = if (start < tiles.size) tiles.subList(start, end) else emptyList()
    // The "+" lives on the page where the (tiles.size)-th slot falls.
    val addOnThisPage = (tiles.size / TILES_PER_PAGE) == page

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in 0 until GRID_ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (col in 0 until GRID_COLS) {
                    val slot = row * GRID_COLS + col
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        when {
                            slot < pageTiles.size -> {
                                val globalIdx = start + slot
                                ValueTile(
                                    spec = pageTiles[slot],
                                    snapshot = snapshot,
                                    live = live,
                                    onLongPress = { onTileLongPress(globalIdx) },
                                )
                            }
                            addOnThisPage && slot == pageTiles.size -> {
                                AddTileSlot(onClick = onAddTile)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(pageCount: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        for (i in 0 until pageCount) {
            val color = if (i == current) MaterialTheme.colorScheme.primary else Color.Gray
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .bg(color),
            )
        }
    }
}
