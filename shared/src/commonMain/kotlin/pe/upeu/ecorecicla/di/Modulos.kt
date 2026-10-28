package pe.upeu.ecorecicla.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pe.upeu.ecorecicla.data.local.BaseLocal
import pe.upeu.ecorecicla.data.local.PesajeLocal
import pe.upeu.ecorecicla.data.local.Reloj
import pe.upeu.ecorecicla.data.local.RelojDelSistema
import pe.upeu.ecorecicla.data.local.RutaLocal
import pe.upeu.ecorecicla.data.remote.AvisosDeSesion
import pe.upeu.ecorecicla.data.remote.EcoApi
import pe.upeu.ecorecicla.data.remote.crearClienteHttp
import pe.upeu.ecorecicla.data.repository.AuthRepositoryImpl
import pe.upeu.ecorecicla.data.repository.PesajeRepositoryImpl
import pe.upeu.ecorecicla.data.repository.RutaRepositoryImpl
import pe.upeu.ecorecicla.db.EcoDb
import pe.upeu.ecorecicla.domain.model.Sesion
import pe.upeu.ecorecicla.domain.repository.AuthRepository
import pe.upeu.ecorecicla.domain.repository.PesajeRepository
import pe.upeu.ecorecicla.domain.repository.RutaRepository
import pe.upeu.ecorecicla.domain.usecase.AnularPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.CerrarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.CorregirPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.IniciarSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarFinDeSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPendientesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPesajesUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPuntoUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarPuntosUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarRutaUseCase
import pe.upeu.ecorecicla.domain.usecase.ObservarUltimaSincronizacionUseCase
import pe.upeu.ecorecicla.domain.usecase.ObtenerPerfilUseCase
import pe.upeu.ecorecicla.domain.usecase.ObtenerSesionUseCase
import pe.upeu.ecorecicla.domain.usecase.RegistrarPesajeUseCase
import pe.upeu.ecorecicla.domain.usecase.SincronizarUseCase
import pe.upeu.ecorecicla.presentation.detalle.DetalleViewModel
import pe.upeu.ecorecicla.presentation.listado.RutaViewModel
import pe.upeu.ecorecicla.presentation.perfil.PerfilViewModel
import pe.upeu.ecorecicla.presentation.sesion.SesionViewModel

/**
 * Red: UNA sola instancia de HttpClient para toda la app. El motor, la URL base
 * (ApiConfig) y el almacen del token los aporta el platformModule.
 */
val redModule = module {
    single { AvisosDeSesion() }
    single { crearClienteHttp(engine = get(), config = get(), almacen = get(), avisos = get()) }
    single { EcoApi(get()) }
}

val dataModule = module {
    single<Reloj> { RelojDelSistema }
    // Una sola base local; el driver lo aporta el platformModule (BaseLocalAndroid).
    single { EcoDb(get<BaseLocal>().crearDriver()) }
    single { RutaLocal(get()) }
    single { PesajeLocal(get()) }
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get(), get(), get(), get()) }
    single<RutaRepository> { RutaRepositoryImpl(get(), get(), get(), get()) }
    single<PesajeRepository> { PesajeRepositoryImpl(get(), get(), get(), get(), get()) }
}

val domainModule = module {
    factory { IniciarSesionUseCase(get()) }
    factory { ObtenerSesionUseCase(get()) }
    factory { ObtenerPerfilUseCase(get()) }
    factory { CerrarSesionUseCase(get()) }
    factory { ObservarFinDeSesionUseCase(get()) }
    factory { ObservarRutaUseCase(get()) }
    factory { ObservarPuntosUseCase(get()) }
    factory { ObservarPuntoUseCase(get()) }
    factory { ObservarPesajesUseCase(get()) }
    factory { ObservarUltimaSincronizacionUseCase(get()) }
    factory { ObservarPendientesUseCase(get()) }
    factory { CerrarRutaUseCase(get()) }
    factory { RegistrarPesajeUseCase(get()) }
    factory { CorregirPesajeUseCase(get()) }
    factory { AnularPesajeUseCase(get()) }
    // single: su Mutex evita dos sincronizaciones simultaneas (manual + automatica)
    single { SincronizarUseCase(get(), get()) }
}

val presentationModule = module {
    viewModel { SesionViewModel(get(), get(), get()) }
    viewModel { (sesion: Sesion) -> RutaViewModel(sesion, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (puntoId: Long, sesion: Sesion) ->
        DetalleViewModel(puntoId, sesion, get(), get(), get(), get(), get(), get())
    }
    viewModel { (sesion: Sesion) -> PerfilViewModel(sesion, get(), get(), get()) }
}

/** Arranca Koin. [platformModule] lo define cada aplicacion (PlatformModule.android.kt). */
fun initKoin(platformModule: Module, configuracion: KoinApplication.() -> Unit = {}) {
    startKoin {
        configuracion()
        modules(redModule, dataModule, domainModule, presentationModule, platformModule)
    }
}
