package com.wboelens.polarrecorder.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.wboelens.polarrecorder.viewModels.LogType
import com.wboelens.polarrecorder.viewModels.LogViewModel

@Composable
fun SnackbarMessageDisplayer(logViewModel: LogViewModel): Pair<SnackbarHostState, LogType?> {
  val snackbarHostState = remember { SnackbarHostState() }
  // Track the current log type
  val currentLogType = remember { mutableStateOf<LogType?>(null) }

  // Collect errors in a side-effect
  LaunchedEffect(logViewModel.snackbarMessagesQueue.hashCode()) {
    logViewModel.snackbarMessagesQueue.collect { _ ->
      while (logViewModel.snackbarMessagesQueue.value.isNotEmpty()) {
        val logEntry = logViewModel.popSnackbarMessage()
        if (logEntry != null) {
          // Store the log type for the current message
          currentLogType.value = logEntry.type
          snackbarHostState.showSnackbar(
              message = logEntry.message,
              duration = SnackbarDuration.Short,
              withDismissAction = true,
          )
        }
      }
    }
  }

  return Pair(snackbarHostState, currentLogType.value)
}

@Composable
fun LogMessageSnackbarHost(
    snackbarHostState: SnackbarHostState,
    logType: LogType? = null,
    modifier: Modifier = Modifier,
) {
  SnackbarHost(hostState = snackbarHostState, modifier = modifier) { data ->
    val containerColor =
        when (logType) {
          LogType.SUCCESS -> MaterialTheme.colorScheme.primary
          LogType.NORMAL -> MaterialTheme.colorScheme.surface
          LogType.ERROR -> MaterialTheme.colorScheme.error
          null -> MaterialTheme.colorScheme.surface // Default case
        }

    val contentColor =
        when (logType) {
          LogType.SUCCESS -> MaterialTheme.colorScheme.onPrimary
          LogType.NORMAL -> MaterialTheme.colorScheme.onSurface
          LogType.ERROR -> MaterialTheme.colorScheme.onError
          null -> MaterialTheme.colorScheme.onSurface // Default case
        }

    Snackbar(snackbarData = data, containerColor = containerColor, contentColor = contentColor)
  }
}
