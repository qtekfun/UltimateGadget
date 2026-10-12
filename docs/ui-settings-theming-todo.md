# Settings: visual consistency — DONE

Implemented: the `UltimateSettingsThemeDark` style (+ `UltimateSettingsThemeDarkNoActionBar`) with the
brand accents, applied ONLY to the settings screens (subclasses of `AbstractSettingsActivityV2`)
in dark mode, via a special case in `AbstractGBActivity.init(...)`. The rest of the screens
keep their theme. Optional pending: an AMOLED variant (they currently use the #0F1115 background) and light mode.

---

# Settings: visual consistency (TODO)

Status: **pending, because of risk.** Explanation and a safe path to do it right.

## Why it was not forced already
The preference screens (`SettingsActivity`, `DeviceSettingsActivity`) extend
`AbstractSettingsActivityV2` → `AbstractGBActivity`, which **sets the theme in code** in
`setLanguage()` with `activity.setTheme(R.style.GadgetbridgeTheme...)` according to the user's theme
preference (light/dark/dynamic/amoled). That is why an `android:theme` in the manifest for
those activities is **overridden** and has no effect. Changing the global theme
(`GadgetbridgeThemeDark`) does affect ALL the classic screens, not just settings, so it
does not meet "only those screens without breaking the rest".

## Proposed safe path (small, scoped change)
1. Add to `res/values/styles.xml` a theme derived from Gadgetbridge's dark one with the brand
   accents, e.g.:
   ```xml
   <style name="UltimateSettingsThemeDark" parent="GadgetbridgeThemeDark">
       <item name="colorPrimary">#8AB4FF</item>
       <item name="colorSecondary">#7DD3C0</item>
       <item name="android:colorBackground">#0F1115</item>
       <item name="colorSurface">#14171D</item>
       <!-- ...Ultimate palette tokens... -->
   </style>
   ```
   (and a `*_NoActionBar` variant if needed).
2. In `AbstractGBActivity.setLanguage()`, **only for those two activities**, apply the brand
   theme instead of the generic one. Minimal, localized example:
   ```java
   boolean brandSettings = (this instanceof SettingsActivity)
           || (this instanceof DeviceSettingsActivity);
   if (brandSettings && isDarkPreference) {
       activity.setTheme(R.style.UltimateSettingsThemeDark);
       return; // do not fall into the generic cascade
   }
   ```
   This way the rest of the app keeps its current theme.

## Risk
`AbstractGBActivity` is shared core; the change is 4-6 lines but touches the startup of
every activity, so it is left documented to be done with an on-device test and
not blindly in a one-shot.
