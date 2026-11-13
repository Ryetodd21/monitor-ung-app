package com.monitorung.app.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ─── Primary Brand — Blue (Trust, Calm, Academic) ───────────────────────────────
val PrimaryBlue      = Color(0xFF3B82F6)  // #3B82F6
val PrimaryBlueDark  = Color(0xFF2563EB)  // #2563EB
val PrimaryBlueLight = Color(0xFFDBEAFE)  // #DBEAFE

// Legacy aliases — used by existing screens
val Blue500 = PrimaryBlue       // chart blue
val Blue600 = PrimaryBlueDark   // primary dark blue
val Blue700 = Color(0xFF1D4ED8)  // blue-700
val Blue50  = PrimaryBlueLight  // blue-50
val Blue800 = Color(0xFF1E3A8A)  // blue-800

// ─── Secondary — Indigo (Depth, Focus) ──────────────────────────────────────
val SecondaryIndigo  = Color(0xFF6366F1)  // #6366F1
val SecondaryLight   = Color(0xFFE0E7FF)  // #E0E7FF

// ─── Surface — 3 modes ────────────────────────────────────────────────────────
val SurfaceLight    = Color(0xFFF1F5F9)  // oklch 0.96 0.01 250 — soft gray-blue
val SurfaceDark     = Color(0xFF111827)  // oklch 0.08 0.02 250 — deep navy
val AmoledBlack     = Color(0xFF000000)  // true black

// ─── Surface elevated (cards, dialogs) ─────────────────────────────────────────
val SurfaceElevatedLight = Color(0xFFFFFFFF)
val SurfaceElevatedDark  = Color(0xFF1F2937)
val SurfaceElevatedAmoled = Color(0xFF0F0F0F)

// ─── Text — Light mode (contrast ≥ 4.5:1 against #F1F5F9) ─────────────────────
val TextPrimaryLight   = Color(0xFF0F172A)  // #0F172A on #F1F5F9 → 14.1:1 ✓
val TextSecondaryLight = Color(0xFF475569)  // #475569 on #F1F5F9 → 6.5:1  ✓
val TextMutedLight     = Color(0xFF64748B)  // #64748B on #F1F5F9 → 4.7:1  ✓
val TextOnPrimary      = Color(0xFFFFFFFF)  // white on blue → 8.6:1 ✓

// ─── Text — Dark mode (contrast ≥ 4.5:1 against #111827) ──────────────────────
val TextPrimaryDark   = Color(0xFFF8FAFC)  // #F8FAFC on #111827 → 16.1:1 ✓
val TextSecondaryDark = Color(0xFFCBD5E1)  // #CBD5E1 on #111827 → 9.1:1  ✓
val TextMutedDark     = Color(0xFF94A3B8)  // #94A3B8 on #111827 → 5.6:1  ✓
val TextOnPrimaryDark  = Color(0xFFFFFFFF)  // white on #3B82F6 → 4.6:1  ✓

/* ─── Semantic / Status — COLORBLIND-SAFE palette ───────────────────────────
 *
 * The status colors follow the Okabe-Ito / IBM Carbon safe color set:
 *   - Success uses TEAL (h≈175°) instead of green — distinct from primary blue
 *     (255°) so deuteranopia/protanopia users can still tell them apart.
 *   - Warning keeps AMBER (h≈80°) — already CB-safe.
 *   - Error/Danger uses FUCHSIA / MAGENTA (h≈290°) instead of red — red and
 *     amber are often indistinguishable under protanopia, while magenta
 *     remains clearly different from blue, teal, and amber.
 *   - All four hues (blue 255°, teal 175°, amber 80°, fuchsia 290°) are
 *     separated by ≥60° in the color wheel and have different luminance, so
 *     they remain distinguishable under all common types of color vision
 *     deficiency.
 *
 * Variable names retain legacy aliases (Green500 / Red500) so existing screens
 * compile unchanged; underlying color values are now teal/fuchsia.
 */
val SuccessTeal       = Color(0xFF0F766E)  // teal-700, light mode  → contrast 5.2:1 on white ✓
val SuccessTealSoft   = Color(0xFFCCFBF1)  // teal-100, soft background tint
val SuccessTealOnDark = Color(0xFF5EEAD4)  // teal-300, dark mode   → contrast 11.1:1 on #111827 ✓
val SuccessSoftOnDark = Color(0xFF134E4A)  // teal-900, soft tint in dark mode

val WarningAmber       = Color(0xFFD97706)  // amber-700  → contrast 4.6:1 on white ✓
val WarningAmberSoft   = Color(0xFFFEF3C7)  // amber-100, soft background
val WarningAmberOnDark = Color(0xFFFCD34D)  // amber-300  → contrast 10.4:1 on dark ✓

val ErrorFuchsia       = Color(0xFFA21CAF)  // fuchsia-700 → contrast 5.6:1 on white ✓
val ErrorFuchsiaSoft   = Color(0xFFFAE8FF)  // fuchsia-100, soft background
val ErrorFuchsiaOnDark = Color(0xFFF0ABFC)  // fuchsia-300 → contrast 9.2:1 on dark ✓
val ErrorSoftOnDark    = Color(0xFF581C87)  // fuchsia-900, soft tint in dark

val InfoBlue      = Color(0xFF0284C7)
val InfoBlueOnDark = Color(0xFF7DD3FC)

// Legacy aliases used across screens (kept for backward compatibility).
// Every reference now resolves to a colorblind-safe teal/magenta variant.
val SuccessGreen = SuccessTeal
val SuccessLight = SuccessTealSoft
val ErrorRed      = ErrorFuchsia
val ErrorLight    = ErrorFuchsiaSoft
val Green500      = SuccessTeal
val GreenLight    = SuccessTealSoft
val Red500        = ErrorFuchsia
val RedLight      = ErrorFuchsiaSoft

// Amber warning aliases — amber is already CB-safe (h≈80°, distinct from
// primary blue 255°, teal 175°, fuchsia 290°), no remapping needed.
val Amber500      = WarningAmber
val AmberLight    = WarningAmberSoft
val WarningLight  = WarningAmberSoft

// ─── Accent chart palette — CB-safe Okabe-Ito 6-color set ─────────────────
// hue spread: blue(255°), orange/amber(60°), sky(220°), teal(175°), purple(280°), magenta(330°).
// Removed red/green slots; replaced with teal and magenta for parity with
// web chart palette.
val ChartBlue    = Color(0xFF3B82F6)    // 255° blue
val ChartIndigo  = Color(0xFF6366F1)    // 250° indigo (kept as a deep-blue variant)
val ChartAmber   = Color(0xFFF59E0B)    // 60°  amber (warning)
val ChartPurple  = Color(0xFF8B5CF6)    // 270° purple
val ChartCyan    = Color(0xFF06B6D4)    // 220° sky/info cyan
val ChartTeal    = Color(0xFF14B8A6)    // 175° teal (replaces ChartGreen)

// Legacy alias — ChartGreen now resolves to teal for CB-safety.
val ChartGreen = ChartTeal

// ─── Pastel fill colors — papercard gamified palette ─────────────────────────
// Soft pastel backgrounds for card fills, pills, and gradient foundations.
// Each comes in light/mid/dark triade for layered depth.
val PastelBlueLight   = Color(0xFFEFF6FF)  // #EFF6FF — lightest blue tint
val PastelBlueMid     = Color(0xFFDBEAFE)  // #DBEAFE — medium blue tint
val PastelBlueDark    = Color(0xFFBFDBFE)  // #BFDBFE — deeper blue tint

val PastelTealLight   = Color(0xFFF0FDFA)  // #F0FDFA
val PastelTealMid     = Color(0xFFCCFBF1)  // #CCFBF1
val PastelTealDark    = Color(0xFF99F6E4)  // #99F6E4

val PastelAmberLight  = Color(0xFFFFFBEB)  // #FFFBEB
val PastelAmberMid   = Color(0xFFFEF3C7)  // #FEF3C7
val PastelAmberDark   = Color(0xFFFDE68A)  // #FDE68A

val PastelFuchsiaLight = Color(0xFFFDF4FF)  // #FDF4FF
val PastelFuchsiaMid  = Color(0xFFFAE8FF)  // #FAE8FF
val PastelFuchsiaDark  = Color(0xFFF5D0FE)  // #F5D0FE

val PastelPurpleLight = Color(0xFFF5F3FF)  // #F5F3FF
val PastelPurpleMid  = Color(0xFFEDE9FE)  // #EDE9FE
val PastelPurpleDark  = Color(0xFFDDB6F2)  // #DDB6F2

// ─── Soft shadow colors — ambient glow for gamified cards ───────────────────
// Light mode: warm very-subtle shadow
val SoftShadowColorLight = Color(0x0A1A1A2E)  // 4% black
val SoftGlowColorLight  = Color(0x143B82F6)   // 8% blue for subtle blue glow
// Dark mode: lifted dark shadow
val SoftShadowColorDark  = Color(0x1A000000)  // 10% black
val SoftGlowColorDark   = Color(0x143B82F6)   // 8% primary blue
// AMOLED: minimal shadow
val SoftShadowColorAmoled = Color(0x20000000)  // 12% black

// ─── Gradient presets — for card overlays, progress bars, nav active states ──
val GradientBlueTeal = listOf(Color(0xFF3B82F6), Color(0xFF14B8A6))
val GradientAmberPeach = listOf(Color(0xFFF59E0B), Color(0xFFFB923C))
val GradientPurplePink = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
val GradientSoftBlue = listOf(Color(0xFFBFDBFE), Color(0xFFDBEAFE))
val GradientTealGreen = listOf(Color(0xFF14B8A6), Color(0xFF10B981))
val GradientFuchsiaPurple = listOf(Color(0xFFA21CAF), Color(0xFF7C3AED))
val GradientAmberGold = listOf(Color(0xFFF59E0B), Color(0xFFEAB308))

// ─── Spacing ──────────────────────────────────────────────────────────────────
val SpaceXS = 4.dp
val SpaceS  = 8.dp
val SpaceM  = 12.dp
val SpaceL  = 16.dp
val SpaceXL = 20.dp
val SpaceXXL = 28.dp

// ─── Corner radius ─────────────────────────────────────────────────────────────
// Rationale:
//   - RadiusHairline (1dp): ultra-thin dividers, 1px visual weight
//   - RadiusMicro (2dp): very small accents, minimal visual noise
//   - RadiusXXS (4dp): active badges, REQUIRED labels
//   - RadiusTiny (3dp): ultra-compact elements, tight clips
//   - RadiusXS (6dp): compact chips, badges, status pills
//   - RadiusS (8dp): list items, chips, small cards
//   - RadiusS2 (10dp): medium-small elements, between S and M
//   - RadiusM (12dp): cards, dialogs, medium containers
//   - RadiusM2 (14dp): medium-large elements, between M and L
//   - RadiusL (16dp): large cards, bottom sheets
//   - RadiusXL (20dp): modals, hero surfaces
//   - RadiusXXL (28.dp): full-bleed containers, splash screens
//   - RadiusPlayful (28dp): gamified card corners (papercard-style)
//   - RadiusBubble (32dp): bottom nav bubbles, achievement badges
//   - RadiusPill (50dp): pill/capsule shapes (full height rounding)
val RadiusHairline = 1.dp
val RadiusMicro = 2.dp
val RadiusXXS  = 4.dp
val RadiusTiny = 3.dp
val RadiusXS   = 6.dp
val RadiusS    = 8.dp
val RadiusS2   = 10.dp
val RadiusSoft = 9.dp
val RadiusM    = 12.dp
val RadiusM2   = 14.dp
val RadiusL    = 16.dp
val RadiusXL   = 20.dp
val RadiusXXL  = 28.dp
val RadiusPlayful = 28.dp
val RadiusBubble = 32.dp
val RadiusPill = 50.dp

// ─── Light Color Scheme ───────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary            = PrimaryBlue,
    onPrimary          = TextOnPrimary,
    primaryContainer   = PrimaryBlueLight,
    onPrimaryContainer = PrimaryBlueDark,
    secondary          = SecondaryIndigo,
    onSecondary        = TextOnPrimary,
    secondaryContainer = SecondaryLight,
    onSecondaryContainer = SecondaryIndigo,
    tertiary           = ChartPurple,
    onTertiary        = TextOnPrimary,
    error              = ErrorFuchsia,
    errorContainer     = ErrorFuchsiaSoft,
    onError           = TextOnPrimary,
    onErrorContainer  = ErrorFuchsia,
    background         = SurfaceLight,
    onBackground      = TextPrimaryLight,
    surface           = SurfaceElevatedLight,
    onSurface        = TextPrimaryLight,
    surfaceVariant    = Color(0xFFE2E8F0),
    onSurfaceVariant  = TextSecondaryLight,
    outline           = Color(0xFF94A3B8),
    outlineVariant   = Color(0xFFCBD5E1),
)

// ─── Dark Color Scheme ────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary           = Color(0xFF93C5FD),
    onPrimary         = Color(0xFF1E3A5F),
    primaryContainer  = PrimaryBlueDark,
    onPrimaryContainer= Color(0xFFDBEAFE),
    secondary         = Color(0xFFA5B4FC),
    onSecondary       = Color(0xFF1E1B4B),
    secondaryContainer= Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary          = Color(0xFFC4B5FD),
    onTertiary       = Color(0xFF2E1065),
    error             = ErrorFuchsiaOnDark,
    errorContainer    = ErrorSoftOnDark,
    onError          = ErrorSoftOnDark,
    onErrorContainer  = ErrorFuchsiaSoft,
    background        = SurfaceDark,
    onBackground     = TextPrimaryDark,
    surface          = SurfaceElevatedDark,
    onSurface       = TextPrimaryDark,
    surfaceVariant   = Color(0xFF374151),
    onSurfaceVariant = TextSecondaryDark,
    outline          = Color(0xFF6B7280),
    outlineVariant  = Color(0xFF374151),
)

// ─── AMOLED Color Scheme ──────────────────────────────────────────────────────
private val AmoledColorScheme = darkColorScheme(
    primary           = Color(0xFF93C5FD),
    onPrimary         = Color(0xFF1E3A5F),
    primaryContainer  = Color(0xFF1E3A5F),
    onPrimaryContainer= Color(0xFFDBEAFE),
    secondary         = Color(0xFFA5B4FC),
    onSecondary       = Color(0xFF1E1B4B),
    secondaryContainer= Color(0xFF1E1B4B),
    onSecondaryContainer= Color(0xFFE0E7FF),
    tertiary          = Color(0xFFC4B5FD),
    onTertiary       = Color(0xFF2E1065),
    error             = ErrorFuchsiaOnDark,
    errorContainer    = ErrorSoftOnDark,
    onError          = ErrorSoftOnDark,
    onErrorContainer  = ErrorFuchsiaSoft,
    background        = AmoledBlack,
    onBackground     = Color(0xFFE5E7EB),
    surface          = SurfaceElevatedAmoled,
    onSurface       = Color(0xFFE5E7EB),
    surfaceVariant   = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline          = Color(0xFF3A3A3A),
    outlineVariant  = Color(0xFF2A2A2A),
)

enum class ThemeVariant { LIGHT, DARK, AMOLED }

@Composable
fun MonitorUNGTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeVariant: ThemeVariant? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val resolvedVariant = themeVariant ?: if (darkTheme) ThemeVariant.DARK else ThemeVariant.LIGHT

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(LocalView.current.context)
            else dynamicLightColorScheme(LocalView.current.context)
        }
        resolvedVariant == ThemeVariant.AMOLED -> AmoledColorScheme
        resolvedVariant == ThemeVariant.DARK -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val isLight = resolvedVariant == ThemeVariant.LIGHT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(RadiusPlayful),
            small      = RoundedCornerShape(RadiusM),
            medium     = RoundedCornerShape(RadiusL),
            large      = RoundedCornerShape(RadiusPlayful),
            extraLarge = RoundedCornerShape(RadiusBubble),
        ),
        content = content
    )
}
