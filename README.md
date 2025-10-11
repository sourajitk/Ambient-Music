<div align="center">
    <img src="https://i.imgur.com/UTyWfnm.png" width="1000">
</div>
</br>
This app allows you to play a curated stream of ambient music in the background without any fuss just like you can on iOS! The idea is to have a "set and forget" music player. You hit play, and it handles the rest, providing a seamless stream of ambient tunes. No complex UI or no creating playlists.

## Features
* **Tracks Fetched on Demand**
    The app pulls its song list from a remote JSON file. This means I can update the playlist with new tracks anytime without you needing to update the app!
    ```json
    [    
        {
            "url": "https://song_url",
            "title": "Laidback Lo-Fi",
            "artist": "Ambient Music Chill",
            "albumArtUrl": "chill.jpeg",
            "genre": "chill"
        }
    ]
    ```
    
* **Smart Caching**
    To save your data and keep the music flowing even on a spotty connection, the playlist gets cached right on your device. When the app wants to refresh, it grabs the latest JSON from the server.

* **Handy Notification Controls**
    Creates a MediaSession that shows the current track's title, artist, and album art. You can pause, play, and skip right from the notification.
    <div align="center">
    <img src="https://i.imgur.com/ShtwPFq.png" width="600">
    </div>

* **A QS Tile**
    For super-quick access, you can add a Quick Settings tile! Just like how you'd access it on iOS! Just swipe down and tap the tile to start or stop the music. You don't even have to find the app or the notification.
    <div align="center">
      <img src="https://i.imgur.com/L2yGIQL.jpeg" width="309">
      <img src="https://i.imgur.com/Pu8QIWu.png" width="300">
    </div>

* **Easy Information about Updates**
  Get to know when I update the app based on notifications posted to you! Dismissed the notification but still want to know if you're on the latest version? Tap to check for updates within the app! Includes use of newest APIs for notification broadcasting for the latest version of Android!
      <div align="center">
      <img src="https://i.imgur.com/DRFXLZs.png" width="240">
      <img src="https://i.imgur.com/bzc8b5w.png" width="240">
      <img src="https://i.imgur.com/dZw0V9n.png" width="240">
    </div>

* **Built with Modern Technologies**
    This project is built with Kotlin and uses Coroutines to handle all the background stuff like fetching music and caching. UI is completely written using Jetpack Compose. Some new APIs are used too like `requestAddTileService()`
    
    <div align="center">
      <img src="https://i.imgur.com/PDKLZWi.png" width="240">
      <img src="https://i.imgur.com/XR1D3ej.png" width="240">
      <img src="https://i.imgur.com/GPxgUCH.png" width="240">
    </div>

* **Support for multiple form factors**
Enable navigation siderails when the display gets beyond a certain DPI for optimal user experience even for unconventional form factors and screen sizes.
    <div align="center">
      <img src="https://i.imgur.com/q7kvM29.png" width="750">
    </div>

## Downloads (Android 12+) [![Github All Releases](https://img.shields.io/github/downloads/sourajitk/Ambient-Music/total.svg)](https://tooomm.github.io/github-release-stats/?username=Sourajitk&repository=Ambient-Music)
<p align="left">
  <p align="left">
    <a href="https://play.google.com/store/apps/details?id=com.sourajitk.ambient_music"><img src="https://i.imgur.com/ElcHPWC.png" alt="Get it on the Google play store" height="80"></a>
    <a href="https://github.com/sourajitk/Ambient-Music/releases"><img src="https://raw.githubusercontent.com/andOTP/andOTP/master/assets/badges/get-it-on-github.png" alt="Get it on Github" height="80"></a>
  </p>
</p>

## Credits
Big thanks to all my testers for constantly testing my app and providing constructive feedback to help make it better throughout all the releases! 😄

Special thanks to @ralph950412 for helping me get these tracks in the first place.

## Documentation
Some useful links I referred to while building the app:

- [Compose Navigation](https://developer.android.com/jetpack/compose/navigation) 
- [Compose Material 3](https://developer.android.com/jetpack/compose/material3)
- [requestAddTileService](https://developer.android.com/reference/android/service/quicksettings/TileService#requestAddTileService(android.content.ComponentName))
- [Window Size Classes](https://developer.android.com/guide/topics/large-screens/support-different-screen-sizes)
- [NavigationRail in Compose](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#NavigationRail(kotlin.Function1))

## Featured In
<img src="https://www.androidauthority.com/wp-content/uploads/2025/07/Photo-of-an-Android-phone-running-Ambient-Music.jpg.webp" width="550">

[Android Authority](https://www.androidauthority.com/apple-ambient-music-on-android-3578211)


## License
This project is licensed under the [MIT License](https://opensource.org/licenses/MIT).  
You are free to use, modify, and distribute this software with proper attribution.
