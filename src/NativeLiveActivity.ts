import { NativeModules, Platform } from 'react-native'

import type {
  ActiveLiveActivity,
  LiveActivityContent,
  PlatformCapabilities,
  StartActivityOptions,
  StartLiveActivityResult,
} from './types'

export type NativeLiveActivityModule = {
  isSupported(): Promise<boolean>
  getPlatformCapabilities(): Promise<PlatformCapabilities>
  getActiveActivities(): Promise<ActiveLiveActivity[]>
  startActivity(
    content: LiveActivityContent,
    options: StartActivityOptions,
  ): Promise<StartLiveActivityResult>
  updateActivity(activityId: string, content: LiveActivityContent): Promise<void>
  endActivity(activityId: string): Promise<void>
}

const LINKING_ERROR =
  `The package '@woobottle/react-native-live-activity' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n'

const NativeLiveActivity: NativeLiveActivityModule =
  (NativeModules.LiveActivity as NativeLiveActivityModule | undefined) ??
  new Proxy({} as NativeLiveActivityModule, {
    get() {
      throw new Error(LINKING_ERROR)
    },
  })

export default NativeLiveActivity
