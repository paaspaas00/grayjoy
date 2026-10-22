package com.futo.platformplayer.compose.onboarding

import com.futo.platformplayer.compose.R

internal enum class SetupStep(val title: Int, val tag: String) {
    Welcome(R.string.setup_welcome, "welcome"),
    Tour(R.string.setup_tour_title, "tour"),
    Theme(R.string.setup_theme_title, "theme"),
    YoutubeLogin(R.string.setup_youtube_title, "youtube"),
    OtherLogin(R.string.setup_other_title, "other-platforms"),
    ImportChoice(R.string.setup_import_choice_title, "import-choice"),
    ImportDetails(R.string.setup_import_title, "import-details"),
    Shorts(R.string.brainrot_shorts, "shorts"),
    HoldSpeed(R.string.hold_to_speed, "hold-speed"),
    SponsorBlock(R.string.sponsorblock, "sponsorblock"),
    Autoplay(R.string.youtube_autoplay, "autoplay"),
    Done(R.string.setup_ready, "done"),
}

internal fun guidedSetupSteps(importYoutube: Boolean, importFiles: Boolean): List<SetupStep> =
    SetupStep.entries.filter { it != SetupStep.ImportDetails || importYoutube || importFiles }
