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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * UltimateGadget visual language — dark only, layered dark surfaces with soft pastel accents.
 * Taken from the UltimateVideo concept (see memory reference-ui-aesthetic). No dynamic color.
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
    val onSecondaryContainer: Color,
    val secondaryContainer: Color,
    val tertiary: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
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
            secondaryContainer = Color(0xFF1B4D44),
            onSecondaryContainer = Color(0xFFC2F2E7),
            tertiary = Color(0xFFF5B971),
            error = Color(0xFFFF8A80),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFFFDAD6),
        )

        val Amoled = Dark.copy(
            background = Color(0xFF000000),
            surface = Color(0xFF000000),
            surfaceLow = Color(0xFF0B0C10),
            surfaceContainer = Color(0xFF12141A),
            surfaceHigh = Color(0xFF1A1D25),
            surfaceHighest = Color(0xFF23262F),
        )

        fun of(amoled: Boolean) = if (amoled) Amoled else Dark
    }
}

val LocalUltimatePalette = staticCompositionLocalOf { UltimatePalette.Dark }

private fun schemeOf(p: UltimatePalette) = darkColorScheme(
    primary = p.primary, onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer, onPrimaryContainer = p.onPrimaryContainer,
    secondary = p.secondary, onSecondary = Color(0xFF00382F),
    secondaryContainer = p.secondaryContainer, onSecondaryContainer = p.onSecondaryContainer,
    tertiary = p.tertiary, onTertiary = Color(0xFF442A00),
    error = p.error, onError = Color(0xFF5A0A05),
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

@Composable
fun UltimateTheme(amoled: Boolean = false, content: @Composable () -> Unit) {
    val palette = UltimatePalette.of(amoled)
    CompositionLocalProvider(LocalUltimatePalette provides palette) {
        MaterialTheme(
            colorScheme = schemeOf(palette),
            typography = UltimateTypography,
            content = content,
        )
    }
}
