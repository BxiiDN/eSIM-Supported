package com.bxiidn.esimmanager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bxiidn.esimmanager.ui.AppShell
import com.bxiidn.esimmanager.ui.theme.EsimManagerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        setContent {
            EsimManagerTheme {
                AppShell()
            }
        }
    }

    /**
     * Meminta mode layar dengan refresh rate tertinggi pada resolusi yang sedang dipakai
     * (144 Hz pada Xiaomi Pad 6) agar animasi dan gulir tetap halus.
     */
    private fun preferHighestRefreshRate() {
        try {
            val current = display?.mode ?: return
            val best = display?.supportedModes
                ?.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                ?.maxByOrNull { it.refreshRate }
                ?: return
            if (best.modeId != current.modeId || window.attributes.preferredDisplayModeId != best.modeId) {
                val params = window.attributes
                params.preferredDisplayModeId = best.modeId
                window.attributes = params
            }
        } catch (e: Exception) {
            // Perangkat tanpa dukungan pemilihan mode: biarkan bawaan sistem.
        }
    }
}
