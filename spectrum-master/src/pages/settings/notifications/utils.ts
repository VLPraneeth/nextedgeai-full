export const notificationConfigTypes = [
  { label: 'Webhook', value: 'webhook' },
  { label: 'Email Group', value: 'email' },
] as const;
export type NotificationTypes = typeof notificationConfigTypes[number]['value'];

export const codeMirrorOptions = {
  matchBrackets: true,
  lineWrapping: true,
  autoCloseBrackets: true,
  mode: 'javascript',
  readOnly: true,
  lineNumbers: true,
};
