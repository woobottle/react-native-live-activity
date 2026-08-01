import NativeLiveActivity from './NativeLiveActivity'
import type {
  ActiveLiveActivity,
  LiveActivityContent,
  PlatformCapabilities,
  StartActivityOptions,
  StartLiveActivityResult,
} from './types'

/**
 * Public JS surface for the library. Mirrors PRD §6 candidate API exactly.
 * Each method is a thin pass-through to the native module so the contract
 * remains identical across iOS and Android implementations.
 */
export const LiveActivity = {
  isSupported(): Promise<boolean> {
    return NativeLiveActivity.isSupported()
  },

  getPlatformCapabilities(): Promise<PlatformCapabilities> {
    return NativeLiveActivity.getPlatformCapabilities()
  },

  getActiveActivities(): Promise<ActiveLiveActivity[]> {
    return NativeLiveActivity.getActiveActivities()
  },

  startActivity(
    content: LiveActivityContent,
    options: StartActivityOptions = {},
  ): Promise<StartLiveActivityResult> {
    return NativeLiveActivity.startActivity(content, options)
  },

  updateActivity(activityId: string, content: LiveActivityContent): Promise<void> {
    return NativeLiveActivity.updateActivity(activityId, content)
  },

  endActivity(activityId: string): Promise<void> {
    return NativeLiveActivity.endActivity(activityId)
  },
}

export type LiveActivityModule = typeof LiveActivity
