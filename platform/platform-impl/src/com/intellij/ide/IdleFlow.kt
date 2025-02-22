// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Service(Service.Level.APP)
class IdleFlow(private val coroutineScope: CoroutineScope) {
  private val listenerToRequest = Collections.synchronizedMap(LinkedHashMap<Runnable, CoroutineScope>())

  // must be `replay = 1`, because on a first subscription,
  // the subscriber should start countdown (`debounce()` or `delay()` as part of `collect`)
  private val _events = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_LATEST)

  companion object {
    @JvmStatic
    fun getInstance(): IdleFlow = service<IdleFlow>()
  }

  val events: SharedFlow<Unit> = _events.asSharedFlow()

  init {
    coroutineScope.launch(RawSwingDispatcher) {
      IdeEventQueue.getInstance().setEvents(_events)
    }
  }

  /**
   * Only for existing Java clients.
   * Returns handle to remove the listener.
   */
  @Internal
  @OptIn(FlowPreview::class)
  fun addIdleListener(delayInMs: Int, listener: java.lang.Runnable): () -> Unit {
    val delay = delayInMs.milliseconds
    checkDelay(delay, listener)

    val listenerScope = coroutineScope.childScope()
    listenerScope.launch {
      events
        .debounce(delay)
        .collect {
          withContext(Dispatchers.EDT) {
            listener.run()
          }
        }
    }
    return {
      listenerScope.cancel()
    }
  }

  private fun checkDelay(delay: Duration, listener: Any) {
    if (delay == Duration.ZERO || delay.inWholeHours >= 24) {
      logger<IdleFlow>().error(PluginException.createByClass(IllegalArgumentException("This delay value is unsupported: $delay"),
                                                             listener::class.java))
    }
  }

  /**
   * Use coroutines and [events].
   */
  @OptIn(FlowPreview::class)
  @Deprecated("Use coroutines and [events]. " +
              "Or at least method that returns close handler: `addIdleListener(delayInMs, listener): () -> Unit`")
  fun addIdleListener(runnable: Runnable, timeoutMillis: Int) {
    val delay = timeoutMillis.toDuration(DurationUnit.MILLISECONDS)
    checkDelay(delay, runnable)

    synchronized(listenerToRequest) {
      val listenerScope = coroutineScope.childScope()
      listenerToRequest.put(runnable, listenerScope)
      listenerScope.launch {
        events
          .debounce(delay)
          .collect {
            withContext(Dispatchers.EDT) {
              runnable.run()
            }
          }
      }
    }
  }

  @Deprecated("Use coroutines and [events]. " +
              "Or at least method that returns close handler: `addIdleListener(delayInMs, listener): () -> Unit`")
  fun removeIdleListener(runnable: Runnable) {
    synchronized(listenerToRequest) {
      val coroutineScope = listenerToRequest.remove(runnable)
      if (coroutineScope == null) {
        logger<IdleFlow>().error("unknown runnable: $runnable")
      }
      else {
        coroutineScope.cancel()
      }
    }
  }

  /**
   * Notify the event queue that IDE shouldn't be considered idle at this moment.
   */
  @Internal
  fun restartIdleTimer() {
    check(_events.tryEmit(Unit))
  }
}