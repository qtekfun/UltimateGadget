# TODO: coherencia de las pantallas de Ajustes con la estética nueva

Las pantallas de ajustes de Gadgetbridge son **AndroidX Preference** (`PreferenceFragmentCompat` /
`PreferenceScreen`), no Compose, y son muchas. Reescribirlas en Compose está fuera de alcance.

## Estado
- La UI nueva (lista de dispositivos + detalle, `activities/ultimate/`) es Compose y usa su propio
  tema oscuro `UltimateTheme` con la paleta de marca (primary #8AB4FF, secondary menta, tertiary ámbar).
- Las activities de ajustes siguen usando el tema **global** de la app (claro/oscuro/dinámico según
  la preferencia del usuario) y los acentos de los *presets* existentes.

## Por qué no se cambió el tema global ahora
El acento (`colorPrimary`) se resuelve vía `?attr/tab_pill` y hay varios *presets* de color y temas
(`GadgetbridgeTheme`, `...Dark`, `...Black`, dinámicos, y presets con colores propios). Cambiar
`colorPrimary`/`colorSecondary` globalmente afectaría a TODA la app existente y a los presets del
usuario — alto riesgo visual para un cambio "barato". Se deja para una pasada dedicada.

## Plan propuesto (cuando se aborde)
1. Definir un tema dedicado `GadgetbridgeTheme.Ultimate(.NoActionBar)` que herede del dark de Material3
   con los acentos de marca (primary #8AB4FF, primaryContainer #1F4A8F, secondary #7DD3C0,
   background #0F1115, surface #14171D), sin tocar los temas existentes.
2. Lanzar las activities de ajustes **desde la UI nueva** forzando ese tema (p.ej.
   `ContextThemeWrapper`/`setTheme()` antes de `super.onCreate`, o un flag en el intent que la
   `AbstractSettingsActivityV2`/`DeviceSettingsActivity` lea para aplicar el tema Ultimate).
3. Unificar tipografía (Bricolage display / Figtree body) en las cabeceras de preferencia vía
   `app:layout` o un `PreferenceFragment` base con tipografías.
4. Opcional: `Preference` personalizadas para los switches con el track menta de la marca.

Mientras tanto, si el usuario tiene la app en modo oscuro, los ajustes ya se ven oscuros; solo el
tono del acento difiere del de la UI nueva.
