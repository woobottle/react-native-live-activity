/**
 * @format
 */

import 'react-native';
import React from 'react';
import App from '../App';

// Note: import explicitly to use the types shipped with jest.
import {it} from '@jest/globals';

declare const jest: {
  mock(moduleName: string, factory: () => unknown): void;
};

jest.mock('react-native-live-activity', () => ({
  LiveActivity: {
    isSupported: () => Promise.resolve(true),
    startActivity: () => Promise.resolve({activityId: 'test-id'}),
    updateActivity: () => Promise.resolve(),
    endActivity: () => Promise.resolve(),
  },
}));

// Note: test renderer must be required after react-native.
import renderer from 'react-test-renderer';

it('renders correctly', () => {
  renderer.create(<App />);
});
