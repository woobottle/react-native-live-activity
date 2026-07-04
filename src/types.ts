/**
 * Minimal opinionated content schema for the initial library version.
 * Kept narrow on purpose - PRD §3 explicitly excludes arbitrary dynamic
 * layouts. Future versions may add typed variants per surface family.
 */
export type LiveActivityContent = {
  title: string
  subtitle?: string
  progress?: number
}

export type StartLiveActivityResult = {
  activityId: string
}

/**
 * Optional, platform-scoped knobs for `startActivity`. Kept under per-platform
 * keys so the shared surface stays honest about where a knob actually applies.
 */
export type StartActivityOptions = {
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

export type PlatformCapabilities = {
  iosLiveActivity: boolean
  androidLiveUpdate: boolean
}
