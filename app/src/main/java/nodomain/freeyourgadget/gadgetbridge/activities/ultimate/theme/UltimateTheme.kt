/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * UltimateGadget visual language — a layered surface system with soft accents, available in
 * light and dark, plus an AMOLED (true-black) variant and optional Material You dynamic color.
 *
 * [UltimateTheme] reads the user's theme preferences itself, so individual Activities only need
 * to wrap their content in `UltimateTheme { ... }` (no arguments) and everything stays in sync.
 *
 * Preference keys (shared with the classic UI where possible):
 *  - `pref_key_theme` (String): "system" | "light" | "dark" | "dynamic". Chooses the light/dark
 *    mode. "system" and "dynamic" follow the OS. Unknown values are treated as "system".
 *  - `pref_key_theme_dynamic` (Boolean): enables Material You dynamic color on Android 12+.
 *    Dynamic color is also enabled when `pref_key_theme` is "dynamic", for compatibility with
 *    the classic selector.
 *  - `pref_key_theme_amoled_black` (Boolean): true-black surfaces; only applied in dark mode.
 */

/** Extra tokens beyond the Material color scheme, for code that draws with its own colours. */
data class UltimatePalette(
    val background: Color,
    val surface: Color,
    val surfaceLow: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val onSecondaryContainer: Color,
    val secondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** True when this palette is meant for a light background (drives which Material builder runs). */
    val isLight: Boolean,
) {
    companion object {
        val Dark = UltimatePalette(
            background = Color(0xFF0F1115),
            surface = Color(0xFF14171D),
            surfaceLow = Color(0xFF1A1D26),
            surfaceContainer = Color(0xFF1F232E),
            surfaceHigh = Color(0xFF272B38),
            surfaceHighest = Color(0xFF303544),
            onSurface = Color(0xFFE6E8EE),
            onSurfaceVariant = Color(0xFFAAB0C0),
            outline = Color(0xFF7C8396),
            outlineVariant = Color(0xFF3A4050),
            primary = Color(0xFF8AB4FF),
            onPrimary = Color(0xFF0A2A5E),
            primaryContainer = Color(0xFF1F4A8F),
            onPrimaryContainer = Color(0xFFD6E4FF),
            secondary = Color(0xFF7DD3C0),
            onSecondary = Color(0xFF00382F),
            secondaryContainer = Color(0xFF1B4D44),
            onSecondaryContainer = Color(0xFFC2F2E7),
            tertiary = Color(0xFFF5B971),
            onTertiary = Color(0xFF442A00),
            error = Color(0xFFFF8A80),
            onError = Color(0xFF5A0A05),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFFFDAD6),
            isLight = false,
        )

        val Amoled = Dark.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceLow = Color(0xFF0B0C10),
            surfaceContainer = Color(0xFF12141A),
            surfaceHigh = Color(0xFF1A1D25),
            surfaceHighest = Color(0xFF23262F),
        )

        /**
         * Light sibling of [Dark]: light surfaces with dark text and the same brand accents,
         * darkened for sufficient contrast on a light background (WCAG AA on text/onSurface).
         */
        val Light = UltimatePalette(
            background = Color(0xFFF6F8FC),
            surface = Color(0xFFFFFFFF),
            surfaceLow = Color(0xFFF0F3F9),
            surfaceContainer = Color(0xFFEAEEF6),
            surfaceHigh = Color(0xFFE3E8F1),
            surfaceHighest = Color(0xFFDBE1EC),
            onSurface = Color(0xFF191C22),
            onSurfaceVariant = Color(0xFF474E5C),
            outline = Color(0xFF737A8A),
            outlineVariant = Color(0xFFC6CCD8),
            primary = Color(0xFF2E5EAC),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD8E3FF),
            onPrimaryContainer = Color(0xFF001A41),
            secondary = Color(0xFF1C7667),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFBFF0E3),
            onSecondaryContainer = Color(0xFF00201A),
            tertiary = Color(0xFF8A5A14),
            onTertiary = Color(0xFFFFFFFF),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            isLight = true,
        )

        fun of(amoled: Boolean) = if (amoled) Amoled else Dark

        /** Apply true-black AMOLED surfaces on top of any dark palette (brand or dynamic). */
        fun UltimatePalette.amoledized(): UltimatePalette = copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceLow = Color(0xFF0B0C10),
            surfaceContainer = Color(0xFF12141A),
            surfaceHigh = Color(0xFF1A1D25),
            surfaceHighest = Color(0xFF23262F),
        )
    }
}

val LocalUltimatePalette = staticCompositionLocalOf { UltimatePalette.Dark }

/** Build a Material [ColorScheme] matching the given palette (light or dark builder). */
private fun schemeOf(p: UltimatePalette): ColorScheme {
    return if (p.isLight) {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
            secondary = p.secondary, onSecondary = p.onSecondary,
            secondaryContainer = p.secondaryContainer, onSecondaryContainer = p.onSecondaryContainer,
            tertiary = p.tertiary, onTertiary = p.onTertiary,
            error = p.error, onError = p.onError,
            errorContainer = p.errorContainer, onErrorContainer = p.onErrorContainer,
            background = p.background, onBackground = p.onSurface,
            surface = p.surface, onSurface = p.onSurface,
            surfaceVariant = p.surfaceHighest, onSurfaceVariant = p.onSurfaceVariant,
            surfaceTint = p.primary,
            outline = p.outline, outlineVariant = p.outlineVariant,
            scrim = Color.Black,
            inverseSurface = p.onSurface, inverseOnSurface = p.background, inversePrimary = p.primaryContainer,
            surfaceBright = p.surface, surfaceDim = p.surfaceHighest,
            surfaceContainerLowest = p.surface, surfaceContainerLow = p.surfaceLow,
            surfaceContainer = p.surfaceContainer, surfaceContainerHigh = p.surfaceHigh,
            surfaceContainerHighest = p.surfaceHighest,
        )
    } else {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary,
            primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
            secondary = p.secondary, onSecondary = p.onSecondary,
            secondaryContainer = p.secondaryContainer, onSecondaryContainer = p.onSecondaryContainer,
            tertiary = p.tertiary, onTertiary = p.onTertiary,
            error = p.error, onError = p.onError,
            errorContainer = p.errorContainer, onErrorContainer = p.onErrorContainer,
            background = p.background, onBackground = p.onSurface,
            surface = p.surface, onSurface = p.onSurface,
            surfaceVariant = p.surfaceHighest, onSurfaceVariant = p.onSurfaceVariant,
            surfaceTint = p.primary,
            outline = p.outline, outlineVariant = p.outlineVariant,
            scrim = Color.Black,
            inverseSurface = p.onSurface, inverseOnSurface = p.background, inversePrimary = p.primaryContainer,
            surfaceBright = p.surfaceHighest, surfaceDim = p.background,
            surfaceContainerLowest = p.background, surfaceContainerLow = p.surfaceLow,
            surfaceContainer = p.surfaceContainer, surfaceContainerHigh = p.surfaceHigh,
            surfaceContainerHighest = p.surfaceHighest,
        )
    }
}

/** Derive an [UltimatePalette] from a Material [ColorScheme] (used for dynamic color). */
private fun paletteFromScheme(s: ColorScheme, isLight: Boolean) = UltimatePalette(
    background = s.background,
    surface = s.surface,
    surfaceLow = s.surfaceContainerLow,
    surfaceContainer = s.surfaceContainer,
    surfaceHigh = s.surfaceContainerHigh,
    surfaceHighest = s.surfaceContainerHighest,
    onSurface = s.onSurface,
    onSurfaceVariant = s.onSurfaceVariant,
    outline = s.outline,
    outlineVariant = s.outlineVariant,
    primary = s.primary,
    onPrimary = s.onPrimary,
    primaryContainer = s.primaryContainer,
    onPrimaryContainer = s.onPrimaryContainer,
    secondary = s.secondary,
    onSecondary = s.onSecondary,
    secondaryContainer = s.secondaryContainer,
    onSecondaryContainer = s.onSecondaryContainer,
    tertiary = s.tertiary,
    onTertiary = s.onTertiary,
    error = s.error,
    onError = s.onError,
    errorContainer = s.errorContainer,
    onErrorContainer = s.onErrorContainer,
    isLight = isLight,
)

/** How the three Ultimate theme preferences resolve into an effective palette. */
private data class ThemeChoice(val dark: Boolean, val amoled: Boolean, val dynamic: Boolean)

@Composable
private fun resolveThemeChoice(): ThemeChoice {
    val context = LocalContext.current
    val prefs = GBApplication.getPrefs()

    val systemValue = context.getString(R.string.pref_theme_value_system)
    val lightValue = context.getString(R.string.pref_theme_value_light)
    val darkValue = context.getString(R.string.pref_theme_value_dark)
    val dynamicValue = context.getString(R.string.pref_theme_value_dynamic)

    val selected = prefs.getString("pref_key_theme", systemValue) ?: systemValue
    val followSystem = selected != lightValue && selected != darkValue
    val dark = when {
        !followSystem -> selected == darkValue
        else -> isSystemInDarkTheme()
    }

    val amoled = prefs.getBoolean("pref_key_theme_amoled_black", false)
    // Dynamic: explicit toggle, or the classic selector set to "dynamic".
    val dynamic = prefs.getBoolean("pref_key_theme_dynamic", false) || selected == dynamicValue

    return ThemeChoice(dark = dark, amoled = amoled, dynamic = dynamic)
}

@Composable
fun UltimateTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val choice = resolveThemeChoice()
    val canUseDynamic = choice.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var palette = when {
        canUseDynamic && choice.dark -> paletteFromScheme(dynamicDarkColorScheme(context), isLight = false)
        canUseDynamic && !choice.dark -> paletteFromScheme(dynamicLightColorScheme(context), isLight = true)
        choice.dark -> UltimatePalette.Dark
        else -> UltimatePalette.Light
    }
    // AMOLED only ever applies in dark mode.
    if (choice.dark && choice.amoled) {
        palette = with(UltimatePalette) { palette.amoledized() }
    }

    CompositionLocalProvider(LocalUltimatePalette provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette),
            typography = UltimateTypography,
            content = content,
        )
    }
}
