package com.wboelens.polarrecorder.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.viewModels.LogViewModel

@Composable
fun LogView(logMessages: List<LogViewModel.LogEntry>) {
  val listState = rememberLazyListState()

  Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
    Text(
        text = "Log Messages:",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground)
    Spacer(modifier = Modifier.height(4.dp))

    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), state = listState) {
      items(logMessages) { message ->
        Text(
            text = message.message,
            style = MaterialTheme.typography.bodySmall,
            color =
                when (message.type) {
                  LogViewModel.LogType.SUCCESS -> MaterialTheme.colorScheme.primary
                  LogViewModel.LogType.NORMAL -> MaterialTheme.colorScheme.onBackground
                  LogViewModel.LogType.ERROR -> MaterialTheme.colorScheme.error
                })
      }
    }
  }

  LaunchedEffect(logMessages.size) {
    if (logMessages.isNotEmpty()) {
      listState.scrollToItem(logMessages.size - 1)
    }
  }
}
