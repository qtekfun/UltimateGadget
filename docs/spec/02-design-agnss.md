# Diseño A-GNSS (Huawei, sin Huawei Health)

Estado: diseño acordado 2026-10-10. Implementación por incrementos (ver final).
Recon y evidencia: `~/re/agnss/agnss-notes.md` (fuera del repo). Paquete real decodificado y
codec RTCM3 validado byte-perfecto contra el reloj (GT Runner 2).

## Qué sabemos (hechos del recon)
- **Lado reloj YA implementado en el núcleo** (upstream, autor Me7c7): service `0x1f`,
  `HuaweiEphemerisManager` + `SendEphemeris*` + `packets/Ephemeris.java`. El reloj INICIA; UG sirve
  un `ephemeris.zip` desde `getExternalFilesDir(null)` (troceado, CRC16, por tag/uuid/versión).
- **El GT Runner 2 solo pide `HW_AGNSS` (ver 3)** — nunca `HW_PGNSS`. Verificado con handshake pasivo
  (solo control, 0 efemérides subidas). → no hace falta el formato propietario PGNSS.
- **`HW_AGNSS` = RTCM 3.3 estándar**: frames 1019 (GPS) / 1020 (GLONASS) / 1042 (BeiDou) /
  1046 (Galileo). Sin cifrado ni .so: HH solo descomprime la respuesta del server. UG sirve el RTCM
  **descomprimido**. Codec validado (decode→encode idéntico en las 113 frames reales).

## Decisión de arquitectura: la descarga va en la companion (mapdownloader)
Motivo decisivo: **el núcleo de Gadgetbridge se quita `INTERNET` a propósito**
(`AndroidManifest.xml`: `tools:node="remove"` + Internet Helper). Dar internet a UG rompe ese pilar
y contradice la regla "modificar el núcleo lo mínimo". Por tanto:
- **mapdownloader** (ya tiene internet) = **productor A-GNSS**: descarga efemérides públicas y
  encodea el RTCM. El encoder es Kotlin puro (bit-packing + parseo de texto RINEX; sin deps nativas).
- **UltimateGadget** = **servidor**: ensambla el `ephemeris.zip` y lo sirve al reloj por el
  `HuaweiEphemerisManager` existente. No toca internet ni el núcleo.
- La spec ("descargadas y enviadas por UltimateGadget … descarga programada solo Wi-Fi, botón manual,
  indicador válido hasta") se cumple a nivel de **familia** UltimateGadget (companion + app).

## Entrega del zip: servicio bound (modelo "Internet Helper")
Android no deja que la companion escriba en `Android/data/com.qtekfun.ultimategadget/files/`
(scoped storage). Elegido: **UG se enlaza a un servicio de la companion** y recibe los bytes.

- La companion expone un `Service` (p. ej. `AgnssHelperService`) con interfaz **Messenger o AIDL**,
  protegido por **permiso custom signature** (`com.qtekfun.ultimategadget.permission.AGNSS`,
  `protectionLevel="signature"`). Ambas apps firman con el mismo `UG_KEYSTORE` (y el mismo debug key),
  así que la firma coincide. El servicio además verifica el paquete llamante.
- Contrato (async, no bloquear el binder — la companion puede tener que descargar):
  `requestAgnss(maxAgeSeconds, constellationsMask) -> { rtcmBytes, validUntilMillis,
  generatedAtMillis, satCountsByConstellation, resultCode }`.
  - Si la cache de la companion está fresca (≤ maxAge) devuelve al instante; si no, descarga RINEX
    (solo Wi-Fi salvo que el usuario fuerce) + encodea + cachea + devuelve.
- **UG ensambla el `ephemeris.zip`** con los `rtcmBytes`: `time`=ahora, `ephemeris_config.json`
  `{"higeo/v1/gnssinfo?type=0x0004/HW_AGNSS":{"ver":3,"uuid":"<uuid>","files":["HW_AGNSS_RTCM_33"]}}`,
  y `<uuid>/HW_AGNSS_RTCM_33` = `rtcmBytes`. Así UG controla la frescura del `time` y el formato del
  protocolo (su competencia). El nombre `HW_AGNSS_RTCM_33` reproduce el de HH (`hexdigest(MAC)+name`).

### Flujo sin bloquear
El reloj reintenta cada ~20 s mientras le falte A-GNSS (observado). Por eso UG no bloquea:
1. Reloj pide (operationInfo==1). Si UG tiene un zip fresco (toe dentro de validez) → sirve
   (re-sella `time`=ahora). El check de 30 min del manager es "descarga reciente", no la validez real
   de la efeméride (~2-4 h); por eso re-sellar es correcto.
2. Si no hay/está viejo → UG lanza `requestAgnss` **async** e ignora esta petición. El siguiente
   reintento del reloj (~20 s) ya encuentra el zip listo.
3. Companion no instalada / sin red → UG ignora (comportamiento actual) y la UI avisa. Sin crash.

### Prefetch y scheduler (spec: programada, Wi-Fi, configurable)
- La companion corre un job **WorkManager periódico** (solo Wi-Fi, intervalo configurable) que
  refresca su cache, para que el `requestAgnss` del momento de conexión sea instantáneo y resista
  estar sin red puntualmente. UG también puede disparar un prefetch al **conectar** el reloj.
- El schedule vive en la UI de la companion (tiene la red). UG muestra estado + "actualizar ahora"
  (= `requestAgnss(maxAge=0)`) + "válido hasta".

## Indicador "válido hasta"
La companion calcula `validUntilMillis` = max(toe) + intervalo de ajuste (~2-4 h) de las efemérides
incluidas, y lo devuelve. UG lo muestra en la pantalla A-GNSS.

## Encoder (companion) — RINEX nav → RTCM3
- Fuente: efemérides broadcast públicas multi-GNSS (RINEX 3 `*_MN.rnx`, IGS/BKG anónimo, ~1 MB).
  Confirmar endpoint abierto sin login (BKG). Elegir la última efeméride por satélite (toe ≤ ahora).
- Mensajes: 1019 (GPS), 1020 (GLONASS, **sign-magnitude** en varios campos), 1042 (BeiDou),
  1046 (Galileo I/NAV). Tablas de campos y escalas validadas en `~/re/scripts/rtcm_codec2.py`.
- Frame: `0xD3` + 6 bits reservados + 10 bits longitud + payload + **CRC24Q** (poly 0x1864CFB).
- Salida: RTCM crudo (sin gzip; UG sirve el descomprimido). Validar contra el paquete real de HH.

## UG — cambios (pequeños, aislados, fuera del núcleo salvo re-sellado)
- Habilitar opción A-GNSS para Huawei (hoy `supportsAgps` excluye HuaweiCoordinator): pantalla
  Ultimate con estado / "válido hasta" / "actualizar ahora" / aviso si falta la companion.
- Cliente del `AgnssHelperService` (bind) + ensamblado del `ephemeris.zip` + re-sellado de `time`.
- En `HuaweiEphemerisManager`: servir el zip (ya existe) y exponer "válido hasta". Quitar/gate el
  logging diagnóstico `UG-AGNSS` de `packets/Ephemeris.java` antes de mergear.

## Seguridad / privacidad
- Permiso signature para el servicio; verificación del paquete llamante.
- Datos 100% públicos (IGS/BKG), cero Huawei/HMS, sin binarios ni claves de Huawei.
- No se redistribuye nada de Huawei. El RTCM se genera a partir de datos abiertos.

## Riesgo de brick (envío al reloj)
Bajo: es RTCM 3.3 estándar, idéntico en formato a lo que manda HH, y el reloj lo está pidiendo.
Aun así, el **primer envío real al reloj es gated**: se hace con el usuario delante (Inc 2).

## Incrementos
- **Inc 1 (offline, seguro):** encoder RINEX→RTCM3 + ensamblado `ephemeris.zip` en la companion.
  Validación offline: decodificar nuestra salida y comparar con el paquete real de HH (tipos/CRC/
  rangos sqrtA). Sin tocar el reloj.
- **Inc 2 (gated, usuario delante):** primer envío real al reloj. Para de-riesgar, primero empujar un
  zip generado por la companion vía adb (dev) y confirmar que el reloj lo acepta; luego cablear el
  bind UG↔companion y repetir por la vía de producción.
- **Inc 3:** scheduler (Wi-Fi, configurable) + prefetch al conectar + indicador "válido hasta" + UI.
