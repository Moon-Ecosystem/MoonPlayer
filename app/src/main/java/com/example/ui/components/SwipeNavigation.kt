package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Turns a drag that runs past the end of the scrollable content below this modifier into
 * navigation: pulling up past the bottom calls [onPullUp], pulling down past the top calls
 * [onPullDown]. Normal scrolling is untouched because only the leftover (unconsumed) drag counts.
 */
@Composable
fun Modifier.overscrollNavigation(
    onPullUp: (() -> Unit)? = null,
    onPullDown: (() -> Unit)? = null,
    threshold: Dp = 80.dp
): Modifier {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    // Read the latest callbacks without recreating the connection (and losing the pull) mid-drag.
    val pullUp by rememberUpdatedState(onPullUp)
    val pullDown by rememberUpdatedState(onPullDown)
    val connection = remember(thresholdPx) {
        object : NestedScrollConnection {
            var leftover = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val handled = (available.y < 0 && pullUp != null) || (available.y > 0 && pullDown != null)
                if (!handled) return Offset.Zero
                leftover += available.y
                // Claim it: otherwise Android's stretch overscroll starts on the first frame and
                // swallows the rest of the drag before it reaches us.
                return Offset(0f, available.y)
            }

            // The drag has ended here; fling scrolling that follows is discarded in onPostFling.
            override suspend fun onPreFling(available: Velocity): Velocity {
                val pulled = leftover
                leftover = 0f
                when {
                    pulled < -thresholdPx -> pullUp?.invoke()
                    pulled > thresholdPx -> pullDown?.invoke()
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                leftover = 0f
                return Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}
