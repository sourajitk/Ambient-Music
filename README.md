# Ambient Music
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
            "albumArtUrl": "chill.jpeg"
        }
    ]
    ```
    
* **Smart Caching**
    To save your data and keep the music flowing even on a spotty connection, the playlist gets cached right on your device. When the app wants to refresh, it grabs the latest JSON from the server.

* **Handy Notification Controls**
    Creates a MediaSession that shows the current track's title, artist, and album art. You can pause, play, and skip right from the notification.
    <div align="center">
    <img src="https://i.imgur.com/oR4SGW5.jpeg" width="600">
    </div>

* **A QS Tile**
    For super-quick access, you can add a Quick Settings tile! Just like how you'd access it on iOS! Just swipe down and tap the tile to start or stop the music. You don't even have to find the app or the notification.
    <div align="center">
      <img src="https://i.imgur.com/L2yGIQL.jpeg" width="309">
      <img src="https://i.imgur.com/yW2KAEE.jpeg" width="300">
    </div>

* **Built with Modern Technologies**
    This project is built with Kotlin and uses Coroutines to handle all the background stuff like fetching music and caching. Some new APIs are used too like `requestAddTileService()`
    
    <div align="center">
      <img src="https://i.imgur.com/4JPWcjE.jpeg" width="300">
      <img src="https://i.imgur.com/ULYqPS2.jpeg" width="300">
    </div>

## Downloads
- [Android 14+](https://github.com/sourajitk/Ambient-Music/releases)


