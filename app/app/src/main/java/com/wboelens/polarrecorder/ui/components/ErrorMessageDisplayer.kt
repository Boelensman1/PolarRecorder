package com.wboelens.polarrecorder.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.wboelens.polarrecorder.viewModels.LogViewModel

@Composable
fun ErrorMessageDisplayer(logViewModel: LogViewModel): SnackbarHostState {
  val snackbarHostState = remember { SnackbarHostState() }

  // Collect errors in a side-effect
  LaunchedEffect(logViewModel.errorMessagesQueue.hashCode()) {
    logViewModel.errorMessagesQueue.collect { _ ->
      while (logViewModel.errorMessagesQueue.value.isNotEmpty()) {
        val error = logViewModel.popErrorMessage()
        if (error != null) {
          snackbarHostState.showSnackbar(
              message = error,
              duration = SnackbarDuration.Short,
              withDismissAction = true,
          )
        }
      }
    }
  }

  return snackbarHostState
}

@Composable
fun ErrorSnackbarHost(snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
  SnackbarHost(hostState = snackbarHostState, modifier = modifier) { data ->
    Snackbar(
        snackbarData = data,
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError)
  }
}
