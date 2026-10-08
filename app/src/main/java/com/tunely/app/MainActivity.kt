package com.tunely.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tunely.app.ui.MainViewModel
import com.tunely.app.ui.design.AuroraSurfaceRoot
import com.tunely.app.ui.design.Dimens
import com.tunely.app.ui.design.DockBar
import com.tunely.app.ui.design.DockItem
import com.tunely.app.ui.design.GhostIconButton
import com.tunely.app.ui.design.T
import com.tunely.app.ui.design.TunelyTheme
import com.tunely.app.ui.design.rememberArtworkColors
import com.tunely.app.ui.screens.HomeScreen
import com.tunely.app.ui.screens.LibraryScreen
import com.tunely.app.ui.screens.MiniPlayer
import com.tunely.app.ui.screens.NowPlayingScreen
import com.tunely.app.ui.screens.SearchScreen
import com.tunely.app.ui.screens.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TunelyTheme(settings = vm.settings) {
                Root(vm)
            }
        }
    }
}

private enum class Tab(val label: String) {
    HOME("Discover"),
    SEARCH("Search"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

@Composable
private fun Root(vm: MainViewModel) {
    val colors = T.colors
    val state by vm.playerState.collectAsState()
    val position by vm.position.collectAsState()
    val message by vm.message.collectAsState()
    val playerError by vm.playerError.collectAsState()
    val aurora by vm.settings.aurora.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    val banner = playerError ?: message

    val artworkSeeds = rememberArtworkColors(
        url = state.track?.artworkUrl,
        fallback = remember(colors.accent) {
            listOf(colors.accent.primary, colors.accent.secondary)
        }
    )

    // Auto-dismiss transient messages so the banner never sticks around.
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(4_000)
            vm.clearMessage()
            vm.player.clearError()
        }
    }

    val notifier = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Only ask once: re-prompting on every launch was one of the old annoyances.
    LaunchedEffect(Unit) {
        val settings = vm.settings
        if (Build.VERSION.SDK_INT >= 33 && !settings.notificationPrompted) {
            settings.notificationPrompted = true
            notifier.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = expanded) { expanded = false }

    AuroraSurfaceRoot(
        seeds = artworkSeeds,
        animated = aurora,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val forward = targetState > initialState
                            val offset = if (forward) 1 else -1
                            (
                                slideInHorizontally(tween(320)) { width -> offset * width / 6 } +
                                    fadeIn(tween(220))
                                ) togetherWith (
                                slideOutHorizontally(tween(280)) { width -> -offset * width / 8 } +
                                    fadeOut(tween(180))
                                )
                        },
                        label = "tabs"
                    ) { current ->
                        val pagePadding = PaddingValues(
                            top = 28.dp,
                            bottom = 200.dp
                        )
                        when (current) {
                            Tab.HOME.ordinal -> HomeScreen(
                                vm = vm,
                                onOpenPlayer = { expanded = true },
                                onSearch = { query ->
                                    vm.onQueryChange(query)
                                    tab = Tab.SEARCH.ordinal
                                },
                                onOpenLibrary = { tab = Tab.LIBRARY.ordinal },
                                contentPadding = pagePadding
                            )
                            Tab.SEARCH.ordinal -> SearchScreen(vm, pagePadding)
                            Tab.LIBRARY.ordinal -> LibraryScreen(vm, pagePadding)
                            else -> SettingsScreen(vm, pagePadding)
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = Dimens.sm)
                ) {
                    AnimatedVisibility(
                        visible = banner != null,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut()
                    ) {
                        Banner(
                            text = banner.orEmpty(),
                            isError = playerError != null,
                            onDismiss = {
                                vm.clearMessage()
                                vm.player.clearError()
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = state.track != null,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut()
                    ) {
                        MiniPlayer(
                            state = state,
                            positionMs = position,
                            onExpand = { expanded = true },
                            onTogglePlay = { vm.player.togglePlay() },
                            onNext = { vm.player.next() },
                            onPrevious = { vm.player.previous() },
                            modifier = Modifier.padding(bottom = Dimens.sm)
                        )
                    }

                    DockBar(
                        items = listOf(
                            DockItem(Tab.HOME.ordinal, Tab.HOME.label, Icons.Rounded.Home),
                            DockItem(Tab.SEARCH.ordinal, Tab.SEARCH.label, Icons.Rounded.Search),
                            DockItem(Tab.LIBRARY.ordinal, Tab.LIBRARY.label, Icons.Rounded.LibraryMusic),
                            DockItem(Tab.SETTINGS.ordinal, Tab.SETTINGS.label, Icons.Rounded.Settings)
                        ),
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.padding(top = Dimens.sm)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && state.track != null,
                enter = slideInVertically(tween(420)) { it } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(320)) { it } + fadeOut(tween(200))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background)
                ) {
                    NowPlayingScreen(vm = vm, onCollapse = { expanded = false })
                }
            }
        }
    }
}

@Composable
private fun Banner(
    text: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val colors = T.colors
    val accent = if (isError) Color(0xFFFF4D6D) else colors.accent.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.md, vertical = 4.dp)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(colors.surfaceGlass)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = Dimens.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.3f))))
        )
        Spacer(Modifier.width(Dimens.md))
        Text(
            text,
            style = T.type.caption,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Dimens.sm))
        GhostIconButton(
            icon = Icons.Rounded.Close,
            onClick = onDismiss,
            size = 28.dp,
            iconSize = 14.dp
        )
    }
}
