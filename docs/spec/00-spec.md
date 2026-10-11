# UltimateGadget — Especificación inicial

Fork de Gadgetbridge (com.qtekfun.ultimategadget), séptima app de la familia Ultimate. Objetivo: Gadgetbridge con interfaz mucho más cuidada y modo golf funcional en relojes Huawei (descarga de mapas de campos, distancias GPS al green, sincronización de rondas), sin depender de Huawei Health ni de HMS en el móvil.

## 1. Decisiones de la entrevista

| Tema | Decisión |
|---|---|
| Base | Fork de Gadgetbridge con la UI en un módulo aparte (`:ui`, Jetpack Compose). El núcleo se toca lo mínimo para poder integrar cambios de upstream |
| Alcance | Se conserva todo lo que trae Gadgetbridge (todas las marcas) |
| Reloj obligatorio | Huawei Watch GT Runner 2 |
| Prioridad | Primero funcionalidad (protocolo y golf); la UI nueva llega cuando lo difícil esté demostrado |
| Privacidad | Cero Huawei Health y cero HMS en el móvil; todo local salvo lo imprescindible |
| Fuente de mapas | Servidores de Huawei (decisión de partida, ver conflicto abierto) |
| Salud | Actividades y rutas GPS (GPX/FIT), pasos, sueño y pulso con gráficas más bonitas, notificaciones, esferas y apps del reloj |
| Actualizaciones GPS | Quiere las actualizaciones A-GNSS (efemérides/almanaque) del reloj, descargadas y enviadas por UltimateGadget sin Huawei Health |
| Sincronización | Nextcloud (datos y ajustes), con credenciales cifradas como en las otras apps |
| Dispositivos de prueba | Pixel 8, OPPO Find X9 Ultra (ROM global) y un móvil Huawei con Huawei Health y el reloj conectado por adb para el análisis |

## 2. Golf: qué debe hacer

1. Descargar mapas de campos al reloj sin pasar por Huawei Health.
2. Distancias GPS al green funcionando en el reloj con los mapas cargados.
3. Sincronizar rondas (tarjeta, golpes, trayectorias) a la app.
4. Fuente de mapas propia (OpenStreetMap, etiquetas `golf=*`) en un repo de datos aparte, como en UltimatePhone y UltimateMaps.
5. **Problema central:** el modo golf aparece en el reloj solo mientras está conectado a la app original y desaparece al desconectarse. Hay que averiguar qué le envía Huawei Health al reloj para activarlo (capacidades, lista de apps, servicio, flag) y replicarlo desde UltimateGadget.

### Actualizaciones de GPS (A-GNSS)

Objetivo: que el reloj reciba datos de ayuda de satélites actualizados para conseguir el fix GPS rápido, sobre todo antes de golf, carrera o ruta, sin Huawei Health.

- Averiguar de dónde salen los datos (servidores de Huawei, o fuentes abiertas/públicas compatibles con el formato del reloj), su formato, su caducidad y cómo se transfieren al reloj.
- Descarga automática y programada (solo con Wi-Fi, configurable) y botón de actualización manual, con indicador de "datos GPS válidos hasta…".
- Si la fuente es de Huawei, aplica el mismo conflicto y la misma decisión A/B/C de la sección 3. Si existe una fuente abierta equivalente, es preferible.
- Entra en la Fase 1 junto al golf, porque comparte el canal de transferencia de ficheros.

## 3. Conflicto abierto (se resuelve en la Fase 0)

Has pedido a la vez "mapas de los servidores de Huawei" y "cero Huawei Health y cero HMS". Es compatible solo si UltimateGadget habla directamente con los servidores de Huawei sin la app original, y eso probablemente exige iniciar sesión con una cuenta de Huawei y reproducir su autenticación. La Fase 0 debe decidir entre:

- **A.** La app habla con Huawei directamente (cuenta opt-in explícita).
- **B.** La descarga se hace una vez en tu equipo/servidor con tu cuenta y el repo de datos publica los mapas ya convertidos; el móvil no toca Huawei.
- **C.** Mapas desde OpenStreetMap y los de Huawei solo para validar el formato.

La opción B encaja mejor con "cero Huawei en el móvil" y con el modelo de repo de datos que ya usas.

## 4. Licencia y marco legal

- Gadgetbridge es AGPL-3.0: UltimateGadget debe seguir siendo AGPL-3.0 con el código publicado.
- La ingeniería inversa se hace sobre tu propio reloj y tu propia cuenta, por interoperabilidad. Usa preferiblemente una cuenta de Huawei secundaria: hay riesgo de bloqueo por incumplir sus condiciones de uso.
- No se redistribuyen binarios, mapas ni claves de Huawei en el repositorio.

### Servicios de terceros en la app compañera de mapas

La app principal mantiene "cero HMS y cero servicios de terceros". La app compañera de descarga de
mapas (`mapdownloader`, la única con acceso a internet) incluye además una utilidad **opcional** para
obtener la clave Bluetooth (auth key) de relojes Amazfit/Zepp y Xiaomi directamente de los servidores
de Huami/Zepp, replicando el método huami-token al que remite la guía oficial de Gadgetbridge. Decisión
acordada:

- Vive solo en la app de mapas, nunca en la app principal.
- El email y la contraseña de la cuenta Zepp/Amazfit se usan únicamente para el login (HTTPS contra
  Huami/Zepp) y **no se almacenan** en ningún sitio ni se registran en logs.
- Es de uso voluntario y solo sobre la cuenta propia, por interoperabilidad; el host de listado de
  dispositivos es seleccionable por región.

### Variante `play` (todo en uno)

Decisión acordada, a propósito **fuera de la regla** "cero red en la app principal": el flavor `play`
(`com.qtekfun.ultimategadget.play`, se instala junto a `mainline`) lleva permiso `INTERNET` y hace en
la propia app lo que hace la app compañera: descarga de mapas base del catálogo, campos de golf de
Huawei (envío directo al reloj), A-GNSS y la clave Bluetooth Amazfit/Zepp. No es un build de tienda.

- `mainline` y `banglejs` no cambian: `mainline` sigue sin `INTERNET` y con la compañera aparte.
- La lógica de red vive en el módulo `:mapsdl-core`, compartido con la compañera; solo `play` lo
  enlaza. El resto del código accede por el contrato `InAppNetwork` (stub inerte sin red).
- La clave Zepp/Amazfit sigue las mismas reglas: solo cuenta propia, credenciales únicamente para el
  login HTTPS, sin almacenarlas ni registrarlas (pantalla con `FLAG_SECURE`, estado solo en memoria).

## 5. Fases

- **Fase 0 — Spike de protocolo (en el equipo de Z, con el reloj por adb).** Ver `01-prompt-fase0-spike.md`. Entregable: informe con la conclusión sobre cómo se activa el golf y cómo se transfieren los mapas, y la decisión A/B/C.
- **Fase 1 — Golf mínimo y GPS.** Activar el modo golf en el reloj desde UltimateGadget, cargar un campo de prueba y enviar una actualización A-GNSS.
- **Fase 2 — Mapas.** Descarga y conversión de campos, repo de datos aparte, selección de regiones.
- **Fase 3 — Rondas.** Sincronización de tarjetas, golpes y trayectorias; exportación GPX/FIT.
- **Fase 4 — UI nueva.** Módulo `:ui` en Compose: inicio, salud, actividades, golf, esferas, ajustes. Tema oscuro/claro y Material You.
- **Fase 5 — Nextcloud.** Copia de datos y ajustes con credenciales cifradas.
- **Fase 6 — Releases.** CI y publicación como UltimateGallery.

## 6. Criterios de aceptación de la Fase 0

- Documentado el mensaje exacto (servicio/comando y valores) que activa el modo golf en el GT Runner 2.
- Reproducido ese mensaje desde un cliente propio (el código de Gadgetbridge modificado) y el modo golf aparece en el reloj sin Huawei Health conectado.
- Documentado el formato de un mapa de campo y cómo llega al reloj.
- Documentado cómo llegan al reloj las actualizaciones A-GNSS (origen, formato, caducidad, transferencia) y si se puede enviar un paquete desde un cliente propio.
- Decisión A/B/C justificada con la evidencia.

## 7. Backlog / ideas futuras

Ítems anotados para abordar más adelante (sin fase asignada todavía):

- **Actualización de firmware de los relojes Huawei.** Objetivo: poder descargar el firmware con la app sidecar de mapas (que sí tiene internet) y transferirlo al reloj desde UltimateGadget, sin Huawei Health ni HMS. Probablemente requiera ingeniería inversa del origen/formato de descarga y del protocolo de transferencia e instalación en el reloj. Analizar solo sobre el reloj y la cuenta propios, por interoperabilidad; parar y consultar si exige romper cifrado/autenticación de Huawei más allá de observar la sesión propia. No redistribuir binarios de firmware.
- **Editor de watchfaces.** Editor propio en la UI nueva para crear/personalizar esferas (más allá de instalar/activar las que ya existen). Pendiente de definir alcance (elementos, fondos, complicaciones) y el formato de esfera del GT Runner 2.
