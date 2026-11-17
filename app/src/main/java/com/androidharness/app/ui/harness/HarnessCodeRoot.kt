package com.androidharness.app.ui.harness

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androidharness.app.AppContainer
import com.androidharness.app.data.AppSettings
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.data.db.SessionEntity
import com.androidharness.app.ui.chat.ChatViewModel
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.session.HarnessSessionRoute
import com.androidharness.app.ui.harness.theme.HarnessCodeTheme
import com.androidharness.app.ui.harness.theme.HarnessTheme
import com.androidharness.app.ui.harness.theme.Space
import com.androidharness.app.ui.setup.SetupScreen
import kotlinx.coroutines.launch

/** Where the shell can send the user. */
sealed interface HarnessRoute {
    data object Home : HarnessRoute
    data class Session(val sessionId: String?) : HarnessRoute
    data object Projects : HarnessRoute
    data object Scheduled : HarnessRoute
    data object Parallel : HarnessRoute
    data object Settings : HarnessRoute
    data object SettingsAll : HarnessRoute
    data object Providers : HarnessRoute
    data object Skills : HarnessRoute
    data object Labs : HarnessRoute
    data object About : HarnessRoute
    data object Classic : HarnessRoute
}

/** Wide layouts get a persistent sidebar; phones get the drawer. */
private val SidebarBreakpoint = 840.dp

/**
 * Harness Code entry point.
 *
 * Owns the theme, onboarding gate and the shell. The engine is untouched: every
 * session screen is driven through [com.androidharness.app.agent.session.AgentSession].
 */
@Composable
fun HarnessCodeRoot(container: AppContainer) {
    val settingsState by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings = settingsState
    if (settings == null) {
        // Warm paper while DataStore's first emission is in flight: no white flash.
        Box(Modifier.fillMaxSize().background(HarnessTheme.palette.paper))
        return
    }
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    HarnessCodeTheme(dark = dark) {
        Box(Modifier.fillMaxSize().background(HarnessTheme.palette.paper)) {
            if (!settings.onboardingDone) {
                val scope = rememberCoroutineScope()
                SetupScreen(container = container, onFinish = {
                    scope.launch { container.settings.setOnboardingDone(true) }
                })
            } else {
                HarnessShell(container, settings)
            }
        }
    }
}

@Composable
private fun HarnessShell(container: AppContainer, settings: AppSettings) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by container.sessions.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val projects by container.workspace.workProjects.collectAsStateWithLifecycle(initialValue = emptyList())
    val running by container.runManager.runningSessionIds.collectAsStateWithLifecycle()
    val initial = remember { settings.lastActiveSessionId?.let { HarnessRoute.Session(it) } ?: HarnessRoute.Home }
    var stack by remember { mutableStateOf(listOf<HarnessRoute>(initial)) }
    val route = stack.last()

    fun navigate(target: HarnessRoute) {
        stack = stack + target
    }

    fun replace(target: HarnessRoute) {
        stack = stack.dropLast(1) + target
    }

    fun openSession(sessionId: String?) {
        // Sessions are peers: opening one replaces the open conversation.
        stack = if (route is HarnessRoute.Session) stack.dropLast(1) + HarnessRoute.Session(sessionId)
        else stack + HarnessRoute.Session(sessionId)
    }

    // Notifications and Telegram deep links arrive on this flow.
    LaunchedEffect(container) {
        container.pendingSessionId.collect { sid -> openSession(sid) }
    }

    BackHandler(enabled = stack.size > 1) {
        stack = stack.dropLast(1)
    }

    val drawerContent: @Composable (Boolean) -> Unit = { compact ->
        HarnessDrawer(
            container = container,
            settings = settings,
            sessions = sessions,
            runningIds = running,
            activeSessionId = (route as? HarnessRoute.Session)?.sessionId,
            current = route,
            compact = compact,
            onClose = { scope.launch { drawerState.close() } },
            onNewSession = {
                scope.launch { drawerState.close() }
                openSession(null)
            },
            onOpenSession = {
                scope.launch { drawerState.close() }
                openSession(it)
            },
            onNavigate = { target ->
                scope.launch { drawerState.close() }
                navigate(target)
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= SidebarBreakpoint
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .background(HarnessTheme.palette.sidebar),
                ) { drawerContent(true) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(HarnessTheme.palette.border))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    HarnessRouteHost(
                        container = container,
                        settings = settings,
                        route = route,
                        sessions = sessions,
                        projects = projects.map { it.id to it.name },
                        running = running,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        showMenuButton = false,
                        onNavigate = ::navigate,
                        onReplace = ::replace,
                        onOpenSession = ::openSession,
                        onBack = { stack = stack.dropLast(1) },
                    )
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = HarnessTheme.palette.sidebar,
                        drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                        modifier = Modifier.width(304.dp),
                    ) { drawerContent(false) }
                },
            ) {
                HarnessRouteHost(
                    container = container,
                    settings = settings,
                    route = route,
                    sessions = sessions,
                    projects = projects.map { it.id to it.name },
                    running = running,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    showMenuButton = true,
                    onNavigate = ::navigate,
                    onReplace = ::replace,
                    onOpenSession = ::openSession,
                    onBack = { stack = stack.dropLast(1) },
                )
            }
        }
    }
}

@Composable
private fun HarnessRouteHost(
    container: AppContainer,
    settings: AppSettings,
    route: HarnessRoute,
    sessions: List<SessionEntity>,
    projects: List<Pair<String, String>>,
    running: Set<String>,
    onOpenDrawer: () -> Unit,
    showMenuButton: Boolean,
    onNavigate: (HarnessRoute) -> Unit,
    onReplace: (HarnessRoute) -> Unit,
    onOpenSession: (String?) -> Unit,
    onBack: () -> Unit,
) {
    when (route) {
        HarnessRoute.Home -> HarnessHomeScreen(
            container = container,
            settings = settings,
            sessions = sessions,
            projects = projects,
            onOpenDrawer = onOpenDrawer,
            showMenuButton = showMenuButton,
            onNewSession = onOpenSession,
            onOpenSession = onOpenSession,
            onNavigate = onNavigate,
        )

        is HarnessRoute.Session -> {
            val key = route.sessionId ?: "new-session"
            val viewModel: ChatViewModel = viewModel(
                key = "harness-$key",
                factory = ChatViewModel.factory(container, route.sessionId),
            )
            HarnessSessionRoute(
                viewModel = viewModel,
                container = container,
                showMenuButton = showMenuButton,
                onOpenDrawer = onOpenDrawer,
                onBack = onBack,
                onOpenSession = onOpenSession,
                onNavigateToProjects = { onNavigate(HarnessRoute.Projects) },
                onNavigateToSettings = { onNavigate(HarnessRoute.Settings) },
            )
        }

        HarnessRoute.Projects -> HarnessProjectsScreen(
            container = container,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            showMenuButton = showMenuButton,
            onOpenSession = onOpenSession,
        )

        HarnessRoute.Scheduled -> HarnessScheduledScreen(
            container = container,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            showMenuButton = showMenuButton,
            onOpenSession = onOpenSession,
        )

        HarnessRoute.Parallel -> HarnessParallelScreen(
            container = container,
            sessions = sessions,
            running = running,
            onBack = onBack,
            onOpenSession = onOpenSession,
        )

        HarnessRoute.Settings -> HarnessSettingsScreen(
            container = container,
            settings = settings,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            showMenuButton = showMenuButton,
            onOpenClassic = { onReplace(HarnessRoute.Classic) },
            onOpenLabs = { onNavigate(HarnessRoute.Labs) },
            onOpenAbout = { onNavigate(HarnessRoute.About) },
            onOpenAllSettings = { onNavigate(HarnessRoute.SettingsAll) },
            onOpenProviders = { onNavigate(HarnessRoute.Providers) },
            onOpenSkills = { onNavigate(HarnessRoute.Skills) },
        )

        HarnessRoute.SettingsAll -> com.androidharness.app.ui.settings.SettingsScreen(
            container = container,
            onBack = onBack,
            onOpenSkills = { onNavigate(HarnessRoute.Skills) },
            onOpenProviders = { onNavigate(HarnessRoute.Providers) },
        )

        HarnessRoute.Providers -> com.androidharness.app.ui.settings.ProvidersScreen(
            container = container,
            onBack = onBack,
        )

        HarnessRoute.Skills -> com.androidharness.app.ui.settings.SkillsScreen(
            container = container,
            onBack = onBack,
        )

        HarnessRoute.Labs -> HarnessLabsScreen(
            container = container,
            onBack = onBack,
            onOpenAllSettings = { onNavigate(HarnessRoute.SettingsAll) },
        )

        HarnessRoute.About -> HarnessAboutScreen(onBack = onBack)

        HarnessRoute.Classic -> com.androidharness.app.ui.AppNav(container)
    }
}

/**
 * The wordmark: a coral tile with an open loop and a small dot — a harness
 * ring, not a wordmark or a starburst, so it shares nothing with Anthropic's
 * mark. Drawn in code so the in-app logo and the launcher asset never drift.
 */
@Composable
fun HarnessMark(size: androidx.compose.ui.unit.Dp = 26.dp, tint: Color = HarnessTheme.palette.accent) {
    val paper = HarnessTheme.palette.surface
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        drawRoundRect(
            color = tint,
            size = Size(side, side),
            cornerRadius = CornerRadius(side * 0.28f, side * 0.28f),
        )
        val stroke = side * 0.115f
        val inset = side * 0.27f
        drawArc(
            color = paper,
            startAngle = 52f,
            sweepAngle = 288f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(side - inset * 2, side - inset * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawCircle(
            color = paper,
            radius = stroke * 0.62f,
            center = Offset(side * 0.5f, side * 0.5f),
        )
    }
}

@Composable
fun HarnessWordmark(subtitle: String? = null, onClose: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessMark()
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                "Harness Code",
                style = MaterialTheme.typography.titleLarge,
                color = HarnessTheme.palette.text,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = HarnessTheme.palette.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close menu", tint = HarnessTheme.palette.muted)
            }
        }
    }
}

/** Shared header for hub screens reached from the drawer. */
@Composable
fun HarnessHubHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().background(HarnessTheme.palette.paper)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onOpenDrawer != null) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open menu", tint = HarnessTheme.palette.text)
                }
            }
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = HarnessTheme.palette.text)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = HarnessTheme.palette.text,
                modifier = Modifier.weight(1f).padding(start = Space.sm),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

/** Drawer destination row with an icon, label and optional live count. */
@Composable
internal fun DrawerDestination(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val palette = HarnessTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = Space.sm)
            .background(
                if (selected) palette.accentSoft else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) palette.accentInk else palette.muted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) palette.accentInk else palette.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badge != null) Pill(label = badge, monospace = true)
    }
}

/** Reusable drawer icons so the shell and the hubs agree on meaning. */
internal object HarnessIcons {
    val Projects = Icons.Outlined.Folder
    val Scheduled = Icons.Outlined.Schedule
    val Settings = Icons.Outlined.Settings
    val Labs = Icons.Outlined.Smartphone
    val About = Icons.Outlined.Info
    val Parallel = Icons.Outlined.Splitscreen
    val Today = Icons.Outlined.CalendarToday
    val New = Icons.Filled.Add
    val Memory = Icons.Outlined.Memory
}

/** Session list search field shared by the drawer. */
@Composable
internal fun DrawerSearch(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search sessions", style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md),
    )
}

/** Bottom bar of the drawer with the composer-style new session action. */
@Composable
internal fun DrawerFooter(onNewSession: () -> Unit, runningCount: Int) {
    Column(Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(Space.md)) {
        if (runningCount > 0) {
            Text(
                "$runningCount running",
                style = MaterialTheme.typography.labelSmall,
                color = HarnessTheme.palette.faint,
                modifier = Modifier.padding(bottom = Space.sm, start = Space.xs),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(HarnessTheme.palette.accent, RoundedCornerShape(12.dp))
                .clickable { onNewSession() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = HarnessTheme.palette.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
            Text("New session", style = MaterialTheme.typography.labelLarge, color = HarnessTheme.palette.onAccent)
        }
    }
}
