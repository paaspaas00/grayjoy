package com.futo.platformplayer.compose.ui

enum class NavigationLayout {
    BottomBar,
    Rail,
    PermanentDrawer,
}

fun navigationLayoutFor(widthDp: Int): NavigationLayout = when {
    widthDp < 600 -> NavigationLayout.BottomBar
    widthDp < 1_200 -> NavigationLayout.Rail
    else -> NavigationLayout.PermanentDrawer
}
