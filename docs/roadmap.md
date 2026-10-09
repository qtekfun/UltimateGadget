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
- [x] Mapas del móvil: importación de PMTiles + app compañera :mapdownloader que los descarga del catálogo de ultimate-maps-data (la app principal es offline).
- [x] Pantallas nuevas: notificaciones, esferas, informes/objetivos, alarmas/recordatorios/relojes mundiales, sueño avanzado, gestor de música, export GPX/FIT por entreno, rendimiento (PAI/carga/VO2max/HRV), temporizadores (locales del móvil), widgets de inicio + QS tiles.
- [x] Bugs: atrás-en-ajustes ya no cierra la app; insets del mapa; rutas con coma decimal; activación de esferas (confirm 0x05).
- [x] Ajustes del dispositivo: piel oscura de marca + categorías (solo Huawei).
- [ ] Snap-to-roads en el planificador (OSRM) y edición de waypoints.
- [x] "Ajustes del dispositivo": piel oscura de marca + categorías (Huawei).
- [x] Informes semanales/mensuales con gráficas. Objetivos, rachas y medallas. [ ] export a PDF pendiente.
- [x] Puntuaciones derivadas: readiness, carga (TRIMP/ACWR), VO2max, HRV, PAI.
- [x] Sueño avanzado: fases, puntuación, SpO2 nocturno, siestas.

## Datos y privacidad (diferenciador)
- [x] Notificaciones: apps que notifican al reloj + No molestar (horario/días) en estética Ultimate.
- [x] Opciones del dispositivo agrupadas por secciones (coherente con apps comerciales).
- [ ] Health Connect (compartir salud con otras apps, en local).
- [ ] Nextcloud (Fase 5 de la spec) y destinos opcionales (Strava/openScale).
- [x] Export/Import de la copia de la base (local). [ ] GPX/FIT por entreno e import de Huawei Health (pendiente).
- [ ] Dashboard multi-dispositivo (agregar y comparar).

## Dispositivo
- [x] Vitrina/instalador de esferas (watchfaces). [ ] Diseñador básico (later).
- [x] Alarmas, recordatorios, relojes mundiales y temporizadores (local). [ ] Gestión de apps/widgets del reloj.
- [x] Gestor de música (listar/subir/borrar). [ ] Playlists.

## Golf (nicho propio)
- [ ] Biblioteca de campos desde OpenStreetMap (golf=*), scorecard, seguimiento de golpes, distancias.
- [ ] Desbloquear el formato del fichero de campo/efemérides Huawei (requiere muestra con dispositivo rooteado).

## Ambicioso / later
- [x] Widget de inicio (pasos/batería/FC) y QS tiles (sincronizar, buscar reloj).
- [ ] Reglas de notificaciones (filtros por app/horario).
- [ ] Material You dinámico opcional.
- [x] UI nueva como pantalla de arranque (con gateo de primer-inicio/permisos).
