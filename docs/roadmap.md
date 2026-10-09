# UltimateGadget — Roadmap

Ideas de producto frente a Huawei Health, Garmin Connect, Fitbit, Zepp y Samsung Health, con el
enfoque privacidad-primero / 100% local del proyecto. Marca `[x]` lo hecho, `[~]` en curso.

## Hecho / en curso
- [x] UI nueva (Compose, Material 3 oscuro + AMOLED, fuentes Bricolage/Figtree, icono propio).
- [x] Lista de dispositivos con tarjetas + detalle con opciones; estado de conexión en vivo; batería.
- [x] Añadir dispositivo (flujo de emparejamiento).
- [x] Mapas offline del reloj desde OpenStreetMap (generar/instalar/listar/borrar). Plan C de la spec.
- [x] Rutas GPX al reloj (envío) + gestor de mapas/rutas en la estética nueva.
- [x] Dashboard de salud configurable con datos reales de la DB (pasos, pulso/SpO2, sueño/estrés, último entreno) + detalle por tarjeta.
- [x] Ver la ruta de un entreno sobre el mapa.
- [x] Planificador de rutas (dibujar en el móvil → enviar al reloj).
- [x] A-GNSS/AGPS para marcas soportadas por Gadgetbridge (Amazfit/ZeppOS, Garmin). Huawei bloqueado (formato propietario).
- [x] Release firmado por CI (tags vX.Y.Z).
- [x] Ajustes con estética oscura de marca.
- [x] Pull-to-refresh en el dashboard (sincroniza).
- [x] Visor de mapa MapLibre+PMTiles (core reutilizable) en planificador y entrenos.


## Siguiente (alto valor, reaprovecha lo hecho)
- [x] Mapas del móvil: importación de PMTiles + app compañera :mapdownloader que los descarga del catálogo de UltimateMaps-data (la app principal es offline).
- [ ] Snap-to-roads en el planificador (OSRM) y edición de waypoints.
- [ ] Informes semanales/mensuales con gráficas (export a PDF). Objetivos, rachas y medallas.
- [ ] Puntuaciones derivadas en local: readiness/energía (tipo Body Battery), carga de entreno, VO2max, HRV, PAI.
- [ ] Sueño avanzado: fases, puntuación, SpO2 nocturno, siestas.

## Datos y privacidad (diferenciador)
- [ ] Health Connect (compartir salud con otras apps, en local).
- [ ] Nextcloud (Fase 5 de la spec) y destinos opcionales (Strava/openScale).
- [x] Export/Import de la copia de la base (local). [ ] GPX/FIT por entreno e import de Huawei Health (pendiente).
- [ ] Dashboard multi-dispositivo (agregar y comparar).

## Dispositivo
- [ ] Vitrina/instalador de esferas (watchfaces) y, más adelante, diseñador básico.
- [ ] Gestión de apps/widgets del reloj, recordatorios, alarmas, temporizadores, relojes mundiales.
- [ ] Gestor de música y playlists.

## Golf (nicho propio)
- [ ] Biblioteca de campos desde OpenStreetMap (golf=*), scorecard, seguimiento de golpes, distancias.
- [ ] Desbloquear el formato del fichero de campo/efemérides Huawei (requiere muestra con dispositivo rooteado).

## Ambicioso / later
- [ ] Widgets de pantalla de inicio y Quick Settings tiles.
- [ ] Reglas de notificaciones (filtros por app/horario).
- [ ] Material You dinámico opcional.
- [x] UI nueva como pantalla de arranque (con gateo de primer-inicio/permisos).
