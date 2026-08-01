/**
 * @format
 */

import 'react-native';
import React from 'react';
import {PermissionsAndroid} from 'react-native';

// Note: import explicitly to use the types shipped with jest.
import {expect, it, jest} from '@jest/globals';

const mockStartActivity = jest.fn(() =>
  Promise.resolve({activityId: 'test-id'}),
);

jest.mock('@woobottle/react-native-live-activity', () => ({
  LiveActivity: {
    isSupported: () => Promise.resolve(true),
    getActiveActivities: () => Promise.resolve([]),
    startActivity: mockStartActivity,
    updateActivity: () => Promise.resolve(),
    endActivity: () => Promise.resolve(),
  },
}));

const App = require('../App').default;

// Note: test renderer must be required after react-native.
import renderer from 'react-test-renderer';

it('renders correctly', () => {
  renderer.create(<App />);
});

it('starts a ten-minute timer activity', async () => {
  jest
    .spyOn(PermissionsAndroid, 'request')
    .mockResolvedValue(PermissionsAndroid.RESULTS.GRANTED);
  const app = renderer.create(<App />);

  await renderer.act(async () => {
    await app.root.findByProps({testID: 'start-activity'}).props.onPress();
  });

  expect(mockStartActivity).toHaveBeenCalledWith(
    expect.objectContaining({
      timer: expect.objectContaining({
        state: 'running',
        startAt: expect.any(Number),
        endAt: expect.any(Number),
      }),
    }),
    {referenceId: 'example-timer'},
  );
});
