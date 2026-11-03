package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.Screen

/**
 * The player with the queue directly below it, as two pages that follow the finger: swipe up
 * for the queue, down to return. Lists nest inside the pager, so the queue scrolls first and
 * only a pull down from its top moves back to the player.
 */
@Composable
fun PlayerQueuePager(
    viewModel: MainViewModel,
    currentScreen: Screen,
    onOpenDrawer: () -> Unit
) {
    val queuePage = 1
    val pagerState = rememberPagerState(
        initialPage = if (currentScreen == Screen.QUEUE) queuePage else 0
    ) { 2 }

    // Buttons, Back and the drawer change the screen: move the pager to match.
    LaunchedEffect(currentScreen) {
        val target = if (currentScreen == Screen.QUEUE) queuePage else 0
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }
    // A finished swipe changes the page: record it as the current screen.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.navigateTo(if (page == queuePage) Screen.QUEUE else Screen.PLAYER)
        }
    }

    // Drop the keyboard (queue search) once the queue is 20% of the way off screen, not after
    // the swipe settles; a small pull that snaps back leaves it up.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(pagerState) {
        // currentPage flips to the player only past halfway, so before that read the offset.
        snapshotFlow {
            pagerState.currentPage != queuePage || pagerState.currentPageOffsetFraction <= -0.2f
        }.collect { leaving ->
            if (leaving) {
                keyboard?.hide()
                focusManager.clearFocus()
            }
        }
    }

    VerticalPager(
        state = pagerState,
        // Keep the other page composed so the first swipe doesn't stutter while it builds.
        beyondViewportPageCount = 1,
        // A short drag (15% of the screen) is enough to switch; the default needs half.
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapPositionalThreshold = 0.15f),
        modifier = Modifier.fillMaxSize()
    ) { page ->
        if (page == queuePage) {
            PlaylistQueueScreen(viewModel = viewModel, onBack = { viewModel.navigateTo(Screen.PLAYER) })
        } else {
            MainPlayerScreen(viewModel = viewModel, onOpenDrawer = onOpenDrawer)
        }
    }
}
