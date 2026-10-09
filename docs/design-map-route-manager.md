# Diseño: Gestor de Mapas y Rutas (UI amigable)

Estado: propuesta de diseño. No implementa código todavía.

Objetivo: que el usuario descargue mapas offline (por regiones) y envíe rutas GPX al reloj Huawei
desde una UI propia, sin usar el menú de "instalar firmware". Base ya validada en hardware real:
`OpenStreetMap → offvmp → <mapId>.bin → reloj` y `GPX → reloj` funcionan con el soporte que ya trae
el núcleo; esto es puramente trabajo de UI + un repo de datos, respetando la spec (UI nueva en `:ui`
Compose, núcleo tocado lo mínimo).

---

## 1. Restricción clave: generación de los `.bin`

El `.bin` del mapa se genera con **offvmp** (Python, MIT): `OSM .pbf/GeoJSON → build_country.py →
encode.py → <mapId>.bin`. Una app Android **no puede ejecutar ese pipeline** (es Python + requiere
osmium/datos OSM/CPU/almacenamiento, y portar el encoder a Kotlin/NDK sería reescribir y mantener un
códec no trivial, con riesgo de divergencia de formato y de generar ficheros corruptos que —según la
investigación de Me7c7— pueden hacer que el reloj se comporte mal).

### Opciones evaluadas

- **(a) Repo de datos aparte con `.bin` pregenerados por región + índice descargable.** La app solo
  **descarga** un fichero ya hecho y lo envía al reloj. Es el modelo que la spec ya cita
  (UltimatePhone/UltimateMaps). La app nunca ejecuta offvmp.
- **(b) Herramienta/CI que regenera el repo de datos periódicamente** (GitHub Actions corriendo
  offvmp sobre extractos de Geofabrik, publicando los `.bin` + `index.json` como release/pages).
- **(c) Portar el encoder al dispositivo.** Descartada: complejidad alta, mantenimiento del códec,
  consumo en el móvil, y el riesgo de corrupción es peor en cliente que en un pipeline controlado.

### Recomendación: **(a) + (b)**

Un repositorio de datos independiente (p.ej. `UltimateGadget-maps`) contiene los `.bin` por región y
un `index.json`. Un workflow de CI los regenera (mensualmente o bajo demanda) ejecutando offvmp sobre
extractos OSM públicos. La app consume el índice, descarga la región elegida y la instala reutilizando
el flujo del núcleo. Ventajas: la app no ejecuta código pesado ni de terceros; la generación es
auditable y reproducible en CI; el formato se controla en un solo sitio; el reloj solo recibe ficheros
ya validados. El contorno (`_contour`) y el mapa `global` entran en el mismo índice como variantes.

> Rutas: **no dependen del repo de datos**. Un GPX se importa del propio móvil y se envía directamente.
> Por eso la Fase 1 ataca rutas (valor inmediato, cero infraestructura).

---

## 2. Puntos de enganche en el núcleo (ya existen, se reutilizan)

La UI nueva **no reimplementa el protocolo**: construye un `Uri` a un fichero local y llama al mismo
punto de entrada que hoy usa el instalador.

- Punto de entrada único: `GBDevice`/servicio → `GBDeviceService.onInstallApp(Uri, Bundle)`
  (`app/.../impl/GBDeviceService.java:319`) → `HuaweiBRSupport.onInstallApp`
  (`app/.../service/devices/huawei/HuaweiBRSupport.java:166`) →
  `HuaweiSupportProvider.onInstallApp(Uri, Bundle)` (`app/.../service/devices/huawei/HuaweiSupportProvider.java:2596`).
  Ese método ya **despacha** según el contenido del Uri:
  - **GPX/ruta**: `HuaweiGpxRouteInstallHandler.isValid()` → `HuaweiGPSTrackConverter.getTrack(...)` →
    `HuaweiP2PFitnessData.sendTrack(track)` (`HuaweiSupportProvider.java:2599-2606`). El nombre de la
    ruta y los flags de navegación van en el `Bundle` vía `HuaweiGpxRouteInstallHandler.EXTRA_TRACK_NAME`
    / `EXTRA_NAVIGATION_ENABLED` / `EXTRA_STRAIGHT_NAVIGATION_ENABLED`
    (`app/.../devices/GpxRouteInstallHandler.java:32-34`; validez = GPX parseable,
    `GpxRouteInstallHandler.java:74`; compatibilidad = `supportsRouteUpload()`,
    `HuaweiGpxRouteInstallHandler.isCompatible`).
  - **Mapa offline**: `HuaweiFwHelper.isOfflineMap` (`HuaweiFwHelper.java:82,100` → magic `offvmp`,
    nombre `<mapId>.bin`/`_contour`/`global`) → `HuaweiP2PMapkitService.startUpload(fileName, uriHelper)`
    (`HuaweiSupportProvider.java:2609-2612`).
- Servicio mapkit (`service/devices/huawei/p2p/HuaweiP2PMapkitService.java`):
  - `startUpload`→`startUpload2`→`uploadMapToDevice` (ping `0xca`, consulta espacio, negocia y sube;
    `:253,:220,:253`), `MapInfo{mapId,mapType(0 regular/1 contour/2 global),version}` (`:134`),
    `queryUploadedMaps()` (`:121`) cuya respuesta se parsea en `handleMapList` (`:329`) y lista los
    mapas que el reloj ya tiene (id/type/version) — **esto da el estado "en-reloj/actualizable"**,
    `queryFreeSpace()` (`:109`), y `deleteMapsList`/`deleteMaps` (`:162,:189`) para borrar del reloj.
- Validación/instalación: `HuaweiInstallHandler.validateInstallation` rama offline map
  (`app/.../devices/huawei/HuaweiInstallHandler.java:116-153`) comprueba conectado/inicializado y
  `supportsOfflineContourMap()` para contornos.
- Capacidades (`app/.../devices/huawei/HuaweiState.java`): `supportsOfflineMap()` bit 201 (`:868`),
  `supportsOfflineContourMap()` bit 202 (`:874`), `supportsRouteUpload()` bit 70 (`:880`),
  `supportsRouteV2()` bit 216 (`:886`). Verificado: GT Runner 2 = las cuatro; GT7 = solo offlineMap.
- Tabla de ids región→mapId: `~/re/external/HDataResearch/offvmp/docs/filenames.md` (fuente del
  `index.json`; país, región, `File name(w/o .bin)` = mapId decimal).

**Gap pequeño a cubrir en el núcleo (mínimo):** hoy `queryUploadedMaps()` sólo loguea la lista
(`handleMapList`, `:343`). Para pintar estado en la UI hace falta exponer esa lista (callback o
`LiveData`/evento con `List<MapInfo>`). Y `deleteMaps()` está cableado a un id de ejemplo (`:189`):
añadir un `deleteMap(mapId, mapType)` público. Nada más del núcleo cambia.

---

## 3. UX (módulo `:ui`, Compose)

Dos pantallas accesibles desde la ficha del dispositivo (entrada condicionada a `supportsOfflineMap()`
y `supportsRouteUpload()`).

### Pantalla "Mapas"

- Lista agrupada por país (desde `index.json`), buscador por nombre.
- Cada región muestra: nombre, tamaño, versión disponible, y **estado** derivado de cruzar el índice
  con `queryUploadedMaps()`:
  - *No descargado* → botón **Descargar**.
  - *Descargado, no en reloj* → **Enviar al reloj**.
  - *En reloj* (version == índice) → **Actualizado** + **Borrar del reloj**.
  - *En reloj, version < índice* → **Actualizar**.
- Toggle "incluir relieve (contorno)" si `supportsOfflineContourMap()`.
- Progreso de descarga (HTTP) y de envío (reusa `onUploadProgress`).
- Acciones: Descargar (a cache de la app), Enviar (construye `Uri` del fichero y llama `onInstallApp`),
  Borrar local, Borrar del reloj (`deleteMap`).

### Pantalla "Rutas"

- Lista de GPX importados (guardados en almacenamiento de la app).
- **Importar** desde fichero (SAF `ACTION_OPEN_DOCUMENT`), opción de **crear** una ruta simple
  (futuro: dibujar en mapa).
- Por ruta: nombre, nº de puntos, distancia; botones **Enviar al reloj** (con toggles de navegación →
  `EXTRA_NAVIGATION_ENABLED`/`EXTRA_STRAIGHT_NAVIGATION_ENABLED`) y **Borrar**.
- Enviar = `onInstallApp(uriDelGpx, bundleConNombreYFlags)`.

Núcleo tocado lo mínimo: sólo exponer la lista de mapas del reloj y un `deleteMap`. Todo lo demás vive
en `:ui`.

---

## 4. Plan por fases (pequeño y revisable)

- **Fase 1 — Rutas locales (valor inmediato, sin repo de datos).**
  Pantalla "Rutas": importar GPX del móvil, listarlos, enviar al reloj reutilizando
  `HuaweiGpxRouteInstallHandler`/`onInstallApp`, borrar local. Entrada visible sólo si
  `supportsRouteUpload()`.
  *Aceptación:* importar un GPX y enviarlo; aparece en el reloj como ruta navegable; sin pasar por
  "instalar firmware".

- **Fase 2 — Enviar mapas ya descargados.**
  Pantalla "Mapas" que lista ficheros `<mapId>.bin` presentes en una carpeta local y permite enviarlos
  (reusa `startUpload`). Sin repo de datos todavía (el usuario coloca los `.bin`).
  *Aceptación:* enviar un `.bin` local y verlo listado por `queryUploadedMaps()` en el reloj.

- **Fase 3 — Estado "en reloj" + borrado.**
  Exponer `queryUploadedMaps()` a la UI y añadir `deleteMap(mapId,mapType)`. Mostrar estado
  descargado/en-reloj/actualizable y permitir borrar del reloj.
  *Aceptación:* la UI refleja correctamente qué mapas tiene el reloj y su versión; borrar funciona.

- **Fase 4 — Repo de datos + descarga en-app.**
  Publicar `index.json` + `.bin` (repo `UltimateGadget-maps`, CI con offvmp). La app descarga por
  región desde la UI.
  *Aceptación:* elegir "Comunidad de Madrid" en la lista, descargar y enviar sin ficheros manuales.

- **Fase 5 — CI de regeneración.**
  Workflow que reconstruye el repo de datos desde Geofabrik periódicamente y actualiza `index.json`
  (versión = YYMMDDHH, igual que `encode.py`).
  *Aceptación:* un run de CI produce mapas nuevos y la app ve versión actualizable.

---

## 5. Esquema del índice del repo de datos (`index.json`)

```json
{
  "schema": 1,
  "generated": "2026-10-09T07:00:00Z",
  "base_url": "https://<host>/ultimategadget-maps/v1/",
  "regions": [
    {
      "country": "Spain",
      "region": "Community of Madrid",
      "map_id": "1113715378388944103",
      "version": 26100907,
      "bbox": [-4.579, 39.885, -3.053, 41.165],
      "map":     { "path": "es/1113715378388944103.bin",         "bytes": 943334 },
      "contour": { "path": "es/1113715378388944103_contour.bin", "bytes": 512000 }
    }
  ]
}
```

- `map_id` = `File name(w/o .bin)` de `offvmp/docs/filenames.md` (string: cabe en long pero se maneja
  como texto para no perder precisión en JSON).
- `version` = entero YYMMDDHH, el mismo que escribe `encode.py` y que devuelve el reloj en
  `queryUploadedMaps()` → comparación directa para "actualizable".
- `contour` opcional (sólo si existe y el reloj lo soporta).
- Consumo en la app: descargar `index.json` (cacheado), agrupar por `country`, cruzar `map_id`+`version`
  con la lista del reloj para el estado, descargar `base_url`+`path` a cache y enviar.

---

## 6. Ficheros de la app a crear/tocar

Crear (módulo `:ui`, nuevo):
- `ui/maps/MapManagerScreen.kt`, `MapManagerViewModel.kt`, `MapRepositoryIndex.kt` (modelo + fetch de
  `index.json`), `MapDownloadWorker` (descarga a cache).
- `ui/routes/RouteManagerScreen.kt`, `RouteManagerViewModel.kt`, `GpxImport.kt`.
- Navegación/entrada desde la ficha de dispositivo (condicionada a capacidades).

Tocar en núcleo (mínimo, Fase 3):
- `service/devices/huawei/p2p/HuaweiP2PMapkitService.java`: exponer la lista de `handleMapList`
  (callback/evento) y añadir `deleteMap(long mapId, byte mapType)` público (generalizar `deleteMaps`).
- Posible: un pequeño `GBDeviceEvent`/evento para publicar la lista de mapas del reloj a la UI.

Sin cambios en el flujo de `onInstallApp`: la UI nueva lo reutiliza pasando el `Uri` del fichero local.
