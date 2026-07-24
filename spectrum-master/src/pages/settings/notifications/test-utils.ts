export const mockNotificationTypes = [
  {
    id: '0',
    category: 'PIPELINE',
    title: 'Pipeline Errors',
    helpText: 'Errors that prevent pipelines from running successfully',
  },
  {
    id: '1',
    category: 'SYNC',
    title: 'Sync Errors',
    helpText: 'Errors that prevent data sync from running successfully',
  },
  {
    id: '2',
    category: 'SYNAPSE',
    title: 'Synapse Errors',
    helpText: 'Errors that prevent synapses from running successfully',
  },
];

export const mockCadences = [
  { label: 'Real time', frequency: 'IMMEDIATE' },
  { label: 'Hourly', frequency: 'HOURLY' },
  { label: 'Daily', frequency: 'DAILY' },
  { label: 'Weekly', frequency: 'WEEKLY' },
  { label: 'Monthly', frequency: 'MONTHLY' },
];

export const mockWebhookBody = {
  lastNotificationTimestamp: '2023-02-23T08:40:49.165+00:00',
  timestamp: '2023-02-23T08:40:49.165+00:00',
  notificationCount: 2,
  instanceId: 'F5XMSW',
  instanceName: 'Error Notification Test',
  notifications: [
    { timestamp: '2023-02-23T08:40:49.165+00:00', summary: 'message summary 1', message: 'detailed error message 1' },
    { timestamp: '2023-02-23T08:40:49.165+00:00', summary: 'message summary 2', message: 'detailed error message 2' },
  ],
};

export const mockEmailNotificationCreate = {
  type: 'email',
  name: 'Test email group',
  description: 'This is a test email group',
  status: 'Active',
  notificationTypes: ['0', '1'],
  cadence: 'IMMEDIATE',
  configuration: {
    emails: [{ email: 'admin@syncari.com' }],
  },
};

export const mockEmailNotificationUpdate = {
  ...mockEmailNotificationCreate,
  id: '1',
  name: 'Test email group edited',
  description: 'This is a test email group edited',
  status: 'Inactive',
  cadence: 'HOURLY',
};

export const mockWebhookNotificationCreate = {
  type: 'webhook',
  name: ' Sample webhook',
  description: 'This is a test webhook',
  status: 'Active',
  notificationTypes: ['0', '1'],
  cadence: 'IMMEDIATE',
  configuration: {
    headers: {},
    httpMethod: 'POST',
    url: 'http://google.com',
  },
};

export const mockWebhookNotificationUpdate = {
  ...mockWebhookNotificationCreate,
  id: '1',
  name: ' Sample webhook edited',
  description: 'This is a test webhook edited',
  status: 'Inactive',
  cadence: 'HOURLY',
  configuration: {
    ...mockWebhookNotificationCreate.configuration,
    headers: {},
  },
};
