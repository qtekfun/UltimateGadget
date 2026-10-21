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
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * Turns an animated WebP file into a [Drawable]. It is an interface so the screens can be tested without
 * the platform image decoder; [SystemAnimationDecoder] is the real one.
 */
internal fun interface AnimationDecoder {
    /** Blocking; call off the main thread. Null when the file cannot be decoded. */
    fun decode(file: File): Drawable?
}

/**
 * API 28+ decodes the file into an [AnimatedImageDrawable] that loops forever. On API 23-27 there is no
 * animated decoder, so the first frame (the start pose) is shown as a still, when the platform can read it.
 */
internal object SystemAnimationDecoder : AnimationDecoder {
    override fun decode(file: File): Drawable? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) decodeAnimated(file)
        else BitmapFactory.decodeFile(file.path)?.let { BitmapDrawable(android.content.res.Resources.getSystem(), it) }
    }.getOrNull()

    @RequiresApi(28)
    private fun decodeAnimated(file: File): Drawable {
        val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
        if (d is AnimatedImageDrawable) d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        return d
    }
}

internal val LocalAnimationDecoder = staticCompositionLocalOf<AnimationDecoder> { SystemAnimationDecoder }

/** Result of loading the animation of an exercise. */
internal sealed interface AnimLoad {
    object Loading : AnimLoad
    object Failed : AnimLoad
    class Ready(val drawable: Drawable) : AnimLoad
}

/** The pictures of an exercise split by kind: our looping animations and the (cross-fading) photos. */
internal class MediaSplit(val animations: List<PackMedia>, val photos: List<PackMedia>)

internal fun splitMedia(media: List<PackMedia>): MediaSplit {
    val (anims, photos) = media.partition { it.animated }
    return MediaSplit(anims, photos)
}

/**
 * Shows [drawable] and plays it while the screen is resumed. Playback stops when the lifecycle pauses, when
 * the composable leaves the composition and when the drawable changes, and the view lets go of the drawable
 * on release, so nothing keeps running or referenced after the screen is gone. Rotation recreates the
 * activity and so the view; the drawable is decoded again by the caller.
 */
@Composable
internal fun AnimatedDrawableView(drawable: Drawable, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { if (it.drawable !== drawable) it.setImageDrawable(drawable) },
        onRelease = { it.setImageDrawable(null) },
        modifier = modifier,
    )
    DisposableEffect(drawable, lifecycleOwner) {
        val animatable = drawable as? Animatable
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> animatable?.start()
                Lifecycle.Event.ON_PAUSE -> animatable?.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            animatable?.stop()
        }
    }
}
