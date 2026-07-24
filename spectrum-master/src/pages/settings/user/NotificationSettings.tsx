import { RouteComponentProps } from '@reach/router';
import { Form, Icon, message, Switch, Tooltip } from 'antd';
import { isEmpty, keyBy, map } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import Button from 'components/Button';
import Checkbox from 'components/Checkbox';
import EmailInput from 'components/EmailInput';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import Select from 'components/inputs/Select';
import { HStack, Spacer, Stack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import Spinner from 'components/Spinner';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import { useGetNotificationsMetadataQuery, useSaveErrorNotificationsMutation } from 'store/error-notifications/api';
import {
  ErrorChannel,
  ErrorNotificationFrequencies,
  ErrorNotificationItem,
  ErrorNotificationSubscription,
} from 'store/error-notifications/types';
import { selectErrorNotificationUserPrefs } from 'store/user/selectors';
import useSetState from 'utils/useSetState';

import './NotificationSettings.less';

interface NotificationRowProps extends ErrorNotificationItem {
  selections: ErrorNotificationSubscription;
  channels: ErrorChannel[];
  frequencyValues: { value: string; label: string }[];
  onChange: (selections: ErrorNotificationSubscription) => void;
}

const NotificationRow = ({
  selections,
  channels,
  id,
  onChange,
  title,
  helpText,
  frequencyValues,
}: NotificationRowProps) => {
  const rowId = `notification-row-switch-${id}`;

  return (
    <tr className="notification-row">
      <td>
        <HStack spacing="xs">
          <Switch
            // For some reason the types for the Switch component doesn't
            // include the 'id' prop even though it is passed to the button
            {...{ id: rowId }}
            className="synri-notification-row-switch"
            checked={selections.active}
            onChange={(checked) => {
              // If no channels are selected when the row is active, automatically select all channels
              if (checked && isEmpty(selections.channels)) {
                const allChannels = channels.map(({ type }) => type);
                onChange({ ...selections, active: checked, channels: allChannels });
              } else {
                onChange({ ...selections, active: checked });
              }
            }}
          />
          <Text as="label" color="gray-750" htmlFor={rowId}>
            {title}
          </Text>
          <Tooltip title={helpText}>
            <Icon className="synri-label-tooltip-icon" type="question-circle" />
          </Tooltip>
        </HStack>
      </td>
      <td className="synri-notification-table-header-full">
        <HStack spacing="xs">
          <Select
            optionData={frequencyValues}
            value={selections.frequency}
            size="small"
            disabled={!selections.active}
            onChange={(frequency: ErrorNotificationFrequencies) => {
              onChange({ ...selections, active: selections.active, frequency });
            }}
          />
        </HStack>
      </td>
      {channels.map((channel) => {
        const currentlySelected = selections.channels.includes(channel.type);
        return (
          <td key={channel.type}>
            <HStack spacing="xs">
              <Checkbox
                checked={currentlySelected}
                disabled={!selections.active}
                onChange={(evt) => {
                  const checked = evt.target.checked;

                  const newChannels = checked
                    ? [...selections.channels, channel.type]
                    : selections.channels.filter((channelKey) => channelKey !== channel.type);

                  onChange({ ...selections, channels: newChannels });
                }}>
                <Text>{channel.label}</Text>
              </Checkbox>
            </HStack>
          </td>
        );
      })}
    </tr>
  );
};

type NotificationSelections = Record<string, ErrorNotificationSubscription>;

export interface NotificationSettingsProps extends RouteComponentProps {
  children?: React.ReactNode;
}

const NotificationSettings = ({ location }: NotificationSettingsProps) => {
  const { tn } = useI18nContext();

  const [formHasChanged, setFormHasChanged] = useState(false);
  const [showValidationMessage, setShowValidationMessage] = useState(false);

  const storedNotificationSelections = useEnhancedSelector(selectErrorNotificationUserPrefs);
  const [notificationSelections, setNotificationSelections] = useSetState<NotificationSelections>(EMPTY_OBJECT);

  useEffect(() => {
    if (storedNotificationSelections?.subscriptions) {
      setNotificationSelections(keyBy(storedNotificationSelections?.subscriptions, 'catalogId'));
    }
  }, [setNotificationSelections, storedNotificationSelections, storedNotificationSelections?.subscriptions]);

  const [channelData, setChannelData] = useSetState<Record<string, { value: string[] }>>(EMPTY_OBJECT);

  useEffect(() => {
    const emails = storedNotificationSelections?.channelConfigurations?.[0]?.configuration?.emails;
    if (emails) {
      setChannelData({ email: { value: emails || EMPTY_ARRAY } });
    }
  }, [setChannelData, storedNotificationSelections, storedNotificationSelections?.channelConfigurations]);

  const { data, isLoading } = useGetNotificationsMetadataQuery();
  const [save] = useSaveErrorNotificationsMutation();

  const frequencyValues = useMemo(() => {
    return (
      data?.frequencies.map((frequency) => {
        return { value: frequency.frequency, label: frequency.label };
      }) || EMPTY_ARRAY
    );
  }, [data?.frequencies]);

  const EMPTY_NOTIFICATION_SELECTION: ErrorNotificationSubscription = useMemo(() => {
    const frequency = data?.frequencies?.[0]?.frequency || 'DAILY';

    return {
      catalogId: '',
      active: false,
      frequency,
      channels: [],
    };
  }, [data?.frequencies]);

  const enableAll = useCallback(() => {
    const allChannels = data?.channels.map(({ type }) => type) || EMPTY_ARRAY;

    const newSelections =
      data?.notificationItems.reduce((prev, { id: key, ...meta }) => {
        const currentNotification = notificationSelections[key];
        if (currentNotification) {
          prev[key] = { ...currentNotification, active: true, channels: allChannels };
        } else {
          prev[key] = {
            catalogId: key,
            active: true,
            frequency: EMPTY_NOTIFICATION_SELECTION.frequency,
            channels: allChannels,
          };
        }
        return prev;
      }, {} as NotificationSelections) || EMPTY_OBJECT;

    setFormHasChanged(true);
    setNotificationSelections(newSelections);
  }, [
    EMPTY_NOTIFICATION_SELECTION.frequency,
    data?.channels,
    data?.notificationItems,
    notificationSelections,
    setNotificationSelections,
  ]);

  const disableAll = useCallback(() => {
    const newSelections = Object.keys(notificationSelections).reduce<NotificationSelections>((prev, key) => {
      prev[key] = { ...notificationSelections[key], active: false };
      return prev;
    }, {});

    setFormHasChanged(true);
    setNotificationSelections(newSelections);
  }, [notificationSelections, setNotificationSelections]);

  const emailFieldIsInvalid = useMemo(() => {
    const hasActiveNotifications = Object.values(notificationSelections).some(
      (notification) => notification.active && notification.channels.length > 0
    );

    const hasEmailSetup = !!channelData?.email?.value?.length;

    return hasActiveNotifications && !hasEmailSetup;
  }, [channelData?.email?.value?.length, notificationSelections]);

  const validate = useCallback(() => {
    let valid = true;

    if (emailFieldIsInvalid) {
      valid = false;
    }

    if (!valid) {
      setShowValidationMessage(true);
    }

    return valid;
  }, [emailFieldIsInvalid]);

  const onSave = useCallback(() => {
    const valid = validate();

    if (valid) {
      save({
        subscriptions: Object.values(notificationSelections),
        channelConfigurations: map(channelData, (channel, key) => {
          return { type: key, active: true, configuration: { emails: channel.value } };
        }),
      })
        .unwrap()
        .then(() => {
          setFormHasChanged(false);
          message.success(tn('notifications_saved_successfully'));
        })
        .catch(() => {
          message.error(tn('notifications_save_error'));
        });
    }
  }, [validate, save, notificationSelections, channelData, tn]);

  const onEmailChange = useCallback(
    (targetValue: string[]) => {
      const value = targetValue
        .map((email) => email.trim())
        .filter((email) => {
          if (!email) {
            return false;
          }
          if (!email.includes('@')) {
            message.error(tn('invalid_email_address', { email }));
            return false;
          }
          return true;
        });

      setFormHasChanged(true);

      setChannelData({ ...channelData, email: { value } });
    },
    [channelData, setChannelData, tn]
  );

  if (isLoading) {
    return (
      <div data-testid="loading-notification-settings">
        <Stack className="synri-notification-settings-container">
          <CenterLayout>
            <Spinner />
          </CenterLayout>
        </Stack>
      </div>
    );
  }

  return (
    <Stack className="synri-notification-settings-container">
      <TranslatedText size="lgr" weight="semibold" text="title" color="gray-800" />
      <HStack align="stretch" justify="space-between">
        <TranslatedText size="md" weight="semibold" text="error_alerts" color="gray-750" />

        <HStack>
          <Button size="small" onClick={enableAll}>
            {tn('enable_all')}
          </Button>
          <Button size="small" onClick={disableAll}>
            {tn('disable_all')}
          </Button>
        </HStack>
      </HStack>
      <table className="notification-settings-table">
        <tbody>
          {data?.notificationItems.map(({ id: key, ...meta }) => {
            const selections = notificationSelections[key] || { ...EMPTY_NOTIFICATION_SELECTION, catalogId: key };

            return (
              <NotificationRow
                {...meta}
                key={key}
                id={key}
                frequencyValues={frequencyValues}
                onChange={(selections) => {
                  setFormHasChanged(true);
                  setNotificationSelections({ [key]: selections });
                }}
                selections={selections}
                channels={data?.channels}
              />
            );
          })}
        </tbody>
      </table>

      <Spacer y="xl" />

      <TranslatedText size="md" weight="semibold" text="notification_channels" color="gray-750" />

      <Form.Item
        validateStatus={showValidationMessage && emailFieldIsInvalid ? 'error' : undefined}
        help={tn('email_notifications_input')}>
        <label className="synri-label">Emails</label>
        <EmailInput value={channelData?.email?.value || EMPTY_ARRAY} onChange={onEmailChange} />
      </Form.Item>

      <Button disabled={!formHasChanged} type="primary" onClick={onSave}>
        {tn('save_changes')}
      </Button>
    </Stack>
  );
};

export default withI18n(NotificationSettings, 'Profile.notifications_settings');
