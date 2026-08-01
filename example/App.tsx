import React, {useState} from 'react';
import {
  Alert,
  PermissionsAndroid,
  Platform,
  Pressable,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  LiveActivity,
  type ActiveLiveActivity,
  type LiveActivityContent,
  type LiveActivityTimer,
} from '@woobottle/react-native-live-activity';

const EXAMPLE_REFERENCE_ID = 'example-timer';
const TEN_MINUTES_MS = 10 * 60 * 1000;

function App(): React.JSX.Element {
  const [activityId, setActivityId] = useState<string | null>(null);
  const [timer, setTimer] = useState<LiveActivityTimer | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [lastResult, setLastResult] = useState('Ready');

  const canUpdate = activityId != null && !isBusy;
  const canEnd = activityId != null && !isBusy;

  function makePayload(nextTimer: LiveActivityTimer): LiveActivityContent {
    return {
      title: '10분 미션',
      subtitle:
        nextTimer.state === 'completed'
          ? '10분 달성!'
          : nextTimer.state === 'paused'
            ? '잠시 멈춤'
            : '집중하는 중',
      timer: nextTimer,
    };
  }

  async function ensureAndroidNotificationPermission(): Promise<boolean> {
    if (Platform.OS !== 'android' || Platform.Version < 33) {
      return true;
    }

    const result = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    );

    return result === PermissionsAndroid.RESULTS.GRANTED;
  }

  async function handleStart() {
    setIsBusy(true);
    try {
      const hasPermission = await ensureAndroidNotificationPermission();
      if (!hasPermission) {
        setLastResult('Notification permission denied');
        return;
      }

      const supported = await LiveActivity.isSupported();
      if (!supported) {
        setLastResult('Live activity is not supported or enabled');
        return;
      }

      const now = Date.now();
      const nextTimer: LiveActivityTimer = {
        startAt: now,
        endAt: now + TEN_MINUTES_MS,
        state: 'running',
      };
      const result = await LiveActivity.startActivity(makePayload(nextTimer), {
        referenceId: EXAMPLE_REFERENCE_ID,
      });
      setActivityId(result.activityId);
      setTimer(nextTimer);
      setLastResult(`Started ${result.activityId}`);
    } catch (error) {
      showError(error);
    } finally {
      setIsBusy(false);
    }
  }

  async function handlePauseOrResume() {
    if (!activityId || !timer || timer.state === 'completed') {
      return;
    }

    setIsBusy(true);
    try {
      const now = Date.now();
      const nextTimer: LiveActivityTimer =
        timer.state === 'paused'
          ? {
              startAt: timer.startAt,
              endAt: timer.endAt + Math.max(0, now - (timer.pauseAt ?? now)),
              state: 'running',
            }
          : {
              ...timer,
              pauseAt: Math.min(now, timer.endAt),
              state: 'paused',
            };

      await LiveActivity.updateActivity(activityId, makePayload(nextTimer));
      setTimer(nextTimer);
      setLastResult(
        `${nextTimer.state === 'paused' ? 'Paused' : 'Resumed'} ${activityId}`,
      );
    } catch (error) {
      showError(error);
    } finally {
      setIsBusy(false);
    }
  }

  async function handleComplete() {
    if (!activityId || !timer) {
      return;
    }

    setIsBusy(true);
    try {
      const nextTimer: LiveActivityTimer = {
        ...timer,
        pauseAt: undefined,
        state: 'completed',
      };
      await LiveActivity.updateActivity(activityId, makePayload(nextTimer));
      setTimer(nextTimer);
      setLastResult(`Completed ${activityId}`);
    } catch (error) {
      showError(error);
    } finally {
      setIsBusy(false);
    }
  }

  async function handleQuery() {
    setIsBusy(true);
    try {
      const activities = await LiveActivity.getActiveActivities();
      const match = activities.find(
        candidate => candidate.referenceId === EXAMPLE_REFERENCE_ID,
      );
      restoreActivity(match);
      setLastResult(
        match ? `Recovered ${match.activityId}` : 'No example timer found',
      );
    } catch (error) {
      showError(error);
    } finally {
      setIsBusy(false);
    }
  }

  function restoreActivity(activity: ActiveLiveActivity | undefined) {
    if (!activity) {
      return;
    }
    setActivityId(activity.activityId);
    setTimer(activity.content.timer ?? null);
  }

  async function handleEnd() {
    if (!activityId) {
      return;
    }

    setIsBusy(true);
    try {
      await LiveActivity.endActivity(activityId);
      setLastResult(`Ended ${activityId}`);
      setActivityId(null);
      setTimer(null);
    } catch (error) {
      showError(error);
    } finally {
      setIsBusy(false);
    }
  }

  function showError(error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    setLastResult(message);
    Alert.alert('Live Activity error', message);
  }

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="dark-content" backgroundColor={styles.screen.backgroundColor} />
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.title}>Live Activity Example</Text>
          <Text style={styles.subtitle}>
            Start, update, and end the native live surface from JavaScript.
          </Text>
        </View>

        <View style={styles.statusPanel}>
          <Text style={styles.label}>Activity ID</Text>
          <Text style={styles.value}>{activityId ?? 'None'}</Text>
          <Text style={styles.timerState}>
            {timer?.state ?? 'No active timer'}
          </Text>
          <Text style={styles.result}>{lastResult}</Text>
        </View>

        <View style={styles.actions}>
          <ActionButton
            testID="start-activity"
            label={activityId ? 'Start another' : 'Start'}
            disabled={isBusy}
            onPress={handleStart}
          />
          <ActionButton
            testID="pause-resume-activity"
            label={timer?.state === 'paused' ? 'Resume' : 'Pause'}
            disabled={!canUpdate || timer?.state === 'completed'}
            onPress={handlePauseOrResume}
            secondary
          />
          <ActionButton
            testID="complete-activity"
            label="Complete"
            disabled={!canUpdate || timer?.state === 'completed'}
            onPress={handleComplete}
            secondary
          />
          <ActionButton
            testID="query-activities"
            label="Query active"
            disabled={isBusy}
            onPress={handleQuery}
            secondary
          />
          <ActionButton
            testID="end-activity"
            label="End"
            disabled={!canEnd}
            onPress={handleEnd}
            destructive
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

type ActionButtonProps = {
  testID?: string;
  label: string;
  disabled?: boolean;
  secondary?: boolean;
  destructive?: boolean;
  onPress(): void;
};

function ActionButton({
  testID,
  label,
  disabled = false,
  secondary = false,
  destructive = false,
  onPress,
}: ActionButtonProps): React.JSX.Element {
  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.button,
        secondary && styles.secondaryButton,
        destructive && styles.destructiveButton,
        disabled && styles.disabledButton,
      ]}>
      <Text
        style={[
          styles.buttonText,
          secondary && styles.secondaryButtonText,
          destructive && styles.destructiveButtonText,
          disabled && styles.disabledButtonText,
        ]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#f6f7f9',
  },
  container: {
    flex: 1,
    padding: 24,
    gap: 24,
  },
  header: {
    gap: 8,
  },
  title: {
    color: '#17181c',
    fontSize: 30,
    fontWeight: '700',
  },
  subtitle: {
    color: '#5b6170',
    fontSize: 16,
    lineHeight: 22,
  },
  statusPanel: {
    backgroundColor: '#ffffff',
    borderColor: '#dde1e8',
    borderRadius: 8,
    borderWidth: 1,
    gap: 12,
    padding: 16,
  },
  label: {
    color: '#747b89',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0,
    textTransform: 'uppercase',
  },
  value: {
    color: '#252933',
    fontFamily: Platform.select({ios: 'Menlo', android: 'monospace'}),
    fontSize: 13,
  },
  timerState: {
    color: '#2476d4',
    fontSize: 18,
    fontWeight: '700',
    textTransform: 'capitalize',
  },
  result: {
    color: '#3e4450',
    fontSize: 14,
    lineHeight: 20,
  },
  actions: {
    gap: 12,
  },
  button: {
    alignItems: 'center',
    backgroundColor: '#17181c',
    borderRadius: 8,
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: 18,
  },
  secondaryButton: {
    backgroundColor: '#ffffff',
    borderColor: '#c7ccd6',
    borderWidth: 1,
  },
  destructiveButton: {
    backgroundColor: '#fff4f2',
    borderColor: '#e3aaa1',
    borderWidth: 1,
  },
  disabledButton: {
    opacity: 0.45,
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  secondaryButtonText: {
    color: '#252933',
  },
  destructiveButtonText: {
    color: '#a53224',
  },
  disabledButtonText: {
    color: '#6d7480',
  },
});

export default App;
