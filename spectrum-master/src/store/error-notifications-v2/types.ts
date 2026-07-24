export interface ErrorNotificationConfig {
  cadence?: NotificationCadence;
  configuration?: NotificationWebookConfig & { emails?: NotificationEmailConfig[] };
  description?: string;
  id?: string;
  name?: string;
  notificationTypes?: string[];
  status?: NotificationStatus;
  statusMessage?: string;
  type?: NotificationConfigType;
  firstErrorOccured?: string;
  lastErrorOccured?: string;
  retries?: number;
}

export interface ErrorNotificationType {
  category?: string;
  helpText?: string;
  id?: string;
  title?: string;
}

export interface ErrorNotificationCadence {
  frequency?: NotificationCadence;
  label?: string;
}

export type NotificationCadence = 'IMMEDIATE' | 'HOURLY' | 'DAILY' | 'WEEKLY' | 'MONTHLY';

export type NotificationStatus = 'Active' | 'Inactive' | 'Disabled';

export type NotificationConfigType = 'email' | 'webhook';

export type EmailStatus = 'Pending' | 'Active' | 'OptOut';

export type NotificationEmailConfig = { email?: string; status?: EmailStatus };

export interface NotificationWebookConfig {
  httpMethod?: string;
  url?: string;
  headers?: Record<string, string>;
  body?: string;
}

export interface ErrorNotificationTestBody {
  type?: NotificationConfigType;
  configuration?: NotificationWebookConfig;
}

export interface ErrorNotificationTestResponse {
  request: {
    body?: string;
  };
  response: {
    body?: string;
    statusCodeValue?: string;
    statusCode?: string;
  };
}

export interface ErrorNotificationInvitationQuery {
  encInstanceId: string;
  invitationId: string;
  status: Exclude<EmailStatus, 'Pending'>;
}
