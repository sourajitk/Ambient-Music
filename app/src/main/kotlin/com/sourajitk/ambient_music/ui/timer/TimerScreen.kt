package com.sourajitk.ambient_music.ui.timer

import android.os.CountDownTimer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.concurrent.TimeUnit

// Enum to represent the different states of the timer
private enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen() {
    var totalTime by remember { mutableLongStateOf(0L) }
    var remainingTime by remember { mutableLongStateOf(0L) }
    var timerState by remember { mutableStateOf(TimerState.IDLE) }
    var showTimePicker by remember { mutableStateOf(false) }

    var timer: CountDownTimer? by remember { mutableStateOf(null) }

    // This effect handles the countdown logic
    DisposableEffect(key1 = timerState, key2 = remainingTime) {
        if (timerState == TimerState.RUNNING) {
            timer = object : CountDownTimer(remainingTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingTime = millisUntilFinished
                }

                override fun onFinish() {
                    remainingTime = 0
                    timerState = TimerState.IDLE
                }
            }.start()
        }
        onDispose {
            timer?.cancel()
        }
    }

    if (showTimePicker) {
        TimerPickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val newTotalTime = (hour * 3600 + minute * 60) * 1000L
                totalTime = newTotalTime
                remainingTime = newTotalTime
                timerState = TimerState.IDLE // Reset state when new time is set
                showTimePicker = false
            }
        )
    }

    Scaffold{ innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Timer Display
                TimerDisplay(
                    remainingTime = remainingTime,
                    onClick = {
                        // Only allow setting time when the timer is idle
                        if (timerState == TimerState.IDLE) {
                            showTimePicker = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Timer Controls
                TimerControls(
                    timerState = timerState,
                    onStart = { timerState = TimerState.RUNNING },
                    onPause = { timerState = TimerState.PAUSED },
                    onStop = {
                        remainingTime = 0
                        totalTime = 0
                        timerState = TimerState.IDLE
                    },
                    onRestart = {
                        remainingTime = totalTime
                        timerState = TimerState.RUNNING
                    }
                )
            }
        }
    }
}

@Composable
fun TimerDisplay(remainingTime: Long, onClick: () -> Unit) {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime) % 60

    val timeString = String.format("%02d:%02d", minutes, seconds)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
    ) {
        // You could add a CircularProgressIndicator here for a visual countdown
        // CircularProgressIndicator(progress = remainingTime / totalTime.toFloat(), modifier = Modifier.fillMaxSize())

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Remaining Time",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // AnimatedContent provides a nice transition when the time changes
            AnimatedContent(
                targetState = timeString,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn())
                        .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                        .using(SizeTransform(clip = false))
                }, label = "TimerTextAnimation"
            ) { targetText ->
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (remainingTime == 0L) {
                TextButton(onClick = onClick) {
                    Text("Set Time")
                }
            }
        }
    }
}

@Composable
private fun TimerControls(
    timerState: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        // Stop and Restart buttons are only visible when the timer is not idle
        if (timerState != TimerState.IDLE) {
            FilledTonalButton(onClick = onStop) {
                Icon(Icons.Default.Stop, contentDescription = "Stop Timer")
                Spacer(modifier = Modifier.size(40.dp))
                Text("Stop")
            }
            FilledTonalButton(onClick = onRestart) {
                Icon(Icons.Default.Refresh, contentDescription = "Restart Timer")
                Spacer(modifier = Modifier.size(40.dp))
                Text("Restart")
            }
        }

        // The main Start/Pause button
        val remainingTime = 0
        Button(
            onClick = {
                when (timerState) {
                    TimerState.IDLE, TimerState.PAUSED -> onStart()
                    TimerState.RUNNING -> onPause()
                }
            },
            // Disable the button if no time is set
            enabled = timerState != TimerState.IDLE || remainingTime > 0
        ) {
            when (timerState) {
                TimerState.RUNNING -> {
                    Icon(Icons.Default.Pause, contentDescription = "Pause Timer")
                    Spacer(modifier = Modifier.size(40.dp))
                    Text("Pause")
                }
                else -> {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Timer")
                    Spacer(modifier = Modifier.size(40.dp))
                    Text("Start")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(is24Hour = true)

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Time", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
