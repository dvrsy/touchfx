package app.touchfx

import android.app.Application
import app.touchfx.engine.LightEngine

class LedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LightEngine.init(this)
    }
}
