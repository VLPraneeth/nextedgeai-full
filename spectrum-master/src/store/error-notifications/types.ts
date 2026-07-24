import { InputContainerProps } from 'components/inputs/InputContainer';

export interface ErrorCatalogMetadata {
  notificationItems: ErrorNotificationItem[];
  channels: ErrorChannel[];
  frequencies: ErrorNotificationFrequency[];
}

export interface ErrorChannel extends Partial<InputContainerProps> {
  type: string;
  label: string;
  configurationType: string;
  placeholder?: string;
}

export type ErrorNotificationFrequencies = 'HOURLY' | 'DAILY' | 'WEEKLY' | 'MONTHLY';

export interface ErrorNotificationFrequency {
  frequency: ErrorNotificationFrequencies;
  label: string;
}

export interface ErrorNotificationItem {
  id: string;
  category: string;
  title: string;
  helpText: string;
  priority: string;
}

export interface ErrorNotificationPayload {
  subscriptions: ErrorNotificationSubscription[];
  channelConfigurations: ChannelConfiguration[];
}

export interface ErrorNotificationSubscription {
  catalogId: string;
  active: boolean;
  frequency: ErrorNotificationFrequencies;
  channels: string[];
}

export interface ErrorNotificationChannel {
  type: string;
  configuration: ChannelConfiguration;
}

export interface ChannelConfiguration {
  type: string;
  active: boolean;
  configuration: {
    emails?: string[];
  };
}
