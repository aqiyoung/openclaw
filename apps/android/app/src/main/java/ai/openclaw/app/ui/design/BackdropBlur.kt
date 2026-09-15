package ai.openclaw.app.ui.design

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Handler
import android.graphics.PixelCopy
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Looper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.core.app.ComponentActivity
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real backdrop frosted-glass.
 *
 * Captures the host window region directly behind this composable with [PixelCopy], blurs it
 * through a [RenderEffect] [GraphicsLayer], and paints it behind the content — the genuine
 * "glass over the content behind it" look (Compose has no first-class backdropBlur API;
 * `Modifier.blur` only blurs a composable's own layer).
 *
 * The caller's [Surface] is expected to paint with a transparent color and let this modifier
 * draw BOTH the frosted backdrop and the fallback [fill]; the surface's border/shadow still
 * frame it. If capture is unavailable (no host Activity, transient PixelCopy failure, etc.)
 * the blur is simply skipped and [fill] alone shows, so the surface is never left blank.
 *
 * The composer is pinned while the chat scrolls behind it, so [onGloballyPositioned] does not
 * fire during scroll — a lightweight poll ([intervalMs]) re-captures the region so the frozen
 * backdrop tracks the content moving underneath.
 */
fun Modifier.frostedBackdrop(
  fill: Color,
  radius: Dp,
  intervalMs: Long = 90L,
): Modifier = this.then(backdropModifier(fill, radius, intervalMs))

private fun backdropModifier(
  fill: Color,
  radius: Dp,
  intervalMs: Long,
): Modifier = Modifier.composed {
  val view = LocalView.current
  val density = LocalDensity.current
  val window = remember(view) { view.context.findActivity()?.window }
  val radiusPx = with(density) { radius.toPx() }
  val rect = remember { mutableStateOf<Rect?>(null) }
  var bitmap by remember { mutableStateOf<Bitmap?>(null) }
  val graphicsLayer = remember { GraphicsLayer() }
  DisposableEffect(radiusPx) {
    graphicsLayer.renderEffect =
      RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
    onDispose { }
  }
  val scope = rememberCoroutineScope()
  DisposableEffect(window) {
    val job = scope.launch {
      while (isActive) {
        delay(intervalMs)
        val w = window ?: continue
        val r = rect.value ?: continue
        if (r.width <= 0f || r.height <= 0f) continue
        val bmp = captureWindowRegion(w, r) ?: continue
        bitmap?.recycle()
        bitmap = bmp
      }
    }
    onDispose {
      job.cancel()
      bitmap?.recycle()
      bitmap = null
    }
  }
  this
    .onGloballyPositioned { coordinates ->
      val pos = coordinates.localToWindow(Offset.Zero)
      rect.value =
        Rect(
          pos.x,
          pos.y,
          pos.x + coordinates.size.width,
          pos.y + coordinates.size.height,
        )
    }
    .drawBehind {
      // Fallback fill — always visible, so the surface reads as frosted even without blur.
      drawRect(color = fill, size = size)
      val bmp = bitmap
      if (bmp != null) {
        graphicsLayer.size = IntSize(size.width.roundToInt(), size.height.roundToInt())
        graphicsLayer.record { drawImage(bmp.asImageBitmap()) }
        drawContext.canvas.drawLayer(graphicsLayer)
      }
    }
}

private suspend fun captureWindowRegion(window: Window, rect: Rect): Bitmap? {
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

private tailrec fun Context.findActivity(): ComponentActivity? =
  when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
