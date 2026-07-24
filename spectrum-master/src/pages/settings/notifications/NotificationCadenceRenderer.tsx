import { Spin } from 'antd';

import { useGetErrorNotificationCadencesQuery } from 'store/error-notifications-v2/api';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';

export function NotificationCadenceRenderer({ data }: { data: ErrorNotificationConfig | undefined }) {
  const { data: cadenceData, isLoading: cadenceIsLoading } = useGetErrorNotificationCadencesQuery();

  if (cadenceIsLoading) {
    return <Spin />;
  }

  return (
    <span className="ag-cell-value">{cadenceData?.find((cadence) => cadence.frequency === data?.cadence)?.label}</span>
  );
}
