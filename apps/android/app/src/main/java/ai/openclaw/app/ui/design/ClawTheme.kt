package ai.openclaw.app.ui.design

import ai.openclaw.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val clawFontFamily =
  FontFamily(
    Font(resId = R.font.manrope_400_regular, weight = FontWeight.Normal),
    Font(resId = R.font.manrope_500_medium, weight = FontWeight.Medium),
    Font(resId = R.font.manrope_600_semibold, weight = FontWeight.SemiBold),
    Font(resId = R.font.manrope_700_bold, weight = FontWeight.Bold),
  )

/**
 * App color tokens consumed by ClawTheme and bridged into Material components.
 */
@Immutable
internal data class ClawColors(
  val canvas: Color,
  val surface: Color,
  val surfaceRaised: Color,
  val surfacePressed: Color,
  val accent: Color,
  val accentSoft: Color,
  val accentBorder: Color,
  val border: Color,
  val borderStrong: Color,
  val text: Color,
  val textMuted: Color,
  val textSubtle: Color,
  val primary: Color,
  val primaryText: Color,
  val success: Color,
  val successSoft: Color,
  val warning: Color,
  val warningSoft: Color,
  val danger: Color,
  val dangerSoft: Color,
  val codeBg: Color,
  val codeText: Color,
  val codeBorder: Color,
)

/**
 * App spacing scale for Compose screens and shared controls.
 */
@Immutable
internal data class ClawSpacing(
  val xxxs: Dp = 4.dp,
  val xxs: Dp = 8.dp,
  val xs: Dp = 12.dp,
  val sm: Dp = 16.dp,
  val md: Dp = 20.dp,
  val lg: Dp = 24.dp,
  val xl: Dp = 32.dp,
  val xxl: Dp = 40.dp,
  val touchTarget: Dp = 48.dp,
)

/**
 * Radius scale for rows, panels, controls, sheets, and status pills.
 */
@Immutable
internal data class ClawRadii(
  val row: Dp = 4.dp,
  val panel: Dp = 5.dp,
  val control: Dp = 6.dp,
  val button: Dp = 8.dp,
  val sheet: Dp = 10.dp,
  val pill: Dp = 12.dp,
)

/**
 * App text styles kept independent from Material typography names.
 */
@Immutable
internal data class ClawTypography(
  val display: TextStyle,
  val title: TextStyle,
  val section: TextStyle,
  val body: TextStyle,
  val label: TextStyle,
  val caption: TextStyle,
  val captionSmall: TextStyle,
  val mono: TextStyle,
)

private val ClawDarkColors =
  ClawColors(
    canvas = Color(0xFF0E1015),
    surface = Color(0xFF0E1015),
    surfaceRaised = Color(0xFF191C24),
    surfacePressed = Color(0xFF1F2330),
    accent = Color(0xFFFF5C5C),
    accentSoft = Color(0x1AFF5C5C),
    accentBorder = Color(0xFFE05252),
    border = Color(0xFF1E2028),
    borderStrong = Color(0xFF2E3040),
    text = Color(0xFFBCBCC0),
    textMuted = Color(0xFF8B8B94),
    textSubtle = Color(0xFF6B6E76),
    primary = Color(0xFFFF5C5C),
    primaryText = Color(0xFFFAFAFA),
    success = Color(0xFF3EDB82),
    successSoft = Color(0xFF102719),
    warning = Color(0xFFE6B956),
    warningSoft = Color(0xFF2B2412),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0x14F87171),
    codeBg = Color(0xFF13151B),
    codeText = Color(0xFFBCBCC0),
    codeBorder = Color(0xFF1E2028),
  )

private val ClawLightColors =
  ClawColors(
    canvas = Color(0xFFFAF9F7),
    surface = Color(0xFFFAF9F7),
    surfaceRaised = Color(0xFFFFFFFF),
    surfacePressed = Color(0xFFEFEBE4),
    accent = Color(0xFFBD4531),
    accentSoft = Color(0x14BD4531),
    accentBorder = Color(0xFFA83C29),
    border = Color(0xFFE8E4DC),
    borderStrong = Color(0xFFD6D0C5),
    text = Color(0xFF403C35),
    textMuted = Color(0xFF6E6960),
    textSubtle = Color(0xFF8A847B),
    primary = Color(0xFFBD4531),
    primaryText = Color(0xFFFFFFFF),
    success = Color(0xFF217747),
    successSoft = Color(0xFFE9F7EF),
    warning = Color(0xFFA56F17),
    warningSoft = Color(0xFFFFF3DC),
    danger = Color(0xFFB91C1C),
    dangerSoft = Color(0x14B91C1C),
    codeBg = Color(0xFFF4F1EC),
    codeText = Color(0xFF403C35),
    codeBorder = Color(0xFFE8E4DC),
  )

internal fun clawColorsForTheme(
  dark: Boolean,
  accentArgb: Long?,
): ClawColors {
  val base = if (dark) ClawDarkColors else ClawLightColors
  val accent = accentArgb?.let(::Color) ?: return base
  return base.copy(
    accent = accent,
    accentSoft = accent.copy(alpha = if (dark) 0.25f else 0.08f).compositeOver(base.canvas),
    accentBorder = lerp(accent, Color.Black, 0.12f),
  )
}

private val LocalClawColors = staticCompositionLocalOf { ClawDarkColors }
private val LocalClawSpacing = staticCompositionLocalOf { ClawSpacing() }
private val LocalClawRadii = staticCompositionLocalOf { ClawRadii() }
private val LocalClawTypography = staticCompositionLocalOf { clawTypography(clawFontFamily) }

/**
 * Composition-local access point for OpenClaw Android design tokens.
 */
internal object ClawTheme {
  val colors: ClawColors
    @Composable
    @ReadOnlyComposable
    get() = LocalClawColors.current

  val spacing: ClawSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalClawSpacing.current

  val radii: ClawRadii
    @Composable
    @ReadOnlyComposable
    get() = LocalClawRadii.current

  val type: ClawTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalClawTypography.current
}

/**
 * Installs OpenClaw design tokens and maps them into MaterialTheme for Material3 controls.
 */
@Composable
internal fun ClawDesignTheme(
  dark: Boolean = true,
  accentArgb: Long? = null,
  content: @Composable () -> Unit,
) {
  val colors = clawColorsForTheme(dark = dark, accentArgb = accentArgb)
  val typography = clawTypography(clawFontFamily)

  CompositionLocalProvider(
    LocalClawColors provides colors,
    LocalClawSpacing provides ClawSpacing(),
    LocalClawRadii provides ClawRadii(),
    LocalClawTypography provides typography,
  ) {
    MaterialTheme(
      colorScheme = clawMaterialColorScheme(colors, dark),
      typography = materialTypography(typography),
      shapes = Shapes(),
      content = content,
    )
  }
}

private fun clawTypography(fontFamily: FontFamily) =
  ClawTypography(
    display =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
      ),
    title =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
      ),
    section =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    body =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
      ),
    label =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
      ),
    caption =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
      ),
    captionSmall =
      TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
      ),
    mono =
      TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
      ),
  )

private fun materialTypography(type: ClawTypography) =
  Typography(
    displayMedium = type.display,
    titleLarge = type.title,
    titleMedium = type.section,
    bodyLarge = type.body,
    labelLarge = type.label,
    labelSmall = type.caption,
  )

private fun clawMaterialColorScheme(
  colors: ClawColors,
  dark: Boolean,
) = if (dark) {
  darkColorScheme(
    primary = colors.primary,
    onPrimary = colors.primaryText,
    background = colors.canvas,
    onBackground = colors.text,
    surface = colors.surface,
    onSurface = colors.text,
    surfaceVariant = colors.surfaceRaised,
    onSurfaceVariant = colors.textMuted,
    outline = colors.border,
    error = colors.danger,
    onError = colors.primaryText,
  )
} else {
  lightColorScheme(
    primary = colors.primary,
    onPrimary = colors.primaryText,
    background = colors.canvas,
    onBackground = colors.text,
    surface = colors.surface,
    onSurface = colors.text,
    surfaceVariant = colors.surfaceRaised,
    onSurfaceVariant = colors.textMuted,
    outline = colors.border,
    error = colors.danger,
    onError = colors.primaryText,
  )
}
