# UltimateGadget

Fork AGPL-3.0 de Gadgetbridge (`com.qtekfun.ultimategadget`), séptima app de la familia Ultimate. Objetivo: Gadgetbridge con interfaz nueva (módulo `:ui`, Compose) y modo golf, mapas y actualizaciones A-GNSS funcionando en relojes Huawei sin Huawei Health ni HMS en el móvil. Reloj obligatorio: Huawei Watch GT Runner 2.

Fuente de verdad: `docs/spec/00-spec.md` (decisiones, fases, conflicto A/B/C) y `docs/spec/01-prompt-fase0-spike.md`.

## Decisiones de la spec (no cambiar sin preguntar)
- Núcleo de Gadgetbridge: se modifica lo mínimo para poder integrar upstream. La UI nueva va en `:ui`, aparte.
- Se conservan todas las marcas que trae Gadgetbridge.
- Prioridad: primero protocolo y golf; la UI nueva (Fase 4) no empieza hasta que golf y mapas estén demostrados.
- Privacidad: cero Huawei Health y cero HMS en el móvil. Sin analítica ni servicios de terceros no listados en la spec.
- Mapas: conflicto A/B/C abierto hasta cerrar la Fase 0. Preferida OSM (`golf=*`) en repo de datos aparte.
- A-GNSS: descarga programada (solo Wi-Fi, configurable) y botón manual; indicador "válido hasta…".
- Sincronización: Nextcloud con credenciales cifradas (Fase 5).
- Licencia: AGPL-3.0, código publicado. No se redistribuyen binarios, mapas ni claves de Huawei.

Fases: 0 spike, 1 golf mínimo + A-GNSS, 2 mapas, 3 rondas, 4 UI, 5 Nextcloud, 6 releases. Cada fase termina con criterios de aceptación comprobados.

## Reglas de trabajo
- Spec-driven. Cambios pequeños y revisables, un commit por tarea, mensajes claros.
- Si una decisión de la spec no se puede cumplir, parar y preguntar con opciones.
- Nunca escribir credenciales, tokens ni claves de Huawei en el repo, logs ni informes. Capturas y análisis fuera del repo (`~/re/`) y en `.gitignore`.
- Nada en `/tmp` (ni el scratchpad): descargas, clones y capturas van a `~/re/<tema>/`.
- No factory reset, no desemparejar el reloj ni tocar el móvil sin preguntar.
- Análisis solo sobre el reloj, móvil y cuenta propios, por interoperabilidad. Si algo exige romper cifrado o autenticación de Huawei más allá de observar la propia sesión, parar y consultar. No hay root en los móviles de prueba.

## Build y tests
- JDK 21, Gradle wrapper 9.x, SDK Android con plataforma 37.
- Build: `./gradlew assembleMainlineDebug`
- Tests: `./gradlew testMainlineDebugUnitTest`
- Los tests de `TemperatureUtilsTest` esperan locale inglés; con `es_ES` fallan 2 (formato `36,5`). Localmente: `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`. El CI ya lo fija.
- Línea base verificada sobre upstream `c8d0913`: compila; 1933 tests, 2 fallos solo por locale.
- CI: `.github/workflows/ci.yml` (build + tests). `.woodpecker/` es de upstream y no se usa.

## Carpetas y cambios
- No tocar sin necesidad: `app/src/main/java/.../devices/` y `.../service/devices/` de otras marcas, `external/`, `GBDaoGenerator/`, traducciones (`values-*`).
- Cambios propios hasta ahora: `applicationId`, autoridad del provider de Pebble y nombre visible, solo en el flavor `mainline`, para instalar junto a Gadgetbridge.
- Código nuevo de Huawei golf/A-GNSS: servicios P2P nuevos junto al soporte existente (`service/devices/huawei/p2p/`), aislados y sin reescribir el núcleo.
- Upstream: remote `upstream` (Codeberg, rama `master`); integrar con merges periódicos.
