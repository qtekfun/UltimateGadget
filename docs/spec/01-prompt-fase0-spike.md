# Prompt Fase 0 — Spike de protocolo golf Huawei (UltimateGadget)

Pega este prompt en Claude Code, en tu equipo Fedora, con el móvil Huawei conectado por adb, Huawei Health instalada y el Watch GT Runner 2 emparejado con ella. Lee antes `00-spec.md`.

---

## Contexto

Estoy creando UltimateGadget, un fork de Gadgetbridge (AGPL-3.0). Quiero que el modo golf del Huawei Watch GT Runner 2 funcione sin Huawei Health: descargar mapas de campos, distancias GPS al green y sincronizar rondas. Observación clave: el modo golf aparece en el reloj solo mientras está conectado a la app original y desaparece al desconectarse. Hay que averiguar qué envía Huawei Health al reloj para activarlo y cómo se transfieren los mapas.

Todo se hace sobre mi propio reloj, mi propio móvil y una cuenta de Huawei secundaria, con fines de interoperabilidad. Nada de esto se publica con binarios, claves ni mapas de Huawei.

## Reglas de seguridad (obligatorias)

1. No hagas factory reset del reloj ni del móvil, ni desemparejes el reloj, sin preguntarme antes.
2. No me pidas ni escribas contraseñas, tokens o claves de la cuenta Huawei en ficheros del repositorio. Si aparecen en una captura, ofúscalos en los informes y añade las capturas a `.gitignore`.
3. Trabaja en un directorio nuevo y vacío para cada cosa descargada (APK, capturas, extracciones) y analízalo sin ejecutarlo.
4. No intentes saltarte autenticación, cifrado ni licencias de servidores de Huawei más allá de observar lo que hace mi propia app con mi propia cuenta. Si algo exige romper protecciones, para y cuéntamelo.
5. Antes de cualquier acción destructiva en el móvil o el reloj, confirma conmigo.

## Pasos

### 1. Entorno
- Comprueba `adb devices`, el modelo del móvil, la versión de Android y la versión de Huawei Health (`adb shell dumpsys package <paquete>`).
- Instala o verifica `jadx`, `apktool`, `tshark`/Wireshark y `python3`.
- Clona Gadgetbridge (upstream) en un directorio de trabajo y localiza el soporte Huawei (paquete `devices/huawei`), en particular: construcción de paquetes, servicios/ids, negociación de capacidades y soporte de GT Runner 2. Resume cómo está organizado antes de tocar nada.

### 2. Captura de tráfico Bluetooth
- Activa en el móvil el registro HCI de Bluetooth (opciones de desarrollador) y reinicia Bluetooth.
- Con Huawei Health conectada al reloj, ejecuta escenarios aislados, anotando la hora de cada uno:
  1. Conectar el reloj desde cero (reconexión completa).
  2. Abrir el modo golf en la app y comprobar que aparece en el reloj.
  3. Descargar un campo de golf desde la app.
  4. Desconectar el reloj y comprobar que el modo golf desaparece.
  5. Completar una ronda corta o simulada y sincronizarla.
- Extrae el log (`adb bugreport` o `adb pull` de `btsnoop_hci.log`) y analízalo con tshark. Separa los escenarios por marcas de tiempo.

### 3. Análisis de la activación del modo golf
- Compara la captura del escenario 1 con la del escenario 2/4 para aislar el mensaje o conjunto de capacidades que hace aparecer el golf.
- Contrasta con el soporte Huawei de Gadgetbridge: ¿qué servicio/comando corresponde? ¿falta en Gadgetbridge una capacidad, una instalación de app o una sincronización de estado?
- Entregable: mensaje(s) exactos (servicio, comando, campos) y qué se esperaba frente a qué hace Gadgetbridge hoy.

### 4. Análisis de la transferencia de mapas
- Con las capturas del escenario 3, identifica cómo viaja el mapa (canal, fragmentación, formato, cabeceras, checksums, cifrado).
- Con `jadx` sobre el APK de Huawei Health (extraído de mi propio móvil con `adb pull`), busca cadenas y clases relacionadas con golf, campos, mapas y transferencia de ficheros, y relaciónalas con lo observado en la captura.
- Averigua de dónde obtiene la app los datos del campo (endpoint, autenticación, formato) sin ejecutar peticiones fuera de lo que mi propia app ya hace.

### 4b. Actualizaciones de GPS (A-GNSS)
- Añade a las capturas un escenario extra: forzar o esperar la actualización de datos GPS/satélites desde Huawei Health (en sus ajustes del reloj, si existe esa opción) y anotar la hora.
- Identifica el mensaje de transferencia, el formato de los datos (efemérides/almanaque, constelaciones, cabeceras, validez/caducidad) y el origen: busca en el APK con `jadx` cadenas relacionadas con GPS, GNSS, efemérides, almanaque y asistencia, y relaciónalas con la captura.
- Averigua si el servidor es de Huawei o una fuente pública y si la descarga exige cuenta, sin hacer peticiones distintas de las que ya hace mi propia app.
- Comprueba cómo trata Gadgetbridge este caso para otras marcas (AGPS) para reutilizar su mecanismo de descarga y actualización programada.
- Entregable: origen, formato, caducidad y mensaje de transferencia, y si se puede reproducir desde Gadgetbridge.

### 5. Prueba de concepto
- En un fork de Gadgetbridge, implementa lo mínimo para enviar al reloj el mensaje de activación del golf y comprobar que el modo golf aparece sin Huawei Health conectada.
- Si es viable, intenta enviar un campo de prueba reutilizando un fichero capturado de mi propia descarga.
- Haz lo mismo con un paquete A-GNSS capturado de mi propia descarga y comprueba si el reloj lo acepta.

### 6. Informe final (`informe-fase0.md`)
Incluye: qué activa el golf, formato y transporte del mapa y de las actualizaciones GPS, de dónde salen los datos, qué requiere cuenta de Huawei, si la prueba de concepto funcionó, riesgos (bloqueo de cuenta, cifrado, firmas) y la **recomendación A/B/C** de la sección 3 de `00-spec.md` con la evidencia que la respalda. Sin credenciales ni claves.

## Criterio de parada
Si algún paso exige romper cifrado o autenticación de Huawei más allá de la observación de mi propia sesión, detente, resume qué has encontrado y pregúntame cómo seguir.
