package com.example

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.app.configureAppCheck
import com.example.ui.navigation.ROUTE_ANALYSIS
import com.example.ui.navigation.ROUTE_HUB
import com.example.ui.navigation.ROUTE_STUDIO
import com.example.ui.screens.SpriteAnalysisScreen
import com.example.ui.screens.StudioHubScreen
import com.example.ui.screens.StudioWorkspaceScreen
import com.example.ui.theme.SpriteForgeTheme
import com.example.ui.viewmodel.StudioViewModel
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val studioViewModel: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            SpriteForgeTheme {
                val navController = rememberNavController()
                val projects by studioViewModel.savedProjects.collectAsStateWithLifecycle()
                val analysisState by studioViewModel.analysisState.collectAsStateWithLifecycle()
                val workspaceState by studioViewModel.workspace.collectAsStateWithLifecycle()

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HUB
                ) {
                    composable(ROUTE_HUB) {
                        StudioHubScreen(
                            projects = projects,
                            onImportSpriteUri = { uri, preset ->
                                studioViewModel.startAnalysisFromUri(uri, preset)
                                navController.navigate(ROUTE_ANALYSIS)
                            },
                            onSelectStarter = { templateId, preset ->
                                studioViewModel.startAnalysisFromStarter(templateId, preset)
                                navController.navigate(ROUTE_ANALYSIS)
                            },
                            onOpenProject = { project ->
                                studioViewModel.openSavedProject(project) {
                                    navController.navigate(ROUTE_STUDIO)
                                }
                            },
                            onDeleteProject = studioViewModel::deleteProject
                        )
                    }

                    composable(ROUTE_ANALYSIS) {
                        SpriteAnalysisScreen(
                            state = analysisState,
                            onBack = { navController.popBackStack() },
                            onRetry = studioViewModel::retryAnalysis,
                            onUpdatePreset = studioViewModel::updateAnalysisPreset,
                            onUpdateDensity = studioViewModel::updateAnalysisDensity,
                            onBuildAndOpen = { customName ->
                                studioViewModel.buildAndOpenProject(customName) {
                                    navController.navigate(ROUTE_STUDIO) {
                                        popUpTo(ROUTE_HUB)
                                    }
                                }
                            }
                        )
                    }

                    composable(ROUTE_STUDIO) {
                        StudioWorkspaceScreen(
                            workspace = workspaceState,
                            viewModel = studioViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    // Hardware Gamepad / Analog Stick / D-Pad support for omnidirectional character testing
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        val isGamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        if ((isJoystick || isGamepad) && event.action == MotionEvent.ACTION_MOVE) {
            var x = event.getAxisValue(MotionEvent.AXIS_X)
            var y = event.getAxisValue(MotionEvent.AXIS_Y)
            if (abs(x) < 0.12f) x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            if (abs(y) < 0.12f) y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            studioViewModel.updateJoystick(
                if (abs(x) < 0.12f) 0f else x,
                if (abs(y) < 0.12f) 0f else y
            )
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                studioViewModel.updateJoystick(studioViewModel.workspace.value.joyX, -1f); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                studioViewModel.updateJoystick(studioViewModel.workspace.value.joyX, 1f); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                studioViewModel.updateJoystick(-1f, studioViewModel.workspace.value.joyY); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                studioViewModel.updateJoystick(1f, studioViewModel.workspace.value.joyY); true
            }
            else -> false
        }
        return if (handled) true else super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                studioViewModel.updateJoystick(studioViewModel.workspace.value.joyX, 0f); true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                studioViewModel.updateJoystick(0f, studioViewModel.workspace.value.joyY); true
            }
            else -> false
        }
        return if (handled) true else super.onKeyUp(keyCode, event)
    }
}
