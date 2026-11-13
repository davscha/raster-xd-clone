package de.davscha.raster

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.davscha.raster.settings.AppSettings
import de.davscha.raster.ui.RasterApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Die App ist immer dunkel: helle Symbole in der Statusleiste, auch im hellen Gerätemodus
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val settings = AppSettings(applicationContext)
        setContent { RasterApp(settings) }
    }
}
