package com.wboelens.polarrecorder.recording

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers

/** Interface for RxJava schedulers, enabling testability. */
interface SchedulerProvider {
  fun io(): Scheduler

  fun computation(): Scheduler
}

/** Default implementation that uses the standard RxJava schedulers. */
class RxSchedulerProvider : SchedulerProvider {
  override fun io(): Scheduler = Schedulers.io()

  override fun computation(): Scheduler = Schedulers.computation()
}
