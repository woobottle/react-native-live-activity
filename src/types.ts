/**
 * Minimal opinionated content schema for the initial library version.
 * Kept narrow on purpose - PRD §3 explicitly excludes arbitrary dynamic
 * layouts. Future versions may add typed variants per surface family.
 */
export type LiveActivityTimerState = 'running' | 'paused' | 'completed'

export type LiveActivityTimer = {
  startAt: number
  endAt: number
  pauseAt?: number
  state: LiveActivityTimerState
}

export type LiveActivityContent = {
  title: string
  subtitle?: string
  progress?: number
  timer?: LiveActivityTimer
}

export type StartLiveActivityResult = {
  activityId: string
}

/**
 * Optional, platform-scoped knobs for `startActivity`. Kept under per-platform
 * keys so the shared surface stays honest about where a knob actually applies.
 */
export type StartActivityOptions = {
  /**
   * Stable product identifier used to reconnect to a native activity after the
   * JavaScript process restarts.
   */
  referenceId?: string
  android?: {
    /**
     * Run the ongoing notification via a foreground service so long-running
     * activities are less likely to be reclaimed by the system. Requires the
     * `FOREGROUND_SERVICE` permission (declared by the library) plus, on
     * Android 14+, `FOREGROUND_SERVICE_DATA_SYNC`. No-op on iOS.
     */
    foregroundService?: boolean
  }
}

export type ActiveLiveActivity = {
  activityId: string
  referenceId?: string
  content: LiveActivityContent
}

export type PlatformCapabilities = {
  iosLiveActivity: boolean
  androidLiveUpdate: boolean
}
