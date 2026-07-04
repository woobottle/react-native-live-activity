import React, {useMemo, useState} from 'react';
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
import {LiveActivity} from 'react-native-live-activity';

const progressSteps = [0.2, 0.45, 0.7, 1];

function App(): React.JSX.Element {
  const [activityId, setActivityId] = useState<string | null>(null);
  const [progressIndex, setProgressIndex] = useState(0);
  const [isBusy, setIsBusy] = useState(false);
  const [lastResult, setLastResult] = useState('Ready');

  const progress = progressSteps[progressIndex];
  const canUpdate = activityId != null && !isBusy;
  const canEnd = activityId != null && !isBusy;

  const payload = useMemo(
    () => ({
      title: 'Delivery in progress',
      subtitle:
        progress >= 1
          ? 'Arrived'
          : `${Math.round(progress * 100)}% complete`,
      progress,
    }),
    [progress],
  );

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

      const result = await LiveActivity.startActivity(payload);
      setActivityId(result.activityId);
      setLastResult(`Started ${result.activityId}`);
    } catch (error) {
      showError(error);
    } finally {
      setIsBusy(false);
    }
  }

  async function handleUpdate() {
    if (!activityId) {
      return;
    }

    setIsBusy(true);
    try {
      const nextProgressIndex = (progressIndex + 1) % progressSteps.length;
      const nextProgress = progressSteps[nextProgressIndex];

      await LiveActivity.updateActivity(activityId, {
        title: 'Delivery in progress',
        subtitle:
          nextProgress >= 1
            ? 'Arrived'
            : `${Math.round(nextProgress * 100)}% complete`,
        progress: nextProgress,
      });

      setProgressIndex(nextProgressIndex);
      setLastResult(`Updated ${activityId}`);
    } catch (error) {
      showError(error);
    } finally {
      setIsBusy(false);
    }
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
      setProgressIndex(0);
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
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, {width: `${progress * 100}%`}]} />
          </View>
          <Text style={styles.result}>{lastResult}</Text>
        </View>

        <View style={styles.actions}>
          <ActionButton
            label={activityId ? 'Start another' : 'Start'}
            disabled={isBusy}
            onPress={handleStart}
          />
          <ActionButton
            label="Update"
            disabled={!canUpdate}
            onPress={handleUpdate}
            secondary
          />
          <ActionButton
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
  label: string;
  disabled?: boolean;
  secondary?: boolean;
  destructive?: boolean;
  onPress(): void;
};

function ActionButton({
  label,
  disabled = false,
  secondary = false,
  destructive = false,
  onPress,
}: ActionButtonProps): React.JSX.Element {
  return (
    <Pressable
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
  progressTrack: {
    backgroundColor: '#e7eaf0',
    borderRadius: 999,
    height: 10,
    overflow: 'hidden',
  },
  progressFill: {
    backgroundColor: '#2476d4',
    height: '100%',
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
