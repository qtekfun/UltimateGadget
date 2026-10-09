# Informe Fase 0 — Spike de protocolo (UltimateGadget)

Fecha: 2026-10-09. Reloj objetivo: Huawei Watch GT Runner 2 (también probado un Huawei Watch GT 7).
Todo el análisis es sobre hardware, móvil y cuenta propios, por interoperabilidad. No se ha accedido a
servidores de Huawei, ni se ha instrumentado ni redistribuido su app, código, claves o datos.

## 1. Resumen ejecutivo

- **Golf — activación: RESUELTO.** UltimateGadget habla con la mini-app de golf del reloj por el canal P2P
  (servicio `0x34`, módulo `hw.unitedevice.golf`, paquete `com.huawei.health.wear.golf`) **sin Huawei Health**.
  El reloj responde a mensajes de golf propios y el modo golf aparece en pantalla con el reloj conectado solo a
  UltimateGadget.
- **Golf — carga de campos: PARCIAL.** Documentado el formato de los mensajes (cabecera `GolfMsgHeader` de 36 B +
  payload) y el flujo correcto (el reloj pide por GPS → el teléfono responde la lista de campos con el mismo
  `msgId`). No se ha logrado que el reloj almacene un campo de prueba todavía. El contenido del fichero de mapa
  de campo (`.bin`) sigue sin conocerse (no hay muestra; en relojes vectoriales se envía tal cual).
- **A-GNSS: BLOQUEADO por formato propietario, con vía pública parcial.** Documentada la etiqueta exacta que pide
  el Runner (`higeo/v1/gnssinfo?type=0x0004/HW_AGNSS`, v3) y el formato (RTCM 3 estándar, reproducible desde
  datos públicos). El transporte ya existe en Gadgetbridge. Pero el reloj pide un **conjunto** de ficheros, algunos
  en formato propio de Huawei (`HW_PGNSS_*`) no reproducible, y los exige todos. Hay riesgo documentado de dejar
  el reloj en bucle de arranque con datos incorrectos.
- **Mapas offline (fuera del alcance estricto de Fase 0, pero clave): VÍA LIMPIA ENCONTRADA.** Gadgetbridge ya
  sube mapas offline al reloj; `offvmp` (MIT) los genera desde OpenStreetMap. Cero Huawei.
- **Decisión A/B/C: ver sección 6.**

## 2. Entorno y método

- Fork en `com.qtekfun.ultimategadget`, compila y pasa CI. Sonda de golf y logs de depuración solo en builds debug.
- Captura Bluetooth HCI del propio móvil (Pixel 8) con Huawei Health, y análisis estático del APK propio con `jadx`
  (sin ejecutarlo). Sin root en ningún móvil.

## 3. Golf — activación (criterio de aceptación principal)

Mensaje/transporte exacto, reproducido desde el cliente propio:

- Canal: P2P servicio `0x34`. El golf es una **mini-app del reloj**, direccionada por módulo+paquete+huella:
  - Módulo (phone side): `hw.unitedevice.golf`
  - Paquete (watch app): `com.huawei.health.wear.golf`, huella `SystemApp` (relojes "sport"/vectoriales).
  - El reloj la detecta como vectorial por `productType` 57/78 o característica 159 (Runner 2: vectorial).
- Secuencia observada en nuestro cliente (2026-10-08):
  1. `ping` P2P al paquete de golf → código 202 = existe (control a paquete inexistente → 200).
  2. Mensaje de golf tipo 16 (`GOLF_LOCAL_COURSE_LIST`) → el reloj responde con su lista real (45 campos en el
     Runner, 0 en el GT7).
- **Resultado: el modo golf aparece en el reloj con UltimateGadget y sin Huawei Health.** (Pendiente de verificar
  con un control que es nuestro mensaje, y no la mera conexión, lo que lo hace aparecer.)

Capacidades (servicio `0x01` cmd `0x37`): golf = bit 105, golf-auto-download = bit 285.
- GT Runner 2: golf=SÍ. GT7: golf=NO (el firmware no lo anuncia, aunque su app responda al P2P → modelo recortado
  por software; el móvil no puede añadir una capacidad que el firmware no expone).

## 4. Golf — mapas de campo

- Mensajes (de `GolfHiWearBusinessType`): 8 GPS_INFO_SEND, 9/10 versión, 11 COURSE_FILE, 12 COURSE_LIST_FILE,
  13 MAP_REQUEST, 14 PUSH_SHAKE, 15 DELETE, 16 LOCAL_COURSE_LIST, 20 MAP_KEY, 23/24 CLUB_INFO, 25 TOUCH_DOWNLOAD.
- **El flujo es iniciado por el reloj**: al buscar "campo cercano" envía `GPS_INFO_SEND` (tipo 8) con su posición;
  el teléfono responde `COURSE_LIST_FILE` (tipo 12) con el **mismo** `msgId`. Un push no solicitado se ignora.
  Esto quedó implementado en el fork, pero no se pudo probar (requiere fix GPS real, imposible en interior).
- Origen de los mapas en Huawei Health: nube de Huawei con handshake RSA-OAEP y firma de acceso (`ak=`) sobre
  `higeo/v2/geoFile`. Para relojes vectoriales (Runner 2) el fichero se envía al reloj **tal cual** (sin cifrar a
  nivel de golf); para relojes "lite" va cifrado con AES y clave enviada aparte (`MAP_KEY`).
- **Incógnita pendiente**: el contenido del `.bin` del mapa de campo. No hay muestra. Hipótesis a verificar: podría
  ser el mismo contenedor vectorial que los mapas offline (ver sección 7).

## 5. A-GNSS (actualizaciones GPS)

- Disparador real observado (2026-10-09, al abrir golf en un campo): servicio `0x1f`, el reloj pide la etiqueta
  **`higeo/v1/gnssinfo?type=0x0004/HW_AGNSS`, versión 3**, consultDeviceTime 30000.
- Transporte al reloj: **ya implementado en Gadgetbridge** (`HuaweiEphemerisManager`): un `ephemeris.zip` en el
  almacenamiento externo con `time`, `ephemeris_config.json` y los ficheros por etiqueta; se trocea y envía por BT.
- Formato de los ficheros (investigación pública MIT de Me7c7, issue #3928 / repo HDataResearch):
  - `HW_AGNSS_RTCM_33`: tramas **RTCM 3 estándar** (1019 GPS, 1020 GLONASS, 1042 BeiDou, 1046 Galileo) con CRC-24Q.
    **Reproducible desde efemérides de difusión públicas (RINEX de IGS).**
  - `HW_PGNSS_*`: **formato propietario de Huawei** (derivado de `HiEE.dat` que la app baja del servidor). No
    reproducible sin su algoritmo de conversión.
  - `MTK_EPO_*`: formato EPO de MediaTek (descargable público de `epodownload.mediatek.com`).
- Limitaciones clave (según el mantenedor de Gadgetbridge):
  - El reloj pide los ficheros **en secuencia y requiere todos**; no se puede enviar solo el RTCM.
  - TTL de 1–30 min: inviable distribuir ficheros pregenerados.
  - **El reloj no valida los datos**: datos incorrectos pueden provocar bucle de arranque y requerir reseteo de
    fábrica. Documentado al menos una vez.
- **Estado: bloqueado.** La parte RTCM es pública y reproducible, pero el conjunto incluye el formato propio
  `HW_PGNSS_*` que no sabemos generar, y el reloj los exige todos. Se recomienda seguir el trabajo comunitario de
  #3928 e integrarlo desde upstream cuando madure.

## 6. Decisión A/B/C

Recordatorio: A = la app habla con Huawei; B = descarga previa en equipo propio + repo de datos; C = OpenStreetMap.

- **Mapas de golf → C (OpenStreetMap), con investigación pendiente del formato `.bin`.** B y A implican el
  handshake/firma de Huawei y no encajan con "cero Huawei en el móvil". C es viable si se confirma el formato del
  mapa de campo (sección 7).
- **Mapas offline generales → C, resuelto.** OSM + `offvmp` + soporte ya existente en Gadgetbridge.
- **A-GNSS → ninguna limpia a corto plazo.** La parte RTCM es C (datos públicos), pero falta `HW_PGNSS_*`
  (propietario) que el reloj exige. No se recomienda A (servidores/cuenta Huawei). Mitigación real: el fix GPS ya
  mejora mucho con el soporte actual de upstream (otros usuarios reportan 1–2 min frente a 10+).

## 7. Vía limpia para mapas (hallazgo) y su posible aplicación al golf

- Gadgetbridge sube mapas offline con `HuaweiP2PMapkitService`: ficheros `<mapId>.bin` / `<mapId>_contour.bin`,
  reconocidos por `HuaweiFwHelper.parseAsOfflineMap()`. Capacidades: offlineMap=201, contour=202.
  GT Runner 2: ambas SÍ. GT7: offlineMap SÍ, contour NO.
- `offvmp` (Me7c7, MIT) convierte extractos de OpenStreetMap a ese formato `.bin`. Cadena:
  `OSM .pbf → osmium → GeoJSON → build_country.py → encode.py → <id>.bin`.
- **Hipótesis a verificar (Fase 2):** si el mapa de golf vectorial del Runner 2 usa el mismo contenedor que los
  mapas offline, se podrían generar campos desde OSM (`golf=*`) sin Huawei. Requiere una muestra para confirmar.

## 8. Riesgos

- Bloqueo de cuenta de Huawei: no aplica mientras no se use su app/servidores (no se usan).
- Bucle de arranque del reloj por datos A-GNSS/golf incorrectos: riesgo real documentado. No enviar ficheros
  generados sin validación y sin asumir explícitamente el riesgo.
- Cifrado/firmas de Huawei (handshake de mapas, auth HMS del A-GNSS): no se intentan reproducir.

## 9. Estado del repositorio

- Fork público que compila; CI en verde; sonda de golf P2P y logs de depuración (debug) añadidos.
- Documentado: activación de golf, formato de mensajes de golf, etiqueta y formato de A-GNSS, vía de mapas offline.
