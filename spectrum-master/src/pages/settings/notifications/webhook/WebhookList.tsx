import { navigate, RouteComponentProps } from '@reach/router';
import { ColDef, ColGroupDef } from 'ag-grid-community';
import { Button } from 'antd';
import cx from 'classnames';
import { useMemo, useState } from 'react';

import AgTable from 'components/AgTable';
import Can from 'components/Can';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ActionRenderer } from '../ActionRenderer';
import { useErrorNotificationContext } from '../context/ErrorNotificationFormContext';
import { DeleteConfirmationModal } from '../DeleteConfirmationModal';
import { DisabledStatusModal } from '../DisabledStatusModal';
import { NotificationCadenceRenderer } from '../NotificationCadenceRenderer';
import { NotificationStatusRenderer } from '../NotificationStatusRenderer';
import { NotificationTypesRenderer } from '../NotificationTypesRenderer';

interface WebhookListProps extends RouteComponentProps {
  webhooks: ErrorNotificationConfig[] | undefined;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function WebhookList({ webhooks }: WebhookListProps) {
  const [isDeleteConfirmationModalOpen, setIsDeleteConfirmationModalOpen] = useState(false);
  const { setCurrentNotificationConfig } = useErrorNotificationContext();
  const [isDisabledStatusModalOpen, setIsDisabledStatusModalOpen] = useState(false);
  const { userHasPermission } = useUserHasPermission();

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: tc('name'),
        field: 'name',
        resizable: true,
      },
      {
        headerName: tc('url'),
        field: 'configuration.url',
        resizable: true,
        cellRendererFramework: ({ data }: { data: ErrorNotificationConfig | undefined }) => {
          return <span className="ag-cell-value">{data?.configuration?.url}</span>;
        },
        minWidth: 300,
      },
      {
        headerName: tc('notification_type'),
        field: 'notificationTypes',
        resizable: true,
        cellRendererFramework: NotificationTypesRenderer,
      },
      {
        headerName: tc('schedule'),
        field: 'cadence',
        resizable: true,
        cellRendererFramework: NotificationCadenceRenderer,
      },
      {
        headerName: tc('status'),
        field: 'status',
        resizable: true,
        cellRendererFramework: ({ data }: { data: ErrorNotificationConfig | undefined }) => (
          <NotificationStatusRenderer
            status={data?.status}
            handleClick={() => {
              setCurrentNotificationConfig(data);
              setIsDisabledStatusModalOpen(true);
            }}
          />
        ),
      },
      ...(userHasPermission(AllPermissions.WRITE_ERROR_NOTIFICATION_WEBHOOK)
        ? [
            {
              headerName: tc('actions'),
              resizable: true,
              cellRendererFramework: ({ data }: { data: ErrorNotificationConfig | undefined }) => (
                <ActionRenderer
                  data={data}
                  handleDelete={() => {
                    setCurrentNotificationConfig(data);
                    setIsDeleteConfirmationModalOpen(true);
                  }}
                />
              ),
            },
          ]
        : []),
    ];
  }, [setCurrentNotificationConfig, userHasPermission]);

  return (
    <>
      <Can permission={AllPermissions.WRITE_ERROR_NOTIFICATION_WEBHOOK}>
        <Button
          className="error-notifications__add-button"
          onClick={() => {
            navigate(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE_ADD, { type: 'webhook' }));
          }}
          type="primary">
          {tc('add')}
        </Button>
      </Can>
      <AgTable
        className={cx('error-notifications__list', !webhooks?.length && 'empty')}
        domLayout="autoHeight"
        columnDefs={columns}
        rowData={webhooks}
        noRowsOverlayComponentParams={{
          description: tn('no_records_found_webhook'),
        }}
      />
      <DeleteConfirmationModal
        isModalOpen={isDeleteConfirmationModalOpen}
        setIsModalOpen={setIsDeleteConfirmationModalOpen}
      />
      <DisabledStatusModal isModalOpen={isDisabledStatusModalOpen} setIsModalOpen={setIsDisabledStatusModalOpen} />
    </>
  );
}
