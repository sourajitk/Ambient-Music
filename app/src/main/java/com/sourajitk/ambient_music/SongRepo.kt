package com.sourajitk.ambient_music

object SongRepo {
    val songs = listOf(
        "https://aodp-ssl.itunes.apple.com/itunes-assets/FuseSocial221/v4/60/b0/fd/60b0fde0-d1c9-91d1-617a-2d3a83a793f9/mzaf_17295500299829221229.plus.aac.a.m4a?accessKey=1750304462_2402595721163681266_cTA687AmYFamMANtydv3moy34%2FtEN%2Bd3mn%2F2eBtoE6n8t%2F9Tc6uECQ49dPeg2F%2B655w%2FFH9MPYW5e5DnjfiYLSH6AhPxDEIMnltPACLt%2BLaMCv%2FKqk5llgEXcEYwXTHx4d2xCvZa4jRRghE%2FpmF1wsSAiR2noJ6gU%2BxdnByZsQVe8d4P%2FdZuHcvkZIguY417kdvU7%2BLTpW2RuRrkArEMEw%3D%3D",
    )
    var currentTrackIndex = 0
        private set

    fun selectNextTrack() {
        if (songs.isNotEmpty()) {
            currentTrackIndex = (currentTrackIndex + 1) % songs.size
        }
    }
}