package com.monitorung.app.util

object AppCategorizer {

    // ─── Social Media ────────────────────────────────────────────────────────────
    private val socialMediaPackages = setOf(
        // Major platforms
        "com.facebook.", "com.instagram.", "com.twitter.",
        "com.zhiliaoapp.musically", // TikTok
        "com.tiktok.", "com.whatsapp.", "org.telegram.",
        "com.google.android.youtube", "com.reddit.",
        "com.snapchat.", "com.discord.", "com.linkedin.",
        "com.twitter.android", "com.pinterest.",

        // Messaging
        "com.linecorp.", "com.viber.", "com.skype.",
        "org.thoughtcrime.securesms", // Signal
        "com.wechat", "com.immomo.momo",
        "us.zoom.videomeetings", "com.microsoft.teams",
        "com.google.android.apps.meetings",
        "im.minimax", "com.bbm",

        // Regional / local
        "com.bigo.live", "com.hago",
        "com.facebook.lite", "com.instagram.lite",
        "com.facebook.orca", // Facebook Messenger
        "com.facebook.mlite", // Messenger Lite
        "com.twitter.android.lite",
        "com.whatsapp.w4b", // WhatsApp Business

        // Other
        "com.tumblr", "com.medium.reader",
        "com.quora.android", "tv.twitch.android.app",
        "clubhouse.app", "com.clubhouse",
    )

    // ─── Games ───────────────────────────────────────────────────────────────────
    private val gamePackages = setOf(
        // MOBA / Battle Royale
        "com.pubg.krmobile", "com.pubg.imobile",
        "com.mobile.legends", "com.garena.game.id",
        "com.garena.game.ff", "com.garena.game.codm",
        "com.activision.callofduty.warzone", "com.tencent.ig",
        "com.epicgames.fortnite", "com.supercell.brawlstars",
        "com.riotgames.league.wildrift",
        "com.dts.freefireth", "com.dts.freefire",

        // Strategy / Supercell
        "com.supercell.clashofclans", "com.supercell.royale",
        "com.supercell.clashroyale", "com.supercell.boombeach",
        "com.supercell.hayday",

        // Minecraft / Sandbox
        "com.mojang.minecraftpe", "com.mojang.minecraft",
        "com.roblox.client", "com.roblox.playtogether",

        // Casual
        "com.king.candycrushsaga", "com.king.candycrush",
        "com.king.matchmaster", "com.midasplayer.apps.diamonddiggersaga",
        "com.playrix.homescapes", "com.playrix.gardenscapes",

        // RPG / Adventure
        "com.kurogame.wuthering", "com.kurogame.wutheringwaves",
        "com.genshinimpact", "com.hoyoverse.genshinimpact",
        "com.hoyoverse.hkrpg", "com.hoyoverse.zenless",
        "com.activision.bo4",

        // Racing
        "com.ea.game.fifamobile_row",
        "com.ea.game.nfs14_row", "com.ea.game.nfs15_row",

        // Indonesian popular
        "com.tokopedia.tkpd", // not game but distracts — excluded

        // Other popular
        "com.nianticlabs.pokemongo",
        "com.innersloth.amongus", "com.innersloth.AmongUs",
        "com.zhiliaoapp.musically.go", // TikTok Lite
        "com.miniclip.eightballpool",
        "com.miniclip.plagueinc",
        "com.miniclip.subway", // Subway Surfers
        "com.kiloo.subwaysurfers",
        "com.ketchapp.stack", "com.ketchapp.ballz",
        "com.outfit7.talkingtom", "com.outfit7.talkingtom2",
        "com.outfit7.talkingginger",
        "com.imangi.templerun", "com.imangi.templerun2",

        // Garena / SEA
        "com.garena.game.speed",
        "com.garena.game.lite",
        "com.garena.game.aov",
    )

    /**
     * Add app to custom list at runtime (used to extend detection based on what we see).
     */
    private val customSocialMedia = mutableSetOf<String>()
    private val customGames = mutableSetOf<String>()

    fun addCustomSocialMedia(packages: List<String>) {
        customSocialMedia.addAll(packages)
    }

    fun addCustomGames(packages: List<String>) {
        customGames.addAll(packages)
    }

    fun getSocialMediaPackages(): List<String> = socialMediaPackages.toList()
    fun getGamePackages(): List<String> = gamePackages.toList()

    fun categorize(packageName: String): String {
        return when {
            socialMediaPackages.any { packageName.startsWith(it) } -> "social_media"
            customSocialMedia.any { packageName.startsWith(it) } -> "social_media"
            gamePackages.any { packageName.startsWith(it) } -> "game"
            customGames.any { packageName.startsWith(it) } -> "game"
            else -> "other"
        }
    }
}

fun Long.formatDuration(): String {
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours > 0 -> "${hours}j ${minutes}m"
        else -> "${minutes}m"
    }
}

fun Int.formatDuration(): String = this.toLong().formatDuration()
