# EcoRutas — App Android (KMP) para pesajes en ruta

Aplicación del Examen Parcial U2 (Versión B, caso EcoRecicla). Kotlin Multiplatform (objetivo Android), Compose Multiplatform, Ktor Client, Koin, SQLDelight y EncryptedSharedPreferences. Consume el servicio **EcoAPI** (repositorio `ecoapi`).

## Ejecución

1. Levantar el backend: en `ecoapi`, `./gradlew bootRun`.
2. Instalar la app en el emulador:
   ```bash
   ./gradlew :androidApp:installDebug
   ```
   La URL base por defecto es `http://10.0.2.2:8080/api/v1/` (localhost de la PC visto desde el emulador). En un celular físico: `./gradlew :androidApp:installDebug -PurlBase=http://<IP-de-la-PC>:8080/api/v1/`.
3. Pruebas de `commonTest`:
   ```bash
   ./gradlew :shared:testAndroidHostTest
   ```

> La ruta de la carpeta contiene "Móviles" (con tilde). `gradle.properties` incluye `android.overridePathCheck=true` para compilar. Las pruebas JVM no encuentran sus clases con esa ruta en Windows; ejecutarlas desde una unidad virtual sin tilde: `subst X: "<ruta a Examen-U2>"` y luego `cd X:\ecorutas` y `gradlew.bat :shared:testAndroidHostTest`.

Usuarios: `operario1`, `supervisor1`, `admin` (contraseña `Eco2026*`).

## Estructura de paquetes

```
shared/src/commonMain/kotlin/pe/upeu/ecorecicla/
  domain/
    model/        PuntoAcopio, EstadoPesaje, Pesaje, Sesion (modelo 4.5), Ruta, ErrorApi.kt
    repository/   AuthRepository, RutaRepository, PesajeRepository, MonitorConexion (solo contratos)
    usecase/      SesionUseCases, RutaUseCases, PesajeUseCases, SincronizarUseCase.kt
  data/
    remote/       ClienteHttp.kt, ApiConfig.kt, MapeoErrores.kt, EcoApi.kt, AvisosDeSesion.kt
      dto/        Dtos.kt, Conversiones.kt (DTO -> dominio)
    local/        AlmacenSeguro.kt (interfaz), BaseLocal.kt, FuentesLocales.kt, LectorDeToken.kt
    repository/   AuthRepositoryImpl, RutaRepositoryImpl, PesajeRepositoryImpl (offline-first)
  presentation/   sesion/, listado/, detalle/, perfil/, navigation/, theme/, components/
  di/             Modulos.kt: redModule, dataModule, domainModule, presentationModule (+ platformModule)
shared/src/commonMain/sqldelight/pe/upeu/ecorecicla/db/   Ruta.sq, Pesaje.sq, Sincronizacion.sq
shared/src/commonTest/                                     pruebas (MockEngine + SQLite en memoria)
androidApp/src/main/kotlin/pe/upeu/ecorecicla/
  AlmacenSeguroAndroid.kt      preferencias cifradas + Android Keystore
  BaseLocalAndroid.kt          driver de SQLDelight para Android
  MonitorConexionAndroid.kt    ConnectivityManager -> Flow<Boolean>
  PlatformModule.android.kt    motor OkHttp, URL base, almacén, driver, monitor
```

## Contrato del API consumido

| Uso en la app | Petición | Respuesta |
|---|---|---|
| Login | `POST auth/login` | `LoginResponseDto` (token, privilegios) |
| Perfil | `GET auth/me` | `UsuarioDto` |
| Sincronizar (bajar) | `GET rutas/del-dia`, `GET rutas/{id}/pesajes?pagina&tamanio` | `RutaDto`, `PaginaDto<PesajeDto>` |
| Registrar | `POST pesajes` (sin peso neto) | 201 `PesajeDto` con id definitivo |
| Corregir | `PUT pesajes/{id}` | 200 `PesajeDto` |
| Anular | `DELETE pesajes/{id}?motivo=` | 204 (solo se comprueba el código) |
| Cerrar ruta | `POST rutas/{id}/cierre` | 200 `RutaDto` |

Los listados se deserializan como `PaginaDto<T>` (envoltorio común), y los errores como `ErrorDto`.

## Roles y privilegios en la app

La sesión trae los privilegios del token (`Sesion.puede(...)`). La interfaz solo **ofrece** lo permitido: "Registrar pesaje" (PESAJE_CREAR), "Corregir" (PESAJE_EDITAR, y si no tiene PESAJE_EDITAR_TODOS solo en pesajes propios), "Anular" (PESAJE_ANULAR), "Cerrar ruta" (RUTA_CERRAR). Esto es experiencia de usuario: la seguridad la aplica el servicio, que responde 403 aunque se intente igual.

## SC-B: rol AUDITOR en la aplicación

El rol AUDITOR (solo PUNTO_LEER y PESAJE_LEER) se agregó en el servicio con la migración `V3__rol_auditor.sql` del repositorio `ecoapi`. **La aplicación no necesitó cambios de código**: ninguna pantalla pregunta por el nombre del rol, todas consultan `Sesion.puede(privilegio)` con los privilegios del token. Un auditor que inicia sesión ve el perfil con sus 2 privilegios y no recibe ninguna acción de escritura (Registrar, Corregir, Anular, Cerrar ruta); si las intentara, el servicio responde 403.

## Decisiones de arquitectura

- **Capas**: domain (modelo, contratos, casos de uso, sin anotaciones de serialización), data (Ktor, DTO, SQLDelight, repositorios), presentation (ViewModel + `StateFlow<UiState>` + Compose). Inyección con Koin.
- **Un solo HttpClient** (`single` en Koin) con ContentNegotiation (`ignoreUnknownKeys`), HttpTimeout, URL base centralizada (`defaultRequest`) y Logging en `HEADERS` solo en depuración, con `Authorization` saneada.
- **Cabecera de autorización centralizada**: plugin `TokenJwt` en `ClienteHttp.kt` (lee el token del almacén en cada petición, salvo el login). Plugin `SesionVencida`: ante un 401 borra el token y avisa; `SesionViewModel` vuelve al login.
- **Errores tipados**: `ErrorApi` sellado (Validacion, NoAutenticado, SinPrivilegio, NoEncontrado, Conflicto, Servidor, SinConexion, TiempoAgotado). `MapeoErrores.kt` es el único traductor. Conductas: 400 → mensaje bajo el campo; 401 → login; 403 → "no autorizado", la sesión sigue; 404 → informa y refresca; 409 → motivo del servidor; 500 → mensaje + Reintentar.
- **Sesión**: token en `EncryptedSharedPreferences` (llave AES-256 en el Android Keystore). Al reabrir, `LectorDeToken` lee `exp`: vigente → entra directo; vencido → se borra y va al login. Cerrar sesión borra token y base local (equipo compartido).
- **Offline-first**: las pantallas observan SQLDelight con `Flow` (`asFlow().mapToList`). La red solo actualiza la base. Registrar/corregir sin red guarda la fila con `pendiente = 1` y, si es nueva, un **id negativo** (`siguienteIdLocal`: `MIN(COALESCE(MIN(id),0),0) - 1`, funciona con la tabla vacía). Al confirmarse, la fila provisional se sustituye por la definitiva en la misma transacción.
- **Sincronización** (`SincronizarUseCase`, con `Mutex`): primero sube pendientes, luego baja ruta y pesajes (la bajada borra solo filas `pendiente = 0`). Manual (botón) y automática al recuperar la red (`MonitorConexion`), con indicador de progreso y resumen enviados/recibidos/rechazados.

## Pruebas (commonTest)

| Archivo | Qué garantiza |
|---|---|
| `ConversionDtoTest` | DTO → dominio, `@SerialName`, claves desconocidas, envoltorio paginado, el neto nunca se envía |
| `MapeoErroresTest` | 400 con campos, 401 borra token y avisa (y la cabecera se agregó centralmente), 403 conserva la sesión, 404/409/500/sin red |
| `OfflineTest` | Registro sin conexión → pendiente con id -1, -2; sincronización en orden POST → GET y sin duplicados |

Se sustituye la red con `MockEngine` (ktor-client-mock) y la base con `JdbcSqliteDriver.IN_MEMORY` (`androidHostTest`).
