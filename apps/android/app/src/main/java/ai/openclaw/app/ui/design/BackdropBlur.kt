package ai.openclaw.app.ui.design

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.ui.geometry.Rect
import androidx.core.app.ComponentActivity
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real backdrop frosted-glass helpers.
 *
 * [captureWindowRegion] snapshots the host window region directly behind a composable with
 * [PixelCopy]; the caller (e.g. [ClawGlassSurface]) drives a light poll loop and paints the
 * resulting bitmap through an `Image` + `graphicsLayer { renderEffect = ... }` to blur it.
 * Compose has no first-class backdropBlur, and `Modifier.blur` only blurs a composable's own
 * layer, so capturing the window behind it is the way to frost "the content behind it".
 *
 * If capture is unavailable (no host Activity, transient PixelCopy failure) the caller keeps
 * its translucent fill as a fallback, so the surface is never left blank.
 */

internal suspend fun captureWindowRegion(window: Window, rect: Rect): Bitmap? {
  val left = rect.left.toInt().coerceAtLeast(0)
  val top = rect.top.toInt().coerceAtLeast(0)
  val right = rect.right.toInt()
  val bottom = rect.bottom.toInt()
  val width = right - left
  val height = bottom - top
  if (width <= 0 || height <= 0) return null
  val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val source = android.graphics.Rect(left, top, right, bottom)
  val status = try {
    suspendCancellableCoroutine<Int> { continuation ->
      PixelCopy.request(
        window,
        source,
        bitmap,
        { result ->
          if (continuation.isActive) {
            continuation.resume(result) { bitmap.recycle() }
          } else {
            bitmap.recycle()
          }
        },
        Handler(Looper.getMainLooper()),
      )
    }
  } catch (_: IllegalArgumentException) {
    bitmap.recycle()
    return null
  }
  return if (status == PixelCopy.SUCCESS) bitmap else {
    bitmap.recycle()
    null
  }
}

internal tailrec fun Context.findActivity(): ComponentActivity? =
  when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
