package com.bxiidn.esimmanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bxiidn.esimmanager.device.DeviceReport
import com.bxiidn.esimmanager.device.EuiccController
import com.bxiidn.esimmanager.device.EuiccEvent
import com.bxiidn.esimmanager.device.OperatingMode
import com.bxiidn.esimmanager.ui.screens.CompatibilityScreen
import com.bxiidn.esimmanager.ui.screens.ConnectivityScreen
import com.bxiidn.esimmanager.ui.screens.DashboardScreen
import com.bxiidn.esimmanager.ui.screens.ProfileDetailScreen
import com.bxiidn.esimmanager.ui.screens.ProfilesScreen
import com.bxiidn.esimmanager.ui.screens.SettingsScreen
import com.bxiidn.esimmanager.ui.screens.StoreScreen
import kotlinx.coroutines.delay

enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Outlined.Dashboard),
    PROFILES("profiles", "Profil eSIM", Icons.Outlined.SimCard),
    STORE("store", "eSIM Store", Icons.Outlined.Storefront),
    CONNECTIVITY("connectivity", "Konektivitas", Icons.Outlined.Wifi),
    COMPATIBILITY("compatibility", "Kompatibilitas", Icons.Outlined.VerifiedUser),
    SETTINGS("settings", "Pengaturan", Icons.Outlined.Settings),
}

private fun NavHostController.goTop(dest: Dest) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppShell() {
    val vm: MainViewModel = viewModel()
    val storeVm: StoreViewModel = viewModel()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val current = Dest.entries.firstOrNull { route == it.route || route.startsWith(it.route + "/") }

    val report by vm.report.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val euiccEvent by vm.euiccEvent.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        if (message != null) {
            delay(4200)
            vm.consumeMessage()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val showSidebar = maxWidth >= 600.dp
        val expanded = maxWidth >= 1100.dp

        Row(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (showSidebar) {
                Sidebar(expanded = expanded, current = current, report = report, onSelect = { nav.goTop(it) })
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    NavHost(
                        navController = nav,
                        startDestination = Dest.DASHBOARD.route,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 28 } },
                        exitTransition = { fadeOut(tween(140)) },
                        popEnterTransition = { fadeIn(tween(260)) },
                        popExitTransition = { fadeOut(tween(140)) + slideOutVertically(tween(200)) { it / 28 } },
                    ) {
                        composable(Dest.DASHBOARD.route) {
                            DashboardScreen(vm = vm, onNavigate = { nav.goTop(it) })
                        }
                        composable(Dest.PROFILES.route) {
                            ProfilesScreen(vm = vm, onOpen = { id -> nav.navigate("${Dest.PROFILES.route}/$id") })
                        }
                        composable(
                            route = "${Dest.PROFILES.route}/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { entry ->
                            val id = entry.arguments?.getLong("id") ?: -1L
                            ProfileDetailScreen(vm = vm, profileId = id, onBack = { nav.popBackStack() })
                        }
                        composable(Dest.STORE.route) {
                            StoreScreen(
                                vm = storeVm,
                                report = report,
                                onOpenSettings = { nav.goTop(Dest.SETTINGS) },
                                onOpenProfile = { id -> nav.navigate("${Dest.PROFILES.route}/$id") },
                            )
                        }
                        composable(Dest.CONNECTIVITY.route) {
                            ConnectivityScreen(vm = vm, onNavigate = { nav.goTop(it) })
                        }
                        composable(Dest.COMPATIBILITY.route) {
                            CompatibilityScreen(vm = vm)
                        }
                        composable(Dest.SETTINGS.route) {
                            SettingsScreen(vm = vm, storeVm = storeVm)
                        }
                    }

                    // Pesan singkat di bagian bawah konten.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(24.dp),
                    ) {
                        MessageToast(message)
                    }
                }
                if (!showSidebar) {
                    BottomBar(current = current, onSelect = { nav.goTop(it) })
                }
            }
        }
    }

    euiccEvent?.let { event -> EuiccEventDialog(event = event, vm = vm) }
}

/** Dipisah ke fungsi tingkat atas agar AnimatedVisibility tidak terikat scope Row/Column. */
@Composable
private fun MessageToast(message: String?) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 2 },
        exit = fadeOut(tween(200)),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 640.dp),
        ) {
            Text(
                message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun Sidebar(expanded: Boolean, current: Dest?, report: DeviceReport, onSelect: (Dest) -> Unit) {
    val width by animateDpAsState(if (expanded) 272.dp else 92.dp, tween(280), label = "sidebarWidth")
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(width)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SimCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (expanded) {
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "eSIM Manager",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            report.deviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dest.entries.forEach { dest ->
                    SidebarItem(dest = dest, selected = dest == current, expanded = expanded, onClick = { onSelect(dest) })
                }
            }

            Spacer(Modifier.weight(1f))

            if (expanded) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "MODE PERANGKAT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (report.mode == OperatingMode.FULL_ESIM) "eSIM penuh" else "Pengelola via Wi-Fi",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (report.mode == OperatingMode.FULL_ESIM) {
                                "eUICC aktif terdeteksi."
                            } else {
                                "Tanpa eUICC — instalasi langsung tidak tersedia."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun SidebarItem(dest: Dest, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        tween(220),
        label = "navBackground",
    )
    val foreground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(220),
        label = "navForeground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = if (expanded) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(dest.icon, contentDescription = dest.label, tint = foreground, modifier = Modifier.size(22.dp))
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                dest.label,
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Navigasi bawah untuk layar sempit (ponsel); tablet memakai sidebar. */
@Composable
private fun BottomBar(current: Dest?, onSelect: (Dest) -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Dest.entries.forEach { dest ->
                val selected = dest == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(dest) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        dest.icon,
                        contentDescription = dest.label,
                        tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EuiccEventDialog(event: EuiccEvent, vm: MainViewModel) {
    val activity = LocalContext.current.findActivity()
    AlertDialog(
        onDismissRequest = { vm.dismissEuiccEvent() },
        title = {
            Text(
                when {
                    event.isSuccess -> "Dikonfirmasi sistem"
                    event.isResolvable -> "Perlu persetujuan Anda"
                    else -> "Ditolak sistem"
                },
            )
        },
        text = { Text(EuiccController.describe(event)) },
        confirmButton = {
            if (event.isResolvable && activity != null) {
                TextButton(onClick = { vm.resolveEuicc(activity, event) }) { Text("Lanjutkan di sistem") }
            } else {
                TextButton(onClick = { vm.dismissEuiccEvent() }) { Text("Tutup") }
            }
        },
        dismissButton = {
            if (event.isResolvable) {
                TextButton(onClick = { vm.dismissEuiccEvent() }) { Text("Batal") }
            } else if (!event.isSuccess && activity != null) {
                TextButton(
                    onClick = {
                        vm.dismissEuiccEvent()
                        vm.openSystemEsimSettings(activity)
                    },
                ) { Text("Buka setelan eSIM") }
            }
        },
    )
}
