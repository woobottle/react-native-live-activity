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

export type PlatformCapabilities = {
  iosLiveActivity: boolean
  androidLiveUpdate: boolean
}
