import { Spin, Tag } from 'antd';

import { useGetErrorNotificationTypesQuery } from 'store/error-notifications-v2/api';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';

export function NotificationTypesRenderer({ data }: { data: ErrorNotificationConfig | undefined }) {
  const notificationTypeIds = data?.notificationTypes;
  const { data: notificationTypes, isLoading: notificationTypesIsLoading } = useGetErrorNotificationTypesQuery();

  if (notificationTypesIsLoading) {
    return <Spin />;
  }

  return notificationTypeIds?.map((typeId) => {
    const typeName = notificationTypes?.find((type) => type.id === typeId)?.title;
    return (
      <Tag className="error-notifications__tags" key={typeName}>
        {typeName}
      </Tag>
    );
  });
}
