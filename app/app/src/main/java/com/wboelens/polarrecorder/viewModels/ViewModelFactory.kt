package com.wboelens.polarrecorder.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wboelens.polarrecorder.state.DeviceState
import com.wboelens.polarrecorder.state.LogState

/**
 * Factory for creating ViewModels with Application-scoped state dependencies. This allows
 * ViewModels to delegate to state classes that survive Activity restarts.
 */
class ViewModelFactory(
    private val deviceState: DeviceState,
    private val logState: LogState,
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    return when {
      modelClass.isAssignableFrom(DeviceViewModel::class.java) -> {
        DeviceViewModel(deviceState) as T
      }
      modelClass.isAssignableFrom(LogViewModel::class.java) -> {
        LogViewModel(logState) as T
      }
      else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
  }
}
