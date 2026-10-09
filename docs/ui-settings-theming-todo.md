# Ajustes: coherencia estética — HECHO

Implementado: estilo `UltimateSettingsThemeDark` (+ `UltimateSettingsThemeDarkNoActionBar`) con los
acentos de marca, aplicado SOLO a las pantallas de ajustes (subclases de `AbstractSettingsActivityV2`)
en modo oscuro, mediante un special-case en `AbstractGBActivity.init(...)`. El resto de pantallas
conserva su tema. Pendiente opcional: variante AMOLED (ahora usan el fondo #0F1115) y modo claro.

---

# Ajustes: coherencia estética (TODO)

Estado: **pendiente, por riesgo**. Explicación y camino seguro para hacerlo bien.

## Por qué no se forzó ya
Las pantallas de preferencias (`SettingsActivity`, `DeviceSettingsActivity`) extienden
`AbstractSettingsActivityV2` → `AbstractGBActivity`, que **fija el tema por código** en
`setLanguage()` con `activity.setTheme(R.style.GadgetbridgeTheme...)` según la preferencia de
tema del usuario (claro/oscuro/dinámico/amoled). Por eso un `android:theme` en el manifest para
esas activities **se sobreescribe** y no tiene efecto. Cambiar el tema global
(`GadgetbridgeThemeDark`) sí afecta a TODAS las pantallas clásicas, no solo a los ajustes, así que
no cumple "solo esas pantallas sin romper el resto".

## Camino seguro propuesto (cambio pequeño y acotado)
1. Añadir en `res/values/styles.xml` un tema derivado del oscuro de Gadgetbridge con los acentos de
   marca, p.ej.:
   ```xml
   <style name="UltimateSettingsThemeDark" parent="GadgetbridgeThemeDark">
       <item name="colorPrimary">#8AB4FF</item>
       <item name="colorSecondary">#7DD3C0</item>
       <item name="android:colorBackground">#0F1115</item>
       <item name="colorSurface">#14171D</item>
       <!-- ...tokens de la paleta Ultimate... -->
   </style>
   ```
   (y una variante `*_NoActionBar` si hiciera falta).
2. En `AbstractGBActivity.setLanguage()`, **solo para esas dos activities**, aplicar el tema de
   marca en lugar del genérico. Ejemplo mínimo y localizado:
   ```java
   boolean brandSettings = (this instanceof SettingsActivity)
           || (this instanceof DeviceSettingsActivity);
   if (brandSettings && isDarkPreference) {
       activity.setTheme(R.style.UltimateSettingsThemeDark);
       return; // no caer en la cascada genérica
   }
   ```
   Así el resto de la app conserva su tema actual.

## Riesgo
`AbstractGBActivity` es núcleo compartido; el cambio es de 4-6 líneas pero toca el arranque de
todas las activities, por eso se deja documentado para hacerlo con una prueba en el dispositivo y
no a ciegas en un one-shot.
