package com.clevercorner.arrowescape.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the game's chrome sits above the board or beside it.
 */
enum class GamePanes { STACKED, SIDE_BY_SIDE }

/**
 * When a game screen is worth splitting into two panes.
 *
 * Deliberately not a `WindowSizeClass`. A size class answers "how much
 * width is there", and that alone gets this wrong in the one case that
 * matters most: a tablet held upright is Expanded width and should still
 * stack, because stacking is the right shape for a tall window. The real
 * question has two halves - is there enough width to spare, and is this
 * window actually wider than it is tall - so it is asked directly, as a
 * pure function of the two numbers.
 *
 * Being pure also makes it the one part of this layout that can be
 * checked without a device.
 */
object GameLayout {
    /**
     * Wide enough that the rail is not stealing room the board wanted.
     * Below this a split leaves two cramped panes instead of one good one.
     */
    private val MIN_SPLIT_WIDTH = 720.dp

    /**
     * How much wider than tall before splitting earns its keep. A plain
     * `width > height` would flip layouts on a near-square window over a
     * single dp of resize, which on a folding phone is a hinge animation
     * that rearranges the screen twice on the way past.
     */
    private const val MIN_SPLIT_RATIO = 1.25f

    /** Chrome rail: wide enough for the stats row, narrow enough to be chrome. */
    val RAIL_WIDTH: Dp = 300.dp

    fun panesFor(width: Dp, height: Dp): GamePanes =
        if (width >= MIN_SPLIT_WIDTH && width >= height * MIN_SPLIT_RATIO) {
            GamePanes.SIDE_BY_SIDE
        } else {
            GamePanes.STACKED
        }
}

/**
 * The frame both game modes are played in: a board, the chrome around it,
 * and an ad along the bottom.
 *
 * Exists for two reasons that happen to be the same refactor. The header
 * band used to be copied character for character into both game screens,
 * and a landscape layout would have meant copying the choice between two
 * arrangements into both of them as well. Now the screens supply content
 * and this decides where it goes - which means a change to the shape of
 * the game is made once, and neither screen can drift from the other.
 *
 * [board] receives the modifier for its pane rather than fetching its own,
 * because only the scaffold knows whether that pane is a row or a column.
 */
@Composable
fun GameScaffold(
    topBar: @Composable () -> Unit,
    stats: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    banner: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    board: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier) {
        when (GameLayout.panesFor(maxWidth, maxHeight)) {
            GamePanes.STACKED -> Column(Modifier.fillMaxSize()) {
                HeaderBand {
                    topBar()
                    stats()
                    Spacer(Modifier.height(12.dp))
                }
                board(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                controls()
                banner()
            }

            GamePanes.SIDE_BY_SIDE -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    board(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .padding(16.dp)
                    )
                    ChromeRail {
                        topBar()
                        stats()
                        // The board is the thing to look at; the controls
                        // belong at the bottom of the rail where a hand
                        // holding the slab already is, not floating in the
                        // middle of it.
                        Spacer(Modifier.weight(1f))
                        controls()
                    }
                }
                banner()
            }
        }
    }
}

/** The branded band across the top of a stacked layout. */
@Composable
private fun HeaderBand(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .background(HeaderTint()),
            content = content
        )
    }
}

/** The same chrome, stood on its end down the side of a wide window. */
@Composable
private fun ChromeRail(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .width(GameLayout.RAIL_WIDTH)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(HeaderTint()),
            verticalArrangement = Arrangement.Top,
            content = content
        )
    }
}

/** The faint wash of brand colour both chrome shapes share. */
@Composable
private fun HeaderTint(): Brush = Brush.verticalGradient(
    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), Color.Transparent)
)
