package dev.wardar.scrollapp

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * A source of short videos. Everything site-specific lives here; the page script
 * (assets/scrollapp.js) is generic and receives these values as its config.
 *
 * Site markup changes often: if ads stop being skipped or the like button stops working,
 * the markers and selectors below are what to update.
 */
enum class Platform(
    val title: String,
    val shortName: String,
    /** What one item is called, plural ("shorts"). */
    val itemNoun: String,
    /** Series color for this source; the three were validated together for color-blind safety on the dark surface. */
    val color: Color,
    val feedUrl: String,
    val loginUrl: String,
    /** Origins the page script is injected into. */
    val origins: Set<String>,
    /** Path of a single video's page, used for sharing. */
    val linkPattern: String,
    /** Whole-line labels that mark an item as an ad. */
    val adMarkers: List<String>,
    /** Labels that mark a non-video item (people suggestions, Threads posts) as filler. */
    val nonVideoMarkers: List<String>,
    val adSelector: String = "",
    val likeSelector: String = "",
    val repostPattern: String = "",
    /** Extra panels (comments, share sheets) during which vertical swipes belong to the page. */
    val panelSelector: String = "",
    /** CSS hiding promo slots that sit outside the feed items. */
    val css: String = "",
    val skipLive: Boolean = false,
    /** The page has no per-video links; the id has to come from its React props. */
    val videoIdInProps: Boolean = false,
) {
    YOUTUBE(
        title = "YouTube Shorts",
        shortName = "Shorts",
        itemNoun = "shorts",
        color = Color(0xFFD95926),
        feedUrl = "https://m.youtube.com/shorts/",
        loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2Fshorts%2F",
        origins = setOf("https://m.youtube.com", "https://www.youtube.com"),
        linkPattern = "^/shorts/[\\w-]+",
        adMarkers = listOf("Sponsored", "Ad", "Реклама", "Жарнама"),
        nonVideoMarkers = emptyList(),
        adSelector = "ytm-ad-slot-renderer, ad-slot-renderer, ytm-promoted-sparkles-web-renderer, ytm-reel-player-ad-overlay-renderer",
        likeSelector = "ytm-like-button-renderer button, like-button-view-model button",
        panelSelector = "ytm-engagement-panel-section-list-renderer, bottom-sheet-container, .engagement-panel-container",
        css = "ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer, ytm-companion-ad-renderer, " +
            "ytm-mealbar-promo-renderer, ytm-statement-banner-renderer, ytm-ad-slot-renderer, ad-slot-renderer " +
            "{ display: none !important; }",
    ),
    INSTAGRAM(
        title = "Instagram Reels",
        shortName = "Reels",
        itemNoun = "reels",
        color = Color(0xFF9085E9),
        feedUrl = "https://www.instagram.com/reels/",
        loginUrl = "https://www.instagram.com/accounts/login/?next=%2Freels%2F",
        origins = setOf("https://www.instagram.com"),
        linkPattern = "^/reels?/[\\w-]+",
        adMarkers = listOf("Sponsored", "Реклама", "Жарнама"),
        nonVideoMarkers = listOf(
            "Suggested for you", "Suggested for You", "Suggested accounts", "People you may know", "Threads",
            "Рекомендации для вас", "Рекомендуемые аккаунты", "Возможно, вы знакомы",
        ),
        likeSelector = "svg[aria-label='Like'], svg[aria-label='Unlike'], svg[aria-label='Нравится'], svg[aria-label='Не нравится']",
        repostPattern = "^(repost|репост)",
    ),
    TIKTOK(
        title = "TikTok",
        shortName = "TikTok",
        itemNoun = "TikToks",
        color = Color(0xFF199E70),
        feedUrl = "https://www.tiktok.com/foryou",
        loginUrl = "https://www.tiktok.com/login",
        origins = setOf("https://www.tiktok.com", "https://m.tiktok.com"),
        linkPattern = "/video/\\d+",
        adMarkers = listOf("Sponsored", "Ad", "Promoted", "Promotional content", "Реклама", "Жарнама"),
        nonVideoMarkers = listOf("Suggested accounts", "Рекомендуемые аккаунты"),
        adSelector = "[data-e2e='ad-tag'], [data-e2e='video-ad-tag']",
        likeSelector = "[data-e2e='like-icon'], [data-e2e='browse-like-icon']",
        repostPattern = "^(repost|репост)",
        panelSelector = "[data-e2e='comment-list'], [data-e2e='browse-comment-list']",
        skipLive = true,
        videoIdInProps = true,
    );

    /** The script injected into this platform's pages: its config followed by the shared page script. */
    fun pageScript(script: String): String {
        val config = JSONObject()
            .put("adMarkers", JSONArray(adMarkers))
            .put("nonVideoMarkers", JSONArray(nonVideoMarkers))
            .put("adSelector", adSelector)
            .put("likeSelector", likeSelector)
            .put("repostPattern", repostPattern)
            .put("panelSelector", panelSelector)
            .put("linkPattern", linkPattern)
            .put("css", css)
            .put("skipLive", skipLive)
            .put("videoIdInProps", videoIdInProps)
        return "window.__shConfig = $config;\n$script"
    }
}
