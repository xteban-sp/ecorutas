package pe.upeu.ecorecicla

import android.app.Application
import org.koin.android.ext.koin.androidContext
import pe.upeu.ecorecicla.di.initKoin

/** Arranca Koin una sola vez, antes que cualquier Activity. */
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(platformModule) {
            androidContext(this@MainApplication)
        }
    }
}
