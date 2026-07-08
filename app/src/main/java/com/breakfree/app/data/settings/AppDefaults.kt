package com.breakfree.app.data.settings

object AppDefaults {
    val DURATION_OPTIONS = listOf(
        BreakDurationOption("30s", 30),
        BreakDurationOption("1m", 60),
        BreakDurationOption("5m", 300),
        BreakDurationOption("10m", 600),
        BreakDurationOption("30m", 1800),
        BreakDurationOption("1h", 3600),
        BreakDurationOption("2h", 7200)
    )

    const val GRACE_PERIOD_SECONDS = 10

    val DOOM_SCROLLING_DOMAINS = listOf(
        "tiktok.com",
        "instagram.com",
        "facebook.com",
        "x.com",
        "twitter.com",
        "youtube.com",
        "reddit.com",
        "snapchat.com",
        "pinterest.com",
        "linkedin.com",
        "twitch.tv"
    )
}
