package ai.openclaw.app.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class ClawStatus {
  Neutral,
  Success,
  Warning,
  Danger,
}

/** Full-screen mobile scaffold that applies OpenClaw safe-area and canvas tokens. */
@Composable
internal fun ClawScaffold(
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = ClawTheme.spacing.sm, vertical = ClawTheme.spacing.xxs),
  contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    background: Brush? = null,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .let { m -> if (background != null) m.background(brush = background) else m.background(color = ClawTheme.colors.canvas) }
        .windowInsetsPadding(contentWindowInsets)
        .padding(contentPadding),
  ) {
    content()
  }
}

/** Section title row with an optional trailing action slot. */
@Composable
internal fun ClawSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = title,
      style = ClawTheme.type.section,
      color = ClawTheme.colors.text,
    )
    action?.invoke()
  }
}

/** Primary call-to-action button styled after the iOS pill with a tinted gradient. */
@Composable
internal fun ClawPrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  val colors = ClawTheme.colors
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(if (enabled && pressed) 0.98f else 1f, label = "primaryPressScale")
  val contentColor = if (enabled) colors.primaryText else colors.textSubtle
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = ClawTheme.spacing.touchTarget).scale(scale),
    shape = RoundedCornerShape(50),
    color = Color.Transparent,
    contentColor = contentColor,
    border = if (enabled) BorderStroke(0.75.dp, colors.primary.copy(alpha = 0.85f)) else null,
    interactionSource = interactionSource,
  ) {
    Box(
      modifier =
        Modifier
          .background(
            if (enabled) {
              Brush.verticalGradient(0f to colors.primary, 1f to colors.primary.copy(alpha = 0.9f))
            } else {
              Brush.verticalGradient(0f to colors.surfacePressed, 1f to colors.surfacePressed)
            },
          )
          .background(
            if (enabled) {
              Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.14f),
                0.5f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.08f),
              )
            } else {
              Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Transparent)
            },
          )
          .padding(horizontal = 18.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        if (icon != null) {
          Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, style = ClawTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** Secondary action button for non-default commands. */
@Composable
internal fun ClawSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  // Dark themes take a much quieter sheen: a 42% white wash reads as a bright
  // smudge on near-black surfaces (mirrors the 12% sheen in ClawGlassSurface).
  val isDark = ClawTheme.colors.canvas.luminance() < 0.5f
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.heightIn(min = ClawTheme.spacing.touchTarget),
    shape = RoundedCornerShape(50),
    color = Color.Transparent,
    contentColor = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
    border = BorderStroke(0.75.dp, if (enabled) ClawTheme.colors.borderStrong else ClawTheme.colors.border),
  ) {
    Box(
      modifier =
        Modifier
          .background(
            if (enabled) {
              Brush.verticalGradient(0f to ClawTheme.colors.surfaceRaised, 1f to ClawTheme.colors.surface)
            } else {
              Brush.verticalGradient(0f to ClawTheme.colors.surface, 1f to ClawTheme.colors.surface)
            },
          )
          .background(
            if (enabled) {
              Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (isDark) 0.12f else 0.42f),
                0.6f to Color.Transparent,
              )
            } else {
              Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Transparent)
            },
          )
          .padding(horizontal = 12.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        if (icon != null) {
          Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, style = ClawTheme.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** Fixed-size circular icon button for toolbar actions. */
@Composable
internal fun ClawIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  ClawIconTouchTarget(onClick = onClick, enabled = enabled, modifier = modifier) {
    Box(
      modifier =
        Modifier
          .size(ClawTheme.spacing.iconSlot)
          .background(
            color = if (enabled) ClawTheme.colors.surfaceRaised else ClawTheme.colors.surface,
            shape = CircleShape,
          ).border(width = 1.dp, color = ClawTheme.colors.border, shape = CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = Modifier.size(ClawTheme.spacing.icon),
        tint = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
      )
    }
  }
}

/** Transparent circular icon button for low-emphasis toolbar actions. */
@Composable
internal fun ClawPlainIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  ClawIconTouchTarget(onClick = onClick, enabled = enabled, modifier = modifier) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(ClawTheme.spacing.icon),
      tint = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
    )
  }
}

/**
 * Keeps the full touch target while the painted shape stays small: the hit area
 * and ripple fill [ClawSpacing.touchTarget], the content draws at icon scale.
 */
@Composable
private fun ClawIconTouchTarget(
  onClick: () -> Unit,
  enabled: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      modifier
        .size(ClawTheme.spacing.touchTarget)
        .clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
    content = { content() },
  )
}

/** Compact label/value row for health and readiness summaries. */
@Composable
internal fun ClawStatusRow(
  title: String,
  value: String,
  healthy: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().heightIn(min = ClawTheme.spacing.touchTarget).padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
  ) {
    Text(
      text = title,
      style = ClawTheme.type.body,
      color = ClawTheme.colors.text,
      modifier = Modifier.weight(1f),
      maxLines = 1,
    )
    ClawStatusPill(
      text = value,
      status = if (healthy) ClawStatus.Success else ClawStatus.Warning,
    )
  }
}

/** Compact status chip with a semantic color dot. */
@Composable
internal fun ClawStatusPill(
  text: String,
  status: ClawStatus,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  val (accentColor, backgroundColor) =
    when (status) {
      ClawStatus.Neutral -> colors.textMuted to colors.surfaceRaised
      ClawStatus.Success -> colors.success to colors.successSoft
      ClawStatus.Warning -> colors.warning to colors.warningSoft
      ClawStatus.Danger -> colors.danger to colors.dangerSoft
    }

  Surface(
    modifier = modifier,
    shape = ClawTheme.shapes.row,
    color = backgroundColor,
    border = BorderStroke(1.dp, if (status == ClawStatus.Neutral) colors.border else accentColor.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(accentColor),
      )
      Text(text = text, style = ClawTheme.type.caption, color = colors.text, maxLines = 1)
    }
  }
}

/** Small optional-selectable pill used for filters and metadata chips. */
@Composable
internal fun ClawPill(
  text: String,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val surfaceModifier =
    if (onClick == null) {
      modifier
    } else {
      modifier.selectable(selected = selected, role = Role.Button, onClick = onClick)
    }

  Surface(
    modifier = surfaceModifier,
    shape = ClawTheme.shapes.pill,
    color = if (selected) ClawTheme.colors.accentSoft else ClawTheme.colors.surfaceRaised,
    contentColor = if (selected) ClawTheme.colors.text else ClawTheme.colors.textMuted,
    border = BorderStroke(1.dp, if (selected) ClawTheme.colors.accent.copy(alpha = 0.45f) else ClawTheme.colors.border),
  ) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      style = ClawTheme.type.caption,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** Panel wrapper for homogeneous lists with standard row separators. */
@Composable
internal fun <T> ClawListPanel(
  items: List<T>,
  modifier: Modifier = Modifier,
  row: @Composable (T) -> Unit,
) {
  ClawPanel(modifier = modifier, contentPadding = PaddingValues(horizontal = ClawTheme.spacing.xs, vertical = 4.dp)) {
    ClawSeparatedColumn(items = items, row = row)
  }
}

/** Column helper that inserts standard dividers between rendered rows. */
@Composable
internal fun <T> ClawSeparatedColumn(
  items: List<T>,
  modifier: Modifier = Modifier,
  row: @Composable (T) -> Unit,
) {
  Column(modifier = modifier) {
    items.forEachIndexed { index, item ->
      row(item)
      if (index != items.lastIndex) {
        HorizontalDivider(color = ClawTheme.colors.border.copy(alpha = 0.82f), thickness = 1.dp)
      }
    }
  }
}

/** Two-line settings/detail row with caller-provided leading and trailing slots. */
@Composable
internal fun ClawDetailRow(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  leading: @Composable () -> Unit,
  trailing: @Composable () -> Unit,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = ClawTheme.spacing.row)
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
  ) {
    leading()
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = title, style = ClawTheme.type.body, color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(text = subtitle, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    trailing()
  }
}

/** Circular text badge used for compact numeric or initials-style row marks. */
@Composable
internal fun ClawTextBadge(
  text: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.size(28.dp),
    shape = CircleShape,
    color = ClawTheme.colors.surfacePressed,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
    contentColor = ClawTheme.colors.text,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(text = text, style = ClawTheme.type.label, color = ClawTheme.colors.text, maxLines = 1)
    }
  }
}

/** Circular icon badge used as a neutral leading marker in list rows. */
@Composable
internal fun ClawIconBadge(
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.size(28.dp),
    shape = CircleShape,
    color = ClawTheme.colors.surfacePressed,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
    contentColor = ClawTheme.colors.text,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = ClawTheme.colors.text)
    }
  }
}

/**
 * iOS-style toggle: a 52x32 capsule track with a 28dp thumb, fully tappable as a row.
 */
@Composable
internal fun ClawToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val colors = ClawTheme.colors
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val trackColor = if (!enabled) colors.surfacePressed else if (checked) colors.primary else colors.borderStrong
  val thumbColor = if (enabled) Color.White else colors.textSubtle
  Box(
    modifier =
      modifier
        .size(width = 52.dp, height = 32.dp)
        .clip(RoundedCornerShape(50))
        .background(trackColor)
        .clickable(
          enabled = enabled,
          role = Role.Switch,
          interactionSource = interactionSource,
          indication = null,
          onClick = { onCheckedChange(!checked) },
        ).padding(2.dp),
    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
  ) {
    Box(
      modifier =
        Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(thumbColor)
          .scale(if (pressed) 0.92f else 1f),
    )
  }
}

/**
 * Frosted "Liquid Glass" surface approximation for interactive chrome (top bars,
 * FABs, sheets). Android has no true backdrop blur, so this layers a translucent
 * surface tint with a hairline border and soft shadow over the content behind it.
 */
@Composable
internal fun ClawGlassSurface(
  modifier: Modifier = Modifier,
  shape: Shape = ClawTheme.shapes.panel,
  contentColor: Color = ClawTheme.colors.text,
  blurBehind: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colors = ClawTheme.colors
  val isDark = colors.canvas.luminance() < 0.5f
  val fill = (if (isDark) colors.surface else colors.surfacePressed).copy(alpha = if (isDark) 0.5f else 0.9f)
  val border = BorderStroke(0.5.dp, if (isDark) Color.White.copy(alpha = 0.30f) else colors.border.copy(alpha = 0.55f))
  if (blurBehind) {
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = with(density) { 16.dp.toPx() }
    val blurEffect =
      remember(radiusPx) {
        RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
      }
    val rect = remember { mutableStateOf<Rect?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    // Translucent frosted base — keeps the surface readable if backdrop capture is
    // unavailable (e.g. no host Activity). NOT opaque, so it reads as glass, not a plate.
    val baseFill = (if (isDark) colors.surface else colors.surfacePressed).copy(alpha = if (isDark) 0.5f else 0.55f)
    DisposableEffect(Unit) {
      val job =
        scope.launch {
          while (isActive) {
            delay(90)
            // Resolve the host window on EVERY poll: a `remember(view)`-cached window can
            // stick at null (Activity not attached yet on first composition) and silently
            // disable the whole backdrop blur forever.
            val w = view.context.findActivity()?.window ?: continue
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
    Box(
      modifier =
        Modifier.onGloballyPositioned { coordinates ->
          val pos = coordinates.localToWindow(Offset.Zero)
          rect.value =
            Rect(pos.x, pos.y, pos.x + coordinates.size.width, pos.y + coordinates.size.height)
        },
    ) {
      // 1) Frosted base (fallback / backing).
      Box(Modifier.matchParentSize().clip(shape).background(baseFill))
      // 2) Real backdrop blur: the window region behind the composer, frosted.
      bitmap?.let { captured ->
        Image(
          bitmap = captured.asImageBitmap(),
          contentDescription = null,
          modifier =
            Modifier.matchParentSize().clip(shape).graphicsLayer { renderEffect = blurEffect },
        )
      }
      // 3) Glassy brighten tint over the blur — reads as glass, not a raw screenshot.
      Box(
        Modifier
          .matchParentSize()
          .clip(shape)
          .background(if (isDark) Color.Black.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.16f)),
      )
      // 4) Specular sheen along the top edge — the "liquid glass" highlight.
      Box(
        Modifier
          .matchParentSize()
          .clip(shape)
          .background(
            Brush.verticalGradient(
              colors = listOf(Color.White.copy(alpha = if (isDark) 0.12f else 0.30f), Color.Transparent),
            ),
          ),
      )
      // 5) Content on top, transparent so the glass shows through.
      Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        border = border,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
      ) {
        content()
      }
    }
  } else {
    Surface(
      modifier = modifier,
      shape = shape,
      color = fill,
      contentColor = contentColor,
      border = border,
      shadowElevation = 2.dp,
      tonalElevation = 0.dp,
    ) {
      content()
    }
  }
}

/**
 * Quiet, opaque writing surface for content panels such as the chat composer.
 *
 * Mirrors the web composer contract (`ui/src/styles/chat/composer.css`): ONE surface
 * whose separation comes from luminance plus a hairline - never a stack of backdrop
 * blur, sheen, strong border and shadow, which reads as a dark frame in light themes.
 * Light themes drop the painted edge for a 6% black ring ("Light surfaces get depth
 * from translucent layers instead of a painted edge"); dark themes use a 9% hairline.
 */
@Composable
internal fun ClawComposerSurface(
  modifier: Modifier = Modifier,
  shape: Shape = ClawTheme.shapes.panel,
  contentColor: Color = ClawTheme.colors.text,
  content: @Composable () -> Unit,
) {
  val colors = ClawTheme.colors
  val isDark = colors.canvas.luminance() < 0.5f
  val border =
    if (isDark) {
      BorderStroke(1.dp, colors.text.copy(alpha = 0.09f))
    } else {
      BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f))
    }
  Surface(
    modifier = modifier,
    shape = shape,
    color = colors.surface,
    contentColor = contentColor,
    border = border,
    shadowElevation = 2.dp,
    tonalElevation = 0.dp,
  ) {
    content()
  }
}

/** Reusable one-line list row with optional subtitle, metadata, slots, and click handling. */
@Composable
internal fun ClawListItem(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  metadata: String? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
) {
  val rowModifier =
    if (onClick == null) {
      modifier
    } else {
      modifier.clickable(onClick = onClick)
    }

  Row(
    modifier =
      rowModifier
        .fillMaxWidth()
        .heightIn(min = ClawTheme.spacing.touchTarget)
        .clip(ClawTheme.shapes.row)
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
  ) {
    leading?.invoke()
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = ClawTheme.type.body,
        color = ClawTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textSubtle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (metadata != null) {
      Text(text = metadata, style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle, maxLines = 1)
    }
    trailing?.invoke()
  }
}

/** Keeps segmented options on one row unless a caller explicitly opts into wrapping. */
internal fun segmentedControlRows(
  options: List<String>,
  maxOptionsPerRow: Int? = null,
): List<List<String>> {
  if (options.isEmpty()) return emptyList()
  if (maxOptionsPerRow == null || options.size <= maxOptionsPerRow) return listOf(options)
  require(maxOptionsPerRow > 0) { "maxOptionsPerRow must be positive" }

  val rowCount = (options.size + maxOptionsPerRow - 1) / maxOptionsPerRow
  val minimumRowSize = options.size / rowCount
  val largerRowCount = options.size % rowCount
  var startIndex = 0
  return List(rowCount) { rowIndex ->
    val rowSize = minimumRowSize + if (rowIndex < largerRowCount) 1 else 0
    options.subList(startIndex, startIndex + rowSize).toList().also {
      startIndex += rowSize
    }
  }
}

/** Equal-width segmented control with caller-controlled wrapping. */
@Composable
internal fun ClawSegmentedControl(
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
  enabledOptions: Set<String> = options.toSet(),
  maxOptionsPerRow: Int? = null,
) {
  Column(
    modifier =
      modifier
        .selectableGroup()
        .clip(ClawTheme.shapes.control)
        .background(ClawTheme.colors.surface)
        .border(1.dp, ClawTheme.colors.border, ClawTheme.shapes.control)
        .padding(2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    segmentedControlRows(options, maxOptionsPerRow).forEach { rowOptions ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        rowOptions.forEach { option ->
          val active = option == selected
          val enabled = option in enabledOptions
          Box(
            modifier =
              Modifier
                .weight(1f)
                .heightIn(min = ClawTheme.spacing.control)
                .clip(ClawTheme.shapes.row)
                .background(if (active) ClawTheme.colors.surfacePressed else Color.Transparent)
                .selectable(selected = active, enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = option,
              style = ClawTheme.type.caption,
              color =
                when {
                  active -> ClawTheme.colors.text
                  enabled -> ClawTheme.colors.textMuted
                  else -> ClawTheme.colors.textSubtle
                },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun ClawTextField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  minLines: Int = 1,
  label: String? = null,
  enabled: Boolean = true,
  secret: Boolean = false,
  maxLines: Int = Int.MAX_VALUE,
) {
  // Compose 1.12's String editor retains its initial password semantics.
  // Recreate it when sensitivity changes; the caller still owns the text.
  key(secret) {
    val fieldModifier =
      if (label == null) modifier else modifier.semantics { contentDescription = label }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      enabled = enabled,
      interactionSource = interactionSource,
      modifier =
        fieldModifier
          .fillMaxWidth()
          .heightIn(min = ClawTheme.spacing.touchTarget)
          .clip(ClawTheme.shapes.control)
          .background(ClawTheme.colors.surface)
          .border(
            1.dp,
            if (focused) ClawTheme.colors.accent else ClawTheme.colors.border,
            ClawTheme.shapes.control,
          ).padding(horizontal = ClawTheme.spacing.xs, vertical = ClawTheme.spacing.xxs),
      textStyle =
        ClawTheme.type.body.copy(
          color = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
        ),
      cursorBrush = SolidColor(ClawTheme.colors.text),
      keyboardOptions =
        if (secret) KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false) else KeyboardOptions.Default,
      visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
      minLines = minLines,
      maxLines = maxLines,
      decorationBox = { innerTextField ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          label?.let {
            Text(text = it, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
          Box(modifier = Modifier.fillMaxWidth()) {
            if (value.isEmpty()) {
              Text(text = placeholder, style = ClawTheme.type.body, color = ClawTheme.colors.textSubtle)
            }
            innerTextField()
          }
        }
      },
    )
  }
}

/** Local design-system preview surface for visual smoke checks. */
@Composable
internal fun ClawComponentShowcase(modifier: Modifier = Modifier) {
  var selected by rememberSaveable { mutableStateOf("Chat") }
  var prompt by rememberSaveable { mutableStateOf("") }

  ClawScaffold(modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.sm)) {
      ClawTopBar(
        title = "OpenClaw",
        subtitle = "Local command center",
        navigation = { ClawAvatarMark(text = "OC") },
        actions = {
          ClawIconButton(icon = Icons.Default.Search, contentDescription = "Search", onClick = {})
        },
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(text = "OpenClaw", style = ClawTheme.type.display, color = ClawTheme.colors.text)
          Text(text = "Design system prototype", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
        ClawStatusPill(text = "Connected", status = ClawStatus.Success)
      }

      ClawSegmentedControl(
        options = listOf("Chat", "Voice", "Threads"),
        selected = selected,
        onSelect = { selected = it },
        modifier = Modifier.fillMaxWidth(),
      )

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ClawSectionHeader(title = "Threads")
        ClawListItem(
          title = "Testing testing 1 2 3",
          subtitle = "14 messages · Android",
          metadata = "now",
        )
        ClawListItem(
          title = "Provider setup",
          subtitle = "OpenClaw gateway",
          metadata = "8m",
        )
      }

      ClawTextField(value = prompt, onValueChange = { prompt = it }, placeholder = "Ask OpenClaw anything", minLines = 3)

      Row(horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
        ClawPrimaryButton(text = "Start Chat", onClick = {}, modifier = Modifier.weight(1f))
        ClawSecondaryButton(text = "Voice", onClick = {}, modifier = Modifier.weight(1f))
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClawPill(text = "Realtime", selected = true)
        ClawPill(text = "Dictation")
        ClawPill(text = "Screen")
      }

      ClawEmptyState(
        title = "Nothing needs your attention",
        body = "OpenClaw will surface approvals, failed jobs, and channel issues here.",
      )

      ClawBottomNav(
        items =
          listOf(
            ClawNavItem(key = "overview", label = "Home", icon = Icons.Default.Home),
            ClawNavItem(key = "chat", label = "Chat", icon = Icons.Default.ChatBubble),
            ClawNavItem(key = "voice", label = "Voice", icon = Icons.Default.Mic),
            ClawNavItem(key = "settings", label = "Settings", icon = Icons.Default.Settings),
          ),
        selectedKey = "chat",
        onSelect = {},
      )
    }
  }
}
