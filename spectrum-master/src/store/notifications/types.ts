export enum NotificationTypes {
  ALL = 'ALL',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
  ANNOUNCEMENT = 'ANNOUNCEMENT',
}

export interface Notification {
  id: string;
  subject: string;
  body: string;
  read: boolean;
  type: string;
  createdAt: string;
  archived: boolean;
}

// Heading keys correspond to i18n keys
export type NotificationGroupHeadingKey = 'today' | 'yesterday' | 'this_week' | 'last_week' | 'older';

export type NotificationGroups = Record<NotificationGroupHeadingKey, Notification[]>;
