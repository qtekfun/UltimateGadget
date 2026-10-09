# Readiness (recuperación) — modelo

Puntuación local y transparente de 0 a 100 que estima cuán recuperado estás hoy. **No es una
métrica médica.** Las puntuaciones de los wearables (Oura, Garmin Body Battery, Whoop) son
propietarias; aquí usamos componentes públicos y documentados y los combinamos con pesos abiertos,
pensados para leerse y ajustarse.

## Factores (cada uno 0..100) y pesos

| Factor | Peso | Cómo |
|---|---|---|
| HRV vs línea base | 0.30 | ln(rMSSD) de anoche frente a la media de 7 días, escalado por su desviación. Más alto = más recuperado. |
| FC en reposo vs línea base | 0.20 | FC reposo de hoy frente a media/desv. de 7 días. Más baja = más recuperado. |
| Sueño | 0.30 | Horas de la última noche frente a un objetivo (8 h por defecto), con calidad opcional. |
| Carga de entreno (ACWR) | 0.20 | Agudo (7 días) / crónico (media semanal de ~28 días). Zona 0.8–1.3 = óptimo; picos > 1.5 penalizan. |

El total es la media ponderada de los factores **disponibles** (los pesos se renormalizan sobre los
presentes), así que degrada con elegancia. `confianza` refleja cuántos factores había.

Cuando falta el dato del reloj, se usa el **registro manual** (sueño en horas/calidad y entreno en
minutos + RPE 1–10), persistido en local por día.

## Fuentes (componentes, no una fórmula combinada validada)
- HRV: línea base móvil de 7 días y "smallest worthwhile change" (enfoque de Plews; ln rMSSD).
  Elite HRV: readiness a partir de cambios en ln(rMSSD); coeficiente de variación de 7 días.
- ACWR: Gabbett — ratio agudo:crónico; zona ~0.8–1.3, riesgo al alza por encima; versiones acopladas
  (1 sem / 4 sem) vs no acopladas (1 sem / 3 sem) casi idénticas (Gabbett et al., 2019).
- FC en reposo y duración de sueño como marcadores de recuperación de uso común.

Enlaces: Elite HRV (help.elitehrv.com), Plews/Altini (marcoaltini.substack.com),
Gabbett (bjsm.bmj.com/content/50/8/471). El modelo combinado y los pesos son propios y conviene
calibrarlos con tus propios datos.
