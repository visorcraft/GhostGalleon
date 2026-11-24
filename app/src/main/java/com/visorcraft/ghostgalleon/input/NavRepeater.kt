package com.visorcraft.ghostgalleon.input

import android.os.Handler
import com.visorcraft.ghostgalleon.settings.Action

// Hold-to-repeat engine for NAV actions. onPress fires one move immediately
// and schedules repeats (first after HoldRepeat.INITIAL_DELAY_MS, then one
// per HoldRepeat.REPEAT_INTERVAL_MS); onRelease cancels them. Directions
// track independently — holding LEFT+UP repeats both.
//
// The Scheduler seam keeps this host-testable without Robolectric: Android
// code plugs in a Handler-backed scheduler, tests use a fake clock.
class NavRepeater(
    private val scheduler: Scheduler,
    private val move: (Action) -> Unit,
) {
    interface Scheduler {
        fun postDelayed(task: Runnable, delayMs: Long)
        fun cancel(task: Runnable)
    }

    class HandlerScheduler(private val handler: Handler) : Scheduler {
        override fun postDelayed(task: Runnable, delayMs: Long) {
            handler.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) {
            handler.removeCallbacks(task)
        }
    }

    private val repeatTasks = mutableMapOf<Action, Runnable>()
    private val streaks = mutableMapOf<Action, Int>()

    /**
     * Consecutive hold-repeat count for the active dispatch (`0` on fresh press,
     * `1, 2, 3...` on consecutive scheduled repeats; resets to `0` on release,
     * [cancelAll], or pressing a fresh action).
     */
    var repeatStreak: Int = 0
        private set

    /** Live repeat streak for a specific held [action], or `0` if not repeating. */
    fun streakFor(action: Action): Int = streaks[action] ?: 0

    fun onPress(action: Action) {
        cancelTaskOnly(action)
        streaks[action] = 0
        repeatStreak = 0
        move(action)
        scheduleRepeat(action, HoldRepeat.INITIAL_DELAY_MS)
    }

    fun onRelease(action: Action) {
        cancel(action)
        repeatStreak = 0
    }

    fun cancelAll() {
        repeatTasks.keys.toList().forEach(::cancel)
        streaks.clear()
        repeatStreak = 0
    }

    private fun scheduleRepeat(action: Action, delayMs: Long) {
        val task = Runnable {
            repeatTasks.remove(action)
            val nextStreak = (streaks[action] ?: 0) + 1
            streaks[action] = nextStreak
            repeatStreak = nextStreak
            move(action)
            scheduleRepeat(action, HoldRepeat.REPEAT_INTERVAL_MS)
        }
        repeatTasks[action] = task
        scheduler.postDelayed(task, delayMs)
    }

    private fun cancel(action: Action) {
        cancelTaskOnly(action)
        streaks.remove(action)
    }

    private fun cancelTaskOnly(action: Action) {
        repeatTasks.remove(action)?.let { scheduler.cancel(it) }
    }
}
