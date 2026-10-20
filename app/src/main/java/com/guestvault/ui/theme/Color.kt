package com.guestvault.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------- Brand ----
val PrimaryLight = Color(0xFF312E81)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE0E7FF)
val OnPrimaryContainerLight = Color(0xFF1E1B4B)

val PrimaryDark = Color(0xFFA5B4FC)
val OnPrimaryDark = Color(0xFF1E1B4B)
val PrimaryContainerDark = Color(0xFF312E81)
val OnPrimaryContainerDark = Color(0xFFE0E7FF)

val SecondaryLight = Color(0xFF0D9488)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCCFBF1)
val OnSecondaryContainerLight = Color(0xFF134E4A)

val SecondaryDark = Color(0xFF5EEAD4)
val OnSecondaryDark = Color(0xFF134E4A)
val SecondaryContainerDark = Color(0xFF0F766E)
val OnSecondaryContainerDark = Color(0xFFCCFBF1)

// ------------------------------------------------------------ Neutral ------
val BackgroundLight = Color(0xFFF8FAFC)
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val OnSurfaceVariantLight = Color(0xFF64748B)
val OutlineLight = Color(0xFFCBD5E1)

val BackgroundDark = Color(0xFF0F172A)
val OnBackgroundDark = Color(0xFFF8FAFC)
val SurfaceDark = Color(0xFF1E293B)
val OnSurfaceDark = Color(0xFFF8FAFC)
val SurfaceVariantDark = Color(0xFF334155)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF475569)

// ----------------------------------------------------------- Semantic ------
val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)
val Info = Color(0xFF3B82F6)

// ------------------------------------------------------------ Extras -------
val Indigo900 = Color(0xFF1E1B4B)
val Indigo800 = Color(0xFF312E81)
val Indigo600 = Color(0xFF4F46E5)
val Indigo300 = Color(0xFFA5B4FC)
val Teal500 = Color(0xFF14B8A6)

/** Deep indigo hero gradient used on the dashboard and guest home. */
val HeroGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF4338CA), Color(0xFF312E81), Color(0xFF1E1B4B))
)

/** Lighter gradient for the guest mode banner. */
val SoftHeroGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFEEF2FF), Color(0xFFE0E7FF))
)

/** Teal accent gradient used for success / active states. */
val TealGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF14B8A6), Color(0xFF0D9488))
)
