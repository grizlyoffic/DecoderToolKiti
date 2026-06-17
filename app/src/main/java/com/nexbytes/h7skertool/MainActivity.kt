package com.nexbytes.h7skertool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.*
import androidx.navigation.compose.*
import com.nexbytes.h7skertool.ui.screens.*
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.viewmodel.AppUiState
import com.nexbytes.h7skertool.viewmodel.CaptureViewModel

class MainActivity : ComponentActivity() {
    private val vm: CaptureViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { H7skERTheme { val state by vm.state.collectAsState(); AppRouter(state, vm) } }
    }
}

@Composable
private fun AppRouter(state: AppUiState, vm: CaptureViewModel) {
    AnimatedContent(
        targetState = when {
            state.needsShizuku   -> "shizuku"
            state.needsPassword  -> "password"
            state.needsClientUrl -> "client_url"
            else                 -> "main"
        },
        transitionSpec = { fadeIn(androidx.compose.animation.core.tween(300)) togetherWith fadeOut(androidx.compose.animation.core.tween(200)) },
        label = "route"
    ) { route ->
        when (route) {
            "shizuku"    -> ShizukuCheckScreen(shizukuAvailable = state.shizukuAvailable, permissionGranted = state.shizukuPermissionGranted, onRequestPermission = vm::requestShizukuPermission, onRetry = vm::checkShizuku)
            "password"   -> PasswordScreen(isVerifying = state.isVerifying, error = state.verifyError, onVerify = vm::verifyPassword)
            "client_url" -> ClientUrlScreen(currentUrl = state.clientUrl, onContinue = vm::setClientUrl)
            "main"       -> MainApp(state, vm)
        }
    }
}

@Composable
private fun MainApp(state: AppUiState, vm: CaptureViewModel) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val requestMap = remember(state.requests) { state.requests.associateBy { it.id } }
    val savedModFiles by vm.savedModFiles.collectAsState()
    val selectedMod by vm.selectedMod.collectAsState()
    val importResult by vm.importResult.collectAsState()
    val showBottomBar = currentRoute in listOf("capture", "settings", "mods", "conversion")

    Scaffold(
        containerColor = DeepBlack,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = CardBlack, tonalElevation = 0.dp) {
                    BottomNavItem.items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, null) },
                            label = { Text(item.label, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreen, selectedTextColor = NeonGreen,
                                indicatorColor = NeonGreen.copy(0.1f),
                                unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { pv ->
        NavHost(navController = navController, startDestination = "capture",
            modifier = Modifier.fillMaxSize().padding(pv)) {

            composable("capture") {
                MainCaptureScreen(
                    state = state, savedMods = savedModFiles,
                    onStartCapture = vm::startCapture, onStopCapture = vm::stopCapture,
                    onSearch = vm::setSearch, onFilterEndpoint = vm::setEndpointFilter,
                    onClearCaptures = vm::clearCaptures,
                    onSaveMod = { endpoint, body -> vm.saveModification(endpoint, body) },
                    onClearLogs = {},
                    onNavigateToDetail = { req -> navController.navigate("detail/${req.id}") },
                    onSelectMod = { vm.selectMod(it) }, selectedMod = selectedMod
                )
            }

            composable("settings") {
                SettingsScreen(
                    state = state,
                    onChangeClientUrl = { navController.navigate("change_url") },
                    onClearCaptures = vm::clearCaptures,
                    onClearMods = vm::clearModifications,
                    onLogout = vm::logout,
                    onResetAll = vm::resetAll
                )
            }

            composable("mods") {
                LaunchedEffect(Unit) { vm.loadModFiles() }
                ModsScreen(
                    mods = savedModFiles,
                    onBack = { navController.popBackStack() },
                    onDeleteMod = vm::deleteModFile,
                    onApplyMod = { mod ->
                        vm.selectMod(mod); vm.applyModToProxy(mod)
                        navController.navigate("capture") { popUpTo("capture") { inclusive = true } }
                    },
                    onToggleEnabled = vm::toggleModEnabled,
                    onExportMod = { mod -> vm.exportMod(mod) },
                    onImportMod = { uri -> vm.importMod(uri) },
                    importResult = importResult,
                    onClearImportResult = vm::clearImportResult
                )
            }

            composable("conversion") {
                ConversionMenuScreen(onBack = { navController.popBackStack() })
            }

            composable("detail/{requestId}", arguments = listOf(navArgument("requestId") { type = NavType.StringType })) { back ->
                val reqId = back.arguments?.getString("requestId") ?: return@composable
                val req = requestMap[reqId]
                if (req == null) { LaunchedEffect(Unit) { navController.popBackStack() }; return@composable }
                RequestDetailScreen(
                    request = req,
                    response = state.responses[req.id],
                    onBack = { navController.popBackStack() },
                    onSaveMod = { endpoint, body -> vm.saveModification(endpoint, body) }
                )
            }

            composable("change_url") {
                ClientUrlScreen(currentUrl = state.clientUrl, onContinue = { url ->
                    vm.setClientUrl(url); navController.popBackStack()
                })
            }
        }
    }
}

private sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Capture    : BottomNavItem("capture",    "CAPTURE",  Icons.Default.CenterFocusStrong)
    object Mods       : BottomNavItem("mods",       "MODZ",     Icons.Default.Build)
    object Conversion : BottomNavItem("conversion", "CONVERT",  Icons.Default.Transform)
    object Settings   : BottomNavItem("settings",   "SETTINGS", Icons.Default.Settings)
    companion object { val items = listOf(Capture, Mods, Conversion, Settings) }
}
