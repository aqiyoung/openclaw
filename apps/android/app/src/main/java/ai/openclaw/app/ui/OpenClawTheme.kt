package ai.openclaw.app.ui

import ai.openclaw.app.AppearanceThemeMode
import ai.openclaw.app.ui.design.ClawDesignTheme
import ai.openclaw.app.ui.design.ClawTheme
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * App theme wrapper that resolves the requested appearance for system surfaces and child themes.
 */

/** Resolved dark-mode flag for the active appearance theme, shared with WebView hosts. */
internal val LocalResolvedAppearanceIsDark = staticCompositionLocalOf { false }
@Composable
fun OpenClawTheme(
  themeMode: AppearanceThemeMode = AppearanceThemeMode.Dark,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val isDark = themeMode.isDark(systemDark = isSystemInDarkTheme())
  val colorScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

  OpenClawSystemBarAppearance(lightAppearance = !isDark)

  CompositionLocalProvider(
    LocalResolvedAppearanceIsDark provides isDark,
  ) {
    MaterialTheme(colorScheme = colorScheme, content = content)
  }
}

@Composable
internal fun OpenClawSystemBarAppearance(lightAppearance: Boolean) {
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      WindowCompat
        .getInsetsController(window, window.decorView)
        .isAppearanceLightStatusBars = lightAppearance
      WindowCompat
        .getInsetsController(window, window.decorView)
        .isAppearanceLightNavigationBars = lightAppearance
    }
  }
}
