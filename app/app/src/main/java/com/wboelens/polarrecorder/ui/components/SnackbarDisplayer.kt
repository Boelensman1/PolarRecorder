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
import com.wboelens.polarrecorder.viewModels.LogViewModel

@Composable
fun SnackbarMessageDisplayer(
  logViewModel: LogViewModel
): Pair<SnackbarHostState, LogViewModel.LogType?> {
  val snackbarHostState = remember { SnackbarHostState() }
  // Track the current log type
  val currentLogType = remember { mutableStateOf<LogViewModel.LogType?>(null) }

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
  logType: LogViewModel.LogType? = null,
  modifier: Modifier = Modifier
) {
  SnackbarHost(hostState = snackbarHostState, modifier = modifier) { data ->
    val containerColor =
        when (logType) {
          LogViewModel.LogType.SUCCESS -> MaterialTheme.colorScheme.primary
          LogViewModel.LogType.NORMAL -> MaterialTheme.colorScheme.surface
          LogViewModel.LogType.ERROR -> MaterialTheme.colorScheme.error
          null -> MaterialTheme.colorScheme.surface // Default case
        }

    val contentColor =
        when (logType) {
          LogViewModel.LogType.SUCCESS -> MaterialTheme.colorScheme.onPrimary
          LogViewModel.LogType.NORMAL -> MaterialTheme.colorScheme.onSurface
          LogViewModel.LogType.ERROR -> MaterialTheme.colorScheme.onError
          null -> MaterialTheme.colorScheme.onSurface // Default case
        }

    Snackbar(snackbarData = data, containerColor = containerColor, contentColor = contentColor)
  }
}
