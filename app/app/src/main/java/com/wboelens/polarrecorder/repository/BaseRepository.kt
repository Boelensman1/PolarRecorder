package com.wboelens.polarrecorder.repository

import androidx.annotation.CallSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class BaseRepository {

  protected val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @CallSuper
  fun cleanup() {
    repositoryScope.cancel()
  }
}
