package pe.upeu.ecorecicla

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.AlmacenSeguro
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.remote.ApiConfig
import pe.upeu.ecorecicla.domain.repository.MonitorConexion

/** Lo que solo existe en Android: motor HTTP, URL base, almacen cifrado, driver y red. */
val platformModule = module {
    single<HttpClientEngine> { OkHttp.create() }
    single {
        ApiConfig(
            // 10.0.2.2 = localhost de la PC vista desde el emulador. En un celular fisico,
            // reemplazar en build.gradle.kts (buildConfigField URL_BASE) por la IP de la PC.
            urlBase = BuildConfig.URL_BASE,
            // Registro de cabeceras solo en depuracion; en release, ninguno.
            registroHttp = BuildConfig.DEBUG
        )
    }
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    single<BaseLocal> { BaseLocalAndroid(androidContext()) }
    single<MonitorConexion> { MonitorConexionAndroid(androidContext()) }
}
