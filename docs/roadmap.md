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
- [~] En curso (agentes): bug atrás-en-ajustes, insets del mapa, export GPX/FIT por entreno, sueño avanzado, gestor de música.
- [ ] Snap-to-roads en el planificador (OSRM) y edición de waypoints.
- [ ] Reemplazar/retematizar "Ajustes del dispositivo" (aún UI vieja de preferencias).
- [x] Informes semanales/mensuales con gráficas. Objetivos, rachas y medallas. [ ] export a PDF pendiente.
- [ ] Puntuaciones derivadas en local: readiness/energía (tipo Body Battery), carga de entreno, VO2max, HRV, PAI.
- [~] Sueño avanzado: fases, puntuación, SpO2 nocturno, siestas (agente en curso).

## Datos y privacidad (diferenciador)
- [x] Notificaciones: apps que notifican al reloj + No molestar (horario/días) en estética Ultimate.
- [x] Opciones del dispositivo agrupadas por secciones (coherente con apps comerciales).
- [ ] Health Connect (compartir salud con otras apps, en local).
- [ ] Nextcloud (Fase 5 de la spec) y destinos opcionales (Strava/openScale).
- [x] Export/Import de la copia de la base (local). [ ] GPX/FIT por entreno e import de Huawei Health (pendiente).
- [ ] Dashboard multi-dispositivo (agregar y comparar).

## Dispositivo
- [x] Vitrina/instalador de esferas (watchfaces). [ ] Diseñador básico (later).
- [x] Alarmas, recordatorios y relojes mundiales (estética Ultimate). [ ] Temporizadores y gestión de apps/widgets del reloj.
- [~] Gestor de música (agente en curso). [ ] Playlists.

## Golf (nicho propio)
- [ ] Biblioteca de campos desde OpenStreetMap (golf=*), scorecard, seguimiento de golpes, distancias.
- [ ] Desbloquear el formato del fichero de campo/efemérides Huawei (requiere muestra con dispositivo rooteado).

## Ambicioso / later
- [ ] Widgets de pantalla de inicio y Quick Settings tiles.
- [ ] Reglas de notificaciones (filtros por app/horario).
- [ ] Material You dinámico opcional.
- [x] UI nueva como pantalla de arranque (con gateo de primer-inicio/permisos).
