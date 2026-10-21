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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import java.io.File
import java.util.Locale

/** Milliseconds each picture stays before the cross-fade to the next one starts. */
private const val IMAGE_HOLD_MS = 2200L
private const val IMAGE_FADE_MS = 700

/**
 * Full-screen detail of an exercise: pictures (looping cross-fade for 2 or more, a still for one,
 * a muscle-group icon for none), description, muscles, equipment, the user's best set and the
 * attribution of the text and of every picture. [best] is the already formatted best set or null.
 */
@Composable
fun ExerciseDetail(e: Exercise, best: String?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    val palette = LocalUltimatePalette.current
    val locale = Locale.getDefault()
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = palette.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.displayName(locale), style = MaterialTheme.typography.titleLarge, color = palette.onSurface, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ult_train_close), tint = palette.onSurface)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ExerciseMedia(e)
                    Text(
                        best?.let { stringResource(R.string.ult_train_best, it) } ?: stringResource(R.string.ult_train_no_history_ex),
                        style = MaterialTheme.typography.titleMedium, color = palette.primary,
                    )
                    val summary = e.summary(locale)
                    val steps = e.steps(locale)
                    val tips = e.tips(locale)
                    if (summary.isNotBlank()) {
                        Text(summary, style = MaterialTheme.typography.bodyLarge, color = palette.onSurface, modifier = Modifier.testTag("ex_summary"))
                    }
                    if (steps.isNotEmpty()) {
                        Section(stringResource(R.string.ult_train_detail_steps)) { NumberedList(steps, "ex_step_") }
                    } else {
                        // Schema 1 packs and custom exercises: the plain description, with its wrapped lines joined.
                        val description = e.cleanDescription(locale)
                        if (description.isNotBlank()) {
                            Section(stringResource(R.string.ult_train_detail_description)) {
                                Text(description, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                            }
                        }
                    }
                    if (tips.isNotEmpty()) {
                        Section(stringResource(R.string.ult_train_detail_tips)) { BulletList(tips, "ex_tip_") }
                    }
                    Section(stringResource(R.string.ult_train_muscle)) {
                        Text(TrainingLabels.labels(context, e.muscle).ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                    }
                    if (e.musclesSecondary.isNotBlank()) {
                        Section(stringResource(R.string.ult_train_detail_muscles_secondary)) {
                            Text(TrainingLabels.labels(context, e.musclesSecondary), style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                        }
                    }
                    Section(stringResource(R.string.ult_train_equipment)) {
                        Text(TrainingLabels.labels(context, e.equipment).ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                    }
                    if (e.category.isNotBlank()) {
                        Section(stringResource(R.string.ult_train_detail_category)) {
                            Text(TrainingLabels.label(context, e.category), style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                        }
                    }
                    Section(stringResource(R.string.ult_train_detail_measure)) {
                        Text(measureLabel(e.measure), style = MaterialTheme.typography.bodyMedium, color = palette.onSurface)
                    }
                    e.pack?.let { Attribution(e, it, locale) }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.ult_train_delete)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val palette = LocalUltimatePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = palette.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Attribution(e: Exercise, info: PackInfo, locale: Locale) {
    val palette = LocalUltimatePalette.current
    val unknown = stringResource(R.string.ult_train_attr_unknown_author)
    Section(stringResource(R.string.ult_train_attribution)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val authors = info.authors.joinToString(", ").ifBlank { unknown }
            Text(
                stringResource(R.string.ult_train_attr_text, authors, info.license.ifBlank { "?" }),
                style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant,
            )
            if (locale.language in info.draftTranslations) {
                Text(stringResource(R.string.ult_train_attr_draft), style = MaterialTheme.typography.bodySmall, color = palette.error)
            }
            // Animations drawn for the app have no authors (CC0): one line for all of them. Photos keep their credits.
            info.media.filter { it.generated }.map { it.license.ifBlank { "CC0-1.0" } }.distinct().forEach { license ->
                Text(
                    stringResource(R.string.ult_train_attr_animation, license),
                    style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant,
                )
            }
            info.media.filter { !it.generated }.forEachIndexed { i, m ->
                val who = m.authors.joinToString(", ").ifBlank { unknown }
                Text(
                    stringResource(R.string.ult_train_attr_image, i + 1, who, m.license.ifBlank { "?" }),
                    style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant,
                )
                if (m.ai) Text(stringResource(R.string.ult_train_attr_ai), style = MaterialTheme.typography.bodySmall, color = palette.error)
            }
        }
    }
}

/** Decodes [file] downscaled so the longest side is about [maxSide] pixels; null when it is not an image. */
internal fun decodeScaled(file: File, maxSide: Int = 800): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

@Composable
private fun NumberedList(items: List<String>, tagPrefix: String) {
    val palette = LocalUltimatePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, text ->
            Row(Modifier.testTag(tagPrefix + i), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = palette.primary, modifier = Modifier.widthIn(min = 22.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BulletList(items: List<String>, tagPrefix: String) {
    val palette = LocalUltimatePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { i, text ->
            Row(Modifier.testTag(tagPrefix + i), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = palette.primary, modifier = Modifier.widthIn(min = 22.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The top of the detail: the looping animation when the pack has one (a still first frame before Android 9),
 * otherwise the photos, otherwise the muscle icon. With both, the animation comes first and a button swaps to the photos.
 */
@Composable
private fun ExerciseMedia(e: Exercise) {
    val context = LocalContext.current
    val decoder = LocalAnimationDecoder.current
    val info = e.pack
    val split = remember(e.id) { splitMedia(info?.media.orEmpty()) }
    val anim by produceState<AnimLoad>(if (split.animations.isEmpty()) AnimLoad.Failed else AnimLoad.Loading, e.id) {
        val file = info?.let { i -> split.animations.firstNotNullOfOrNull { TrainingPacks.mediaFile(context, i.packId, it.file) } }
        value = if (file == null) AnimLoad.Failed else withContext(Dispatchers.IO) {
            decoder.decode(file)?.let { AnimLoad.Ready(it) } ?: AnimLoad.Failed
        }
    }
    var showPhotos by rememberSaveable(e.id) { mutableStateOf(false) }
    val hasPhotos = split.photos.isNotEmpty()
    val a = anim
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            a is AnimLoad.Loading -> IconFrame()
            a is AnimLoad.Ready && !(showPhotos && hasPhotos) -> {
                val description = stringResource(R.string.ult_train_media_animation_cd)
                val palette = LocalUltimatePalette.current
                AnimatedDrawableView(
                    a.drawable,
                    Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(palette.surfaceContainer)
                        .semantics { contentDescription = description }.testTag("ex_anim"),
                )
            }
            else -> ExercisePhotos(e, split.photos)
        }
        if (a is AnimLoad.Ready && hasPhotos) {
            TextButton(onClick = { showPhotos = !showPhotos }, modifier = Modifier.testTag("ex_media_toggle")) {
                Text(stringResource(if (showPhotos) R.string.ult_train_media_show_animation else R.string.ult_train_media_show_photos))
            }
        }
    }
}

private val MEDIA_FRAME = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp))

@Composable
private fun IconFrame() {
    val palette = LocalUltimatePalette.current
    Box(MEDIA_FRAME.background(palette.primaryContainer).testTag("ex_icon"), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = palette.onPrimaryContainer, modifier = Modifier.size(72.dp))
    }
}

@Composable
private fun ExercisePhotos(e: Exercise, photos: List<PackMedia>) {
    val palette = LocalUltimatePalette.current
    val context = LocalContext.current
    val info = e.pack
    // Images are decoded lazily and only for the exercise on screen.
    val images by produceState<List<ImageBitmap>>(emptyList(), e.id) {
        value = if (info == null) emptyList() else withContext(Dispatchers.IO) {
            photos.mapNotNull { m -> TrainingPacks.mediaFile(context, info.packId, m.file)?.let { decodeScaled(it) } }
        }
    }
    if (images.isEmpty()) {
        IconFrame()
        return
    }
    var index by remember(images) { mutableIntStateOf(0) }
    if (images.size > 1) {
        LaunchedEffect(images) {
            while (true) {
                delay(IMAGE_HOLD_MS)
                index = (index + 1) % images.size
            }
        }
    }
    Crossfade(targetState = index, animationSpec = tween(IMAGE_FADE_MS), label = "exercise-images", modifier = MEDIA_FRAME.background(palette.surfaceContainer)) { i ->
        Image(images[i], contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().testTag("ex_image_$i"))
    }
}
