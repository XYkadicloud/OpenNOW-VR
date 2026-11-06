package com.opencloudgaming.opennow

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Persisted choice between the console-style home and the classic catalog home.
 * The preference key keeps its historical name so an existing install does not lose the setting.
 */
object HomeUiState {
    /** true = console-style home (hero card + icon rail), false = classic catalog home. */
    val useSwitchStyle: MutableState<Boolean> = mutableStateOf(true)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        useSwitchStyle.value = prefs.getBoolean(KEY_SWITCH_HOME, true)
    }

    fun set(context: Context, enabled: Boolean) {
        useSwitchStyle.value = enabled
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SWITCH_HOME, enabled).apply()
    }

    private const val PREFS = "opennow_ui"
    private const val KEY_SWITCH_HOME = "switch_home"
}

// Flat gradient palette. Deliberately no game artwork behind the shell: a low-resolution banner
// blown up to full screen looks worse than a clean gradient, and the gradient costs no bandwidth.
private val ConsoleTop = Color(0xFF1B2433)
private val ConsoleMid = Color(0xFF11161F)
private val ConsoleBottom = Color(0xFF080B10)
private val ConsoleText = Color(0xFFF3F6FB)
private val ConsoleMuted = Color(0xFF9AA6B8)
private val ConsoleAccent = Color(0xFF69E6FF)
private val ConsoleCard = Color(0xFF1A2130)

/**
 * A third, optional home shell modelled on a modern console dashboard: one wide 16:9 hero card for
 * the focused game, a row of primary actions underneath, and a rail of square game icons along the
 * bottom. Purely additive — the classic catalog home and the experimental space shell are untouched
 * and can be selected from Settings.
 */
@Composable
internal fun ConsoleHomeScreen(
    state: OpenNowUiState,
    viewModel: OpenNowViewModel,
    tvProfile: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalogGames = state.games.ifEmpty { state.catalogResult.games }
    val terms = remember(state.catalogSearch) { searchTermsFor(state.catalogSearch) }
    val games = remember(catalogGames, terms) {
        if (terms.isEmpty()) catalogGames else catalogGames.filter { gameMatchesSearch(it, terms) }
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = games.firstOrNull { it.id == selectedId } ?: games.firstOrNull()
    val railState = rememberLazyListState()
    val firstTileRequester = remember { FocusRequester() }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(games.isNotEmpty()) {
        if (!initialFocusDone && games.isNotEmpty()) {
            delay(150)
            runCatching { firstTileRequester.requestFocus() }
            initialFocusDone = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ConsoleTop, ConsoleMid, ConsoleBottom)))
            .drawWithCache {
                // Built once per size instead of once per frame.
                val glow = Brush.radialGradient(
                    colors = listOf(ConsoleAccent.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.06f),
                    radius = size.width.coerceAtLeast(size.height) * 0.7f,
                )
                onDrawBehind { drawRect(glow) }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (tvProfile) 30.dp else 18.dp, vertical = if (tvProfile) 16.dp else 12.dp),
        ) {
            ConsoleTopBar(
                state = state,
                onSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) viewModel.setCatalogSearch("")
                },
                onRefresh = { viewModel.refreshGames() },
                onLibrary = { viewModel.setPage(AppPage.Library) },
                onSettings = { viewModel.setPage(AppPage.Settings) },
            )

            if (searchOpen) {
                Spacer(Modifier.height(10.dp))
                NativeSearchField(
                    query = state.catalogSearch,
                    onQueryChange = viewModel::setCatalogSearch,
                    placeholder = stringResource(R.string.nav_search),
                    searching = state.loadingGames,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(if (tvProfile) 16.dp else 10.dp))

            if (games.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.loadingGames) {
                            stringResource(R.string.console_home_loading)
                        } else {
                            stringResource(R.string.console_home_empty)
                        },
                        color = ConsoleMuted,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // Fit a true 16:9 card inside whatever vertical space is left. No cropping, and
                    // the same rule works on a 4:3 panel and on a 21:9 one.
                    val cardHeight = minOf(maxHeight, maxWidth * 9f / 16f)
                    val cardWidth = cardHeight * 16f / 9f
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ConsoleHeroCard(
                            game = selected,
                            modifier = Modifier.size(cardWidth, cardHeight),
                            actions = {
                                ConsoleActionRow(
                                    canAct = selected != null,
                                    onPlay = { selected?.let { viewModel.play(it) } },
                                    onDetails = { selected?.let { viewModel.selectGame(it) } },
                                    onAddToHome = {
                                        val game = selected
                                        if (game != null) {
                                            scope.launch {
                                                val result = GameShortcuts.pin(context, game)
                                                val message = when (result.kind) {
                                                    GameShortcuts.PinResult.Kind.Pinned ->
                                                        context.getString(R.string.console_home_shortcut_added, game.title)
                                                    GameShortcuts.PinResult.Kind.ChannelAdded ->
                                                        context.getString(R.string.console_home_channel_added, game.title)
                                                    GameShortcuts.PinResult.Kind.SlotAdded ->
                                                        context.getString(R.string.console_home_shortcut_slot_added, game.title, result.slot)
                                                    GameShortcuts.PinResult.Kind.AlreadyAdded ->
                                                        context.getString(R.string.console_home_shortcut_exists, game.title)
                                                    GameShortcuts.PinResult.Kind.NoSlot ->
                                                        context.getString(R.string.console_home_shortcut_no_slot)
                                                    GameShortcuts.PinResult.Kind.Failed ->
                                                        context.getString(R.string.console_home_shortcut_failed)
                                                }
                                                toast(context, message)
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(if (tvProfile) 14.dp else 10.dp))

                LazyRow(
                    state = railState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (tvProfile) 12.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(games, key = { _, item -> item.id }) { index, game ->
                        Box(Modifier.padding(6.dp)) {
                            ConsoleGameTile(
                                game = game,
                                size = if (tvProfile) 70.dp else 62.dp,
                                focusedTile = game.id == selected?.id,
                                modifier = if (index == 0) Modifier.focusRequester(firstTileRequester) else Modifier,
                                onFocused = { selectedId = game.id },
                                onClick = {
                                    selectedId = game.id
                                    viewModel.selectGame(game)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleTopBar(
    state: OpenNowUiState,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
) {
    val currentUser = state.authSession?.user
    val savedAccount = state.savedAccounts.firstOrNull { it.userId == currentUser?.userId }
        ?: state.savedAccounts.firstOrNull()
    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: savedAccount?.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.profile_not_available)
    val tier = state.subscriptionInfo?.membershipTier?.takeIf { it.isNotBlank() }
        ?: currentUser?.membershipTier?.takeIf { it.isNotBlank() }
        ?: savedAccount?.membershipTier?.takeIf { it.isNotBlank() }
    val avatarUrl = currentUser?.avatarUrl?.takeIf { it.isNotBlank() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C3A50))
                .border(1.dp, ConsoleText.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = displayName.take(1).uppercase(),
                    color = ConsoleText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = displayName,
                color = ConsoleText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tier != null) {
                Text(tier, color = ConsoleMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(1f))
        ConsoleIconButton(Icons.Filled.Search, stringResource(R.string.nav_search), onSearch)
        Spacer(Modifier.width(10.dp))
        ConsoleIconButton(Icons.Filled.Refresh, stringResource(R.string.action_refresh), onRefresh)
        Spacer(Modifier.width(10.dp))
        ConsoleIconButton(Icons.Filled.List, stringResource(R.string.nav_library), onLibrary)
        Spacer(Modifier.width(10.dp))
        ConsoleIconButton(Icons.Filled.Settings, stringResource(R.string.nav_settings), onSettings)
    }
}

@Composable
private fun ConsoleIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "console-icon-scale")
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (focused) ConsoleText.copy(alpha = 0.20f) else ConsoleText.copy(alpha = 0.08f))
            .border(1.5.dp, if (focused) ConsoleAccent else Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = ConsoleText, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ConsoleHeroCard(
    game: GameInfo?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    // D-pad traversal across the rail fires a focus change per tile. Requesting a fresh banner for
    // every one of those queued a network round trip plus a full-size decode per step, which is what
    // made the rail feel sticky on a 2019 TV. Only the tile the player settles on is fetched.
    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(game?.id) {
        val target = game
        if (target == null) {
            banner = null
            return@LaunchedEffect
        }
        delay(140)
        banner = gameTvBannerImageUrl(context, target)
    }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(ConsoleCard)
            .border(1.dp, ConsoleText.copy(alpha = 0.10f), shape),
    ) {
        if (banner != null) {
            AsyncImage(
                model = banner,
                contentDescription = game?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.28f to Color.Transparent,
                        1f to Color(0xF70A0E14),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Text(
                text = game?.title.orEmpty(),
                color = ConsoleText,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = game?.let(::storeHeroSubtitle).orEmpty()
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = ConsoleAccent,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            actions()
        }
    }
}

@Composable
private fun ConsoleActionRow(
    canAct: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onAddToHome: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConsoleActionButton(
            label = stringResource(R.string.action_play),
            primary = true,
            enabled = canAct,
            onClick = onPlay,
        )
        ConsoleActionButton(
            label = stringResource(R.string.console_home_add_shortcut),
            primary = false,
            enabled = canAct,
            onClick = onAddToHome,
        )
        ConsoleActionButton(
            label = stringResource(R.string.console_home_details),
            primary = false,
            enabled = canAct,
            onClick = onDetails,
        )
    }
}

@Composable
private fun ConsoleActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "console-action-scale")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(if (primary) ConsoleText else ConsoleText.copy(alpha = 0.14f))
            .border(2.dp, if (focused) ConsoleAccent else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) Color(0xFF0A0E14) else ConsoleText,
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConsoleGameTile(
    game: GameInfo,
    size: androidx.compose.ui.unit.Dp,
    focusedTile: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.16f else 1f, label = "console-tile-scale")
    val shape = RoundedCornerShape(14.dp)
    val art = catalogCardImageUrl(game, tvProfile = false)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(ConsoleCard)
            .border(
                width = if (focused || focusedTile) 2.dp else 1.dp,
                color = when {
                    focused -> ConsoleText
                    focusedTile -> ConsoleAccent.copy(alpha = 0.7f)
                    else -> ConsoleText.copy(alpha = 0.14f)
                },
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
    ) {
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = game.title,
                    color = ConsoleText,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(6.dp),
                )
            }
        }
    }
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}
