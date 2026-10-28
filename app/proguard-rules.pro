# The page script calls into this bridge by name.
-keepclassmembers class dev.wardar.scrollapp.feed.PlatformFeed$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
