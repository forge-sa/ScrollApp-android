# ScrollApp

There is also an [`iPhone version`](https://github.com/forge-sa/ScrollAppIOS).

One vertical feed that mixes **YouTube Shorts, Instagram Reels and TikToks**, with autoscroll,
ad/filler skipping, like and repost buttons, and statistics on how much you scroll.

## Install

Sideload `ScrollApp.apk` (Android 10+): `adb install ScrollApp.apk`, or copy it to the phone and open it.

On first launch you land on **Settings**: tap **Log in** for each source so the feed shows your own
recommendations, then **Done**. Instagram and TikTok mostly need an account; YouTube Shorts works without one.

## Using it

| Action | How |
|---|---|
| Next / previous video (across all sources) | Swipe up / down |
| Like | Heart in the bottom bar (double-tap also works on Reels and TikTok) |
| Repost | Repost button: uses the site's own repost when it shows one, otherwise opens the share sheet with the video's link |
| Autoscroll | Double-arrow button (on by default): moves on when a video ends |
| Sound | Speaker button |
| Statistics | Bar-chart button: time scrolled, number of shorts / reels / TikToks, ads skipped, per-day chart |
| Reload a source | Tap the colored source name in the bottom bar |

Settings also lets you turn sources off, choose **Take turns** or **Shuffle** mixing, and turn off ad skipping.

## How it works

Each source runs its own mobile website in a WebView, signed in with your account. The WebViews are
stacked; a swipe slides the current one away and reveals the next source, which was already lined up
(and buffered) on its next unseen video. A page script (`app/src/main/assets/scrollapp.js`) tells the app
which video is on screen, when it ends (short videos loop, so a loop counts as the end), and whether
the item is an ad, a LIVE stream, or a "suggested for you" / Threads card, which get skipped.

- **Ads**: Google's ad servers are blocked outright. Instagram and TikTok ads come inside the normal
  feed, so they are recognised by their "Sponsored" label and skipped, usually in the background
  before their turn comes up.
- **Threads posts / people suggestions**: these live in Instagram's home feed; the app only ever
  shows the Reels feed, and filler cards that do show up there are skipped.
- **Statistics** are stored on the phone only. A video counts once it has actually played.

The page script is shared with the iPhone version: after changing it, copy it to
`../ScrollAppIOS/PageScript/scrollapp.js` and run `python3 PageScript/embed.py` there.

## When a site changes

Sites change their markup without notice. If ads stop being skipped or the like button stops
working, the labels and selectors to update are all in `Platform.kt`. With a debug build, open
`chrome://inspect` on a computer to look at the live pages; `adb logcat -s ScrollApp` shows what the
feed is doing (skips, autoscrolls, sources that won't play).

## Limitations

- Google sometimes refuses to sign in inside an app's browser. YouTube Shorts still works logged out,
  just with general recommendations.
- Like/repost press the site's own buttons, so they need you to be logged in to that site.
- Skipping ads and automating these sites is against their terms of service. This is a personal,
  sideloaded app; it cannot go on the Play Store.

## Build

```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk, signed with the debug key
```
