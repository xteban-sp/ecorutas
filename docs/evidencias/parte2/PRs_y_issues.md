# Solicitudes de incorporación (PR) e issues

Las ramas están apiladas: cada una sale de la anterior. **Fusionarlas en este orden**, siempre hacia `develop` y con «Create a merge commit» (equivale a `--no-ff`).

## ecoapi (https://github.com/xteban-sp/ecoapi)

| # | Rama → base | Título | Descripción |
|---|---|---|---|
| 1 | `sc-b-istana` → `develop` | SC-B: rol AUDITOR de solo lectura | Agrega el rol AUDITOR con PUNTO_LEER y PESAJE_LEER y el usuario auditor1 mediante la migración V3, sin tocar SecurityConfig, el filtro JWT ni los controladores. Probado: lecturas 200 y todas las escrituras 403 (evidencias/parte2/sc-b_auditor.txt). |
| 2 | `sc-c-istana` → `develop` | SC-C: humedad estimada del pesaje | Agrega la columna humedad_estimada (V4, con CHECK 0–100), la expone en peticiones y respuestas y actualiza la semilla. Probado: 201 con humedad, 400 con detalle en humedadEstimada al enviar 130 y corrección 200. |
| 3 | `docs/parte-ii` → `develop` | Documentar la Parte II en el README | Describe SC-B y SC-C en el servicio: archivos tocados y cómo se verificaron. |
| 4 | `develop` → `main` | Release: producto con la Parte II | Lleva a main las solicitudes de cambio ya integradas en develop. |

## ecorutas (https://github.com/xteban-sp/ecorutas)

| # | Rama → base | Título | Descripción |
|---|---|---|---|
| 1 | `sc-a-istana` → `develop` | SC-A: filtro «Solo pendientes de pesar» | El filtro se resuelve en la consulta SQL listarPuntos y se combina con el buscador en el caso de uso; el composable solo muestra el chip. Probado: FiltroPendientesTest y en el emulador con modo avión. |
| 2 | `sc-d-istana` → `develop` | SC-D: distintivo de pesajes pendientes | El número sale de la consulta observable contarPendientes (no hay contador en el ViewModel). Probado: sube a 1 al registrar sin red y vuelve a 0 al sincronizar (OfflineTest y emulador). |
| 3 | `sc-c-istana` → `develop` | SC-C: humedad estimada de punta a punta | Recorre DTO, conversión al dominio, esquema local con migración 1.sqm (base v2) y las dos pantallas. Probado: validación bajo el campo, registro sin red y llegada al servidor (humedadEstimada 3.5). |
| 4 | `sc-b-istana` → `develop` | SC-B y documentación de la Parte II | Explica por qué el rol AUDITOR no requiere cambios en la app (todo se decide por privilegio) y documenta las cuatro solicitudes en el README. |
| 5 | `develop` → `main` | Release: producto con la Parte II | Lleva a main las solicitudes de cambio integradas. |

## Issues (en ambos repos)

1. **Seguridad: login JWT, filtro y privilegios** — rama `feature/seguridad-jwt`.
2. **CRUD de pesajes y reglas RN-01 a RN-04** — `feature/crud-pesajes` (ecoapi) / `feature/interfaz` (ecorutas).
3. **Persistencia local** — `feature/esquema-datos` (ecoapi) / `feature/offline-first` (ecorutas).
4. **Sincronización offline-first** — `feature/offline-first` y `fix/sincronizacion-red-validada` (ecorutas).
5. **Pruebas** — `feature/pruebas` (ecorutas).
