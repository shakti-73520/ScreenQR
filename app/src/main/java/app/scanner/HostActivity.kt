package app.scanner

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.scanner.ui.PrismViewModel
import app.scanner.ui.common.FloatingPrismNavBar
import app.scanner.ui.common.PrismAtmosphericBackground
import app.scanner.ui.common.PrismDestination
import app.scanner.ui.common.RichResultSheetContent
import app.scanner.ui.screens.CreateScreen
import app.scanner.ui.screens.ScanScreen
import app.scanner.ui.screens.SettingsScreen
import app.scanner.ui.screens.VaultScreen
import app.scanner.ui.theme.LocalPrismColors
import app.scanner.ui.theme.PrismTheme

class HostActivity : ComponentActivity() {

    private val viewModel: PrismViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialRoute = resolveInitialRoute(intent)
        val returnScanResult = isScanForResult(intent)
        handleIncomingImageShare(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            PrismTheme(themeMode = settings.themeMode) {
                PrismMainShell(
                    viewModel = viewModel,
                    initialRoute = initialRoute,
                    returnScanResult = returnScanResult,
                    onReturnResultToCaller = { rawText, formatName ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().apply {
                                putExtra("SCAN_RESULT", rawText)
                                putExtra("SCAN_RESULT_FORMAT", formatName)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingImageShare(intent)
    }

    private fun resolveInitialRoute(intent: Intent?): String {
        return when (intent?.action) {
            "app.scanner.action.CREATE" -> PrismDestination.CREATE.route
            "app.scanner.action.HISTORY", "app.scanner.action.LISTS" -> PrismDestination.VAULT.route
            else -> PrismDestination.SCAN.route
        }
    }

    private fun isScanForResult(intent: Intent?): Boolean {
        return callingActivity != null &&
            (intent?.action == "app.scanner.action.SCAN" ||
                intent?.action == "com.google.zxing.client.android.SCAN")
    }

    private fun handleIncomingImageShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) {
                viewModel.scanImageUri(uri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrismMainShell(
    viewModel: PrismViewModel,
    initialRoute: String,
    returnScanResult: Boolean,
    onReturnResultToCaller: (String, String) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val activeResult by viewModel.activeResult.collectAsStateWithLifecycle()
    val activeSource by viewModel.activeSourceBadge.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val prism = LocalPrismColors.current

    LaunchedEffect(statusMsg) {
        statusMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(activeResult, returnScanResult) {
        val res = activeResult
        if (returnScanResult && res != null) {
            onReturnResultToCaller(res.rawText, res.format.name)
        }
    }

    PrismAtmosphericBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 86.dp)
                )
            },
            bottomBar = {
                FloatingPrismNavBar(
                    currentRoute = currentRoute,
                    onSelect = { dest ->
                        if (currentRoute != dest.route) {
                            navController.navigate(dest.route) {
                                popUpTo(PrismDestination.SCAN.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = initialRoute,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(PrismDestination.SCAN.route) {
                        ScanScreen(
                            viewModel = viewModel,
                            contentPadding = innerPadding,
                            onOpenSettings = {
                                navController.navigate(PrismDestination.STUDIO.route) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(PrismDestination.CREATE.route) {
                        CreateScreen(
                            viewModel = viewModel,
                            contentPadding = innerPadding
                        )
                    }
                    composable(PrismDestination.VAULT.route) {
                        VaultScreen(
                            viewModel = viewModel,
                            contentPadding = innerPadding
                        )
                    }
                    composable(PrismDestination.STUDIO.route) {
                        SettingsScreen(
                            viewModel = viewModel,
                            contentPadding = innerPadding
                        )
                    }
                }
            }
        }

        if (activeResult != null && !returnScanResult) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissResult() },
                sheetState = sheetState,
                containerColor = prism.canvasSecondary,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .width(44.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(prism.glassBorder)
                    )
                },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Surface(
                    color = prism.canvasSecondary,
                    border = BorderStroke(0.dp, prism.canvasSecondary),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column {
                        RichResultSheetContent(
                            code = activeResult!!,
                            sourceBadge = activeSource,
                            onDismiss = { viewModel.dismissResult() }
                        )
                    }
                }
            }
        }
    }
}
