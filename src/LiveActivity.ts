import NativeLiveActivity from './NativeLiveActivity'
import type { LiveActivityContent, StartLiveActivityResult } from './types'

/**
 * Public JS surface for the library. Mirrors PRD §6 candidate API exactly.
 * Each method is a thin pass-through to the native module so the contract
 * remains identical across iOS and Android implementations.
 */
export const LiveActivity = {
  isSupported(): Promise<boolean> {
    return NativeLiveActivity.isSupported()
  },

  startActivity(content: LiveActivityContent): Promise<StartLiveActivityResult> {
    return NativeLiveActivity.startActivity(content)
  },

  updateActivity(activityId: string, content: LiveActivityContent): Promise<void> {
    return NativeLiveActivity.updateActivity(activityId, content)
  },

  endActivity(activityId: string): Promise<void> {
    return NativeLiveActivity.endActivity(activityId)
  },
}

export type LiveActivityModule = typeof LiveActivity
