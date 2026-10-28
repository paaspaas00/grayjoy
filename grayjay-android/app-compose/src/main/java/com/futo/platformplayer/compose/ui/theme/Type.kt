package com.futo.platformplayer.compose.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val GrayjayTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)

/** Compact rhythm only on constrained, narrow phones. System font enlargement stays respected. */
internal val SmallPhoneTypography = GrayjayTypography.copy(
    headlineMedium = GrayjayTypography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 29.sp),
    headlineSmall = GrayjayTypography.headlineSmall.copy(fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = GrayjayTypography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = GrayjayTypography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = GrayjayTypography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = GrayjayTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = GrayjayTypography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = GrayjayTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
)
