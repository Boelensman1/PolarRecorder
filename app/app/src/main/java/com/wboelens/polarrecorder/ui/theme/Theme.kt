package com.wboelens.polarrecorder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class ExtendedColorScheme(
    val warning: ColorFamily,
)

private val lightScheme =
    lightColorScheme(
        primary = primaryLight,
        onPrimary = onPrimaryLight,
        primaryContainer = primaryContainerLight,
        onPrimaryContainer = onPrimaryContainerLight,
        secondary = secondaryLight,
        onSecondary = onSecondaryLight,
        secondaryContainer = secondaryContainerLight,
        onSecondaryContainer = onSecondaryContainerLight,
        tertiary = tertiaryLight,
        onTertiary = onTertiaryLight,
        tertiaryContainer = tertiaryContainerLight,
        onTertiaryContainer = onTertiaryContainerLight,
        error = errorLight,
        onError = onErrorLight,
        errorContainer = errorContainerLight,
        onErrorContainer = onErrorContainerLight,
        background = backgroundLight,
        onBackground = onBackgroundLight,
        surface = surfaceLight,
        onSurface = onSurfaceLight,
        surfaceVariant = surfaceVariantLight,
        onSurfaceVariant = onSurfaceVariantLight,
        outline = outlineLight,
        outlineVariant = outlineVariantLight,
        scrim = scrimLight,
        inverseSurface = inverseSurfaceLight,
        inverseOnSurface = inverseOnSurfaceLight,
        inversePrimary = inversePrimaryLight,
        surfaceDim = surfaceDimLight,
        surfaceBright = surfaceBrightLight,
        surfaceContainerLowest = surfaceContainerLowestLight,
        surfaceContainerLow = surfaceContainerLowLight,
        surfaceContainer = surfaceContainerLight,
        surfaceContainerHigh = surfaceContainerHighLight,
        surfaceContainerHighest = surfaceContainerHighestLight,
    )

private val darkScheme =
    darkColorScheme(
        primary = primaryDark,
        onPrimary = onPrimaryDark,
        primaryContainer = primaryContainerDark,
        onPrimaryContainer = onPrimaryContainerDark,
        secondary = secondaryDark,
        onSecondary = onSecondaryDark,
        secondaryContainer = secondaryContainerDark,
        onSecondaryContainer = onSecondaryContainerDark,
        tertiary = tertiaryDark,
        onTertiary = onTertiaryDark,
        tertiaryContainer = tertiaryContainerDark,
        onTertiaryContainer = onTertiaryContainerDark,
        error = errorDark,
        onError = onErrorDark,
        errorContainer = errorContainerDark,
        onErrorContainer = onErrorContainerDark,
        background = backgroundDark,
        onBackground = onBackgroundDark,
        surface = surfaceDark,
        onSurface = onSurfaceDark,
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = onSurfaceVariantDark,
        outline = outlineDark,
        outlineVariant = outlineVariantDark,
        scrim = scrimDark,
        inverseSurface = inverseSurfaceDark,
        inverseOnSurface = inverseOnSurfaceDark,
        inversePrimary = inversePrimaryDark,
        surfaceDim = surfaceDimDark,
        surfaceBright = surfaceBrightDark,
        surfaceContainerLowest = surfaceContainerLowestDark,
        surfaceContainerLow = surfaceContainerLowDark,
        surfaceContainer = surfaceContainerDark,
        surfaceContainerHigh = surfaceContainerHighDark,
        surfaceContainerHighest = surfaceContainerHighestDark,
    )

val extendedLight =
    ExtendedColorScheme(
        warning =
            ColorFamily(
                warningLight,
                onWarningLight,
                warningContainerLight,
                onWarningContainerLight,
            ),
    )

val extendedDark =
    ExtendedColorScheme(
        warning =
            ColorFamily(
                warningDark,
                onWarningDark,
                warningContainerDark,
                onWarningContainerDark,
            ),
    )

val extendedLightMediumContrast =
    ExtendedColorScheme(
        warning =
            ColorFamily(
                warningLightMediumContrast,
                onWarningLightMediumContrast,
                warningContainerLightMediumContrast,
                onWarningContainerLightMediumContrast,
            ),
    )

val extendedLightHighContrast =
    ExtendedColorScheme(
        warning =
            ColorFamily(
                warningLightHighContrast,
                onWarningLightHighContrast,
                warningContainerLightHighContrast,
                onWarningContainerLightHighContrast,
            ),
    )

val extendedDarkMediumContrast =
    ExtendedColorScheme(
        warning =
            ColorFamily(
                warningDarkMediumContrast,
                onWarningDarkMediumContrast,
                warningContainerDarkMediumContrast,
                onWarningContainerDarkMediumContrast,
            ),
    )

val extendedDarkHighContrast =
    ExtendedColorScheme(
        warning =
            ColorFamily(
                warningDarkHighContrast,
                onWarningDarkHighContrast,
                warningContainerDarkHighContrast,
                onWarningContainerDarkHighContrast,
            ),
    )

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color,
)

val unspecified_scheme =
    ColorFamily(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)

val LocalExtendedColorScheme =
    staticCompositionLocalOf<ExtendedColorScheme> { error("No ExtendedColorScheme provided") }

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable() () -> Unit,
) {
  val colorScheme =
      when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkScheme
        else -> lightScheme
      }

  val extendedColorScheme =
      when {
        darkTheme -> extendedDark
        else -> extendedLight
      }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = colorScheme.background.toArgb()
      window.navigationBarColor = colorScheme.background.toArgb()

      // Optional: set the status bar icons to dark/light based on theme
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
  }

  CompositionLocalProvider(LocalExtendedColorScheme provides extendedColorScheme) {
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
  }
}
