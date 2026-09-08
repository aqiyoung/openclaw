package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawColors
import ai.openclaw.app.ui.design.ClawTypography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Legacy color palette carried over from upstream chat/mobile design.
 *
 * These top-level aliases exist so cherry-picked chat code compiles without
 * pulling in the full upstream design-system commit (which also touched
 * non-android files). Each alias resolves to the fork's own [ClawColors] /
 * [ClawTypography] tokens through the provided [MobileColors] composition
 * local.
 */
@Immutable
internal data class MobileColors(
  val accent: Color,
  val accentSoft: Color,
  val border: Color,
  val borderStrong: Color,
  val cardSurface: Color,
  val codeBg: Color,
  val codeText: Color,
  val codeBorder: Color,
  val text: Color,
  val textSecondary: Color,
  val warning: Color,
  val danger: Color,
) {
  companion object {
    /** Fallback palette used when OpenClawTheme has not been installed. */
    val default: MobileColors = MobileColors(
      accent = Color(0xFF3EDB82),
      accentSoft = Color(0xFF102719),
      border = Color(0xFF242424),
      borderStrong = Color(0xFF3A3A3A),
      cardSurface = Color(0xFF111111),
      codeBg = Color(0xFF111317),
      codeText = Color(0xFFE8EAEE),
      codeBorder = Color(0xFF2B2E35),
      text = Color(0xFFF8F8F8),
      textSecondary = Color(0xFFA8A8A8),
      warning = Color(0xFFE6B956),
      danger = Color(0xFFFF6B6B),
    )
  }
}

internal val LocalMobileColors = staticCompositionLocalOf { MobileColors.default }

/**
 * Resolves [MobileColors] from the currently active [ClawColors] tokens.
 */
internal fun mobileColorsFromClawTheme(
  colors: ClawColors,
  typography: ClawTypography?,
): MobileColors = MobileColors(
  accent = colors.accent,
  accentSoft = colors.successSoft,
  border = colors.border,
  borderStrong = colors.borderStrong,
  cardSurface = colors.surfaceRaised,
  codeBg = colors.codeBg,
  codeText = colors.codeText,
  codeBorder = colors.codeBorder,
  text = colors.text,
  textSecondary = colors.textMuted,
  warning = colors.warning,
  danger = colors.danger,
)

internal object MobileColorsAccessor {
  val accent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.accent
  val accentSoft: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.accentSoft
  val border: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.border
  val borderStrong: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.borderStrong
  val cardSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.cardSurface
  val codeBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.codeBg
  val codeText: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.codeText
  val codeBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.codeBorder
  val text: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.text
  val textSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.textSecondary
  val warning: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.warning
  val danger: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalMobileColors.current.danger
}

internal val mobileAccent: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.accent

internal val mobileAccentSoft: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.accentSoft

internal val mobileBorder: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.border

internal val mobileBorderStrong: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.borderStrong

internal val mobileCardSurface: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.cardSurface

internal val mobileCodeBg: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.codeBg

internal val mobileCodeBorder: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.codeBorder

internal val mobileCodeText: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.codeText

internal val mobileText: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.text

internal val mobileTextSecondary: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.textSecondary

internal val mobileWarning: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.warning

internal val mobileDanger: Color
  @Composable
  @ReadOnlyComposable
  get() = MobileColorsAccessor.danger

internal val mobileCallout: TextStyle
  @Composable
  @ReadOnlyComposable
  get() = ai.openclaw.app.ui.design.ClawTheme.type.label

internal val mobileCaption1:TextStyle
  @Composable
  @ReadOnlyComposable
  get() = ai.openclaw.app.ui.design.ClawTheme.type.caption

internal val mobileCaption2: TextStyle
  @Composable
  @ReadOnlyComposable
  get() = ai.openclaw.app.ui.design.ClawTheme.type.captionSmall
