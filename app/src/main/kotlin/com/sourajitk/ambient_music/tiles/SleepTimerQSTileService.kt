// SPDX-License-Identifier: MIT
// Copyright (c) 2025-2026 Sourajit Karmakar

package com.sourajitk.ambient_music.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sourajitk.ambient_music.R
import com.sourajitk.ambient_music.util.SleepTimerService
import com.sourajitk.ambient_music.util.TileStateUtil
import java.util.concurrent.TimeUnit

class SleepTimerQSTileService : TileService() {

    // 0 == off, rest of the values taken based on how long people take to sleep on average.
    private val timerPresets = listOf(0, 5, 10, 15, 20)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        TileStateUtil.setTileAdded(applicationContext, this::class.java.name, true)
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        TileStateUtil.setTileAdded(applicationContext, this::class.java.name, false)
    }

    override fun onClick() {
        super.onClick()
        // Fetch urrent timer value from the running service.
        val currentTimerValue = SleepTimerService.currentTimerMinutes
        val currentIndex = timerPresets.indexOf(currentTimerValue).let { if (it == -1) 0 else it }
        val nextIndex = (currentIndex + 1) % timerPresets.size
        val nextTimerValue = timerPresets[nextIndex]
        // Send the command to the SleepTimerService to start or stop the timer.
        val intent = Intent(this, SleepTimerService::class.java)
        if (nextTimerValue > 0) {
            val durationInMillis = TimeUnit.MINUTES.toMillis(nextTimerValue.toLong())
            intent.action = SleepTimerService.ACTION_START
            intent.putExtra(SleepTimerService.EXTRA_DURATION_MS, durationInMillis)
        } else {
            intent.action = SleepTimerService.ACTION_STOP
        }
        startService(intent)
        updateTile(nextTimerValue)
    }

    private fun updateTile(currentValue: Int = SleepTimerService.currentTimerMinutes) {
        val tile = qsTile ?: return

        when {
            currentValue == SleepTimerService.CUSTOM_TIMER_VALUE -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.timer_string)
                tile.subtitle = getString(R.string.custom_timer)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_timer_on)
            }

            currentValue > 0 -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.timer_string)
                tile.subtitle = "$currentValue min"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_timer_on)
            }

            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.timer_string)
                tile.subtitle = getString(R.string.timer_string_off)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_timer_off)
            }
        }

        tile.updateTile()
    }
}
