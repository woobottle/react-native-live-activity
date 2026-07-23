/**
 * Unit tests for the react-native-live-activity JS surface.
 *
 * We use the preset-patched react-native (a single instance is guaranteed by
 * the moduleNameMapper in jest.config.js) and inject `NativeModules.LiveActivity`
 * before requiring the library, since the library captures that reference at
 * module-eval time. Each test then asserts the public method is a faithful
 * pass-through to the native module.
 */

declare const jest: any;
declare const describe: (name: string, fn: () => void) => void;
declare const it: (name: string, fn: () => unknown) => void;
declare const expect: any;
declare const beforeEach: (fn: () => void) => void;

import {NativeModules} from 'react-native';

const native = {
  isSupported: jest.fn(),
  getPlatformCapabilities: jest.fn(),
  getActiveActivities: jest.fn(),
  startActivity: jest.fn(),
  updateActivity: jest.fn(),
  endActivity: jest.fn(),
};

// Install before the library captures NativeModules.LiveActivity.
(NativeModules as any).LiveActivity = native;

const {LiveActivity} = require('react-native-live-activity');

describe('LiveActivity JS surface', () => {
  beforeEach(() => {
    Object.values(native).forEach((fn: any) => fn.mockReset());
  });

  it('isSupported passes through to native', async () => {
    native.isSupported.mockResolvedValue(true);
    await expect(LiveActivity.isSupported()).resolves.toBe(true);
    expect(native.isSupported).toHaveBeenCalledTimes(1);
  });

  it('getPlatformCapabilities passes through and returns the capability shape', async () => {
    const caps = {iosLiveActivity: true, androidLiveUpdate: false};
    native.getPlatformCapabilities.mockResolvedValue(caps);
    await expect(LiveActivity.getPlatformCapabilities()).resolves.toEqual(caps);
    expect(native.getPlatformCapabilities).toHaveBeenCalledTimes(1);
  });

  it('startActivity forwards content and resolves the activity id', async () => {
    native.startActivity.mockResolvedValue({activityId: 'abc'});
    const content = {title: 'Pizza', subtitle: '4 min', progress: 0.8};
    await expect(LiveActivity.startActivity(content)).resolves.toEqual({
      activityId: 'abc',
    });
    // Options default to an empty object when omitted.
    expect(native.startActivity).toHaveBeenCalledWith(content, {});
  });

  it('startActivity forwards the foregroundService option', async () => {
    native.startActivity.mockResolvedValue({activityId: 'abc'});
    const content = {title: 'Delivery'};
    const options = {android: {foregroundService: true}};
    await LiveActivity.startActivity(content, options);
    expect(native.startActivity).toHaveBeenCalledWith(content, options);
  });

  it('startActivity forwards timer content and referenceId', async () => {
    native.startActivity.mockResolvedValue({activityId: 'timer-1'});
    const content = {
      title: '독서',
      timer: {
        startAt: 1_721_700_000_000,
        endAt: 1_721_700_600_000,
        state: 'running',
      },
    };
    const options = {referenceId: 'mission-42'};

    await LiveActivity.startActivity(content, options);

    expect(native.startActivity).toHaveBeenCalledWith(content, options);
  });

  it('getActiveActivities returns native activity snapshots', async () => {
    const activities = [
      {
        activityId: 'timer-1',
        referenceId: 'mission-42',
        content: {title: '독서'},
      },
    ];
    native.getActiveActivities.mockResolvedValue(activities);

    await expect(LiveActivity.getActiveActivities()).resolves.toEqual(activities);
    expect(native.getActiveActivities).toHaveBeenCalledTimes(1);
  });

  it('updateActivity forwards id and content', async () => {
    native.updateActivity.mockResolvedValue(undefined);
    const content = {title: 'Pizza', progress: 1};
    await LiveActivity.updateActivity('abc', content);
    expect(native.updateActivity).toHaveBeenCalledWith('abc', content);
  });

  it('endActivity forwards the id', async () => {
    native.endActivity.mockResolvedValue(undefined);
    await LiveActivity.endActivity('abc');
    expect(native.endActivity).toHaveBeenCalledWith('abc');
  });
});
