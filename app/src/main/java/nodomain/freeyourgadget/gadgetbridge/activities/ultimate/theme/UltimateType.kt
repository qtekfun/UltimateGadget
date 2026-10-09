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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Centralised type: display = Bricolage Grotesque, body = Figtree (bundled OFL variable fonts in
 * res/font). The weight variation axis is applied per face so the variable fonts render at the
 * intended weights (API 26+; below that the default instance is used).
 */
private fun wght(w: Int) = FontVariation.Settings(FontVariation.weight(w))

val DisplayFamily: FontFamily = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold, variationSettings = wght(600)),
    Font(R.font.bricolage_grotesque, FontWeight.Bold, variationSettings = wght(700)),
)
val BodyFamily: FontFamily = FontFamily(
    Font(R.font.figtree, FontWeight.Normal, variationSettings = wght(400)),
    Font(R.font.figtree, FontWeight.Medium, variationSettings = wght(500)),
    Font(R.font.figtree, FontWeight.SemiBold, variationSettings = wght(600)),
    Font(R.font.figtree, FontWeight.Bold, variationSettings = wght(700)),
)

val UltimateTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFAAB0C0)),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp),
)

/** Big tabular number used for card hero values. */
val MetricValueStyle = TextStyle(
    fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
    fontSize = 34.sp, letterSpacing = (-0.5).sp,
)

/** Small uppercase section / card header. */
val SectionLabelStyle = TextStyle(
    fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp, letterSpacing = 1.2.sp,
)
