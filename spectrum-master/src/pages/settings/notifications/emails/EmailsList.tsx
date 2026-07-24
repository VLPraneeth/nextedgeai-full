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
import { EmailListModal } from './EmailListModal';
import { EmailsRenderer } from './EmailListRenderer';

interface EmailListProps extends RouteComponentProps {
  emails: ErrorNotificationConfig[] | undefined;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function EmailsList({ emails }: EmailListProps) {
  const { currentNotificationConfig, setCurrentNotificationConfig } = useErrorNotificationContext();
  const [isEmailListModalOpen, setIsEmailListModalOpen] = useState(false);
  const [isDeleteConfirmationModalOpen, setIsDeleteConfirmationModalOpen] = useState(false);
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
        headerName: tc('email_list'),
        field: 'configuration.emails',
        resizable: true,
        cellRendererFramework: ({ data }: { data: ErrorNotificationConfig | undefined }) => (
          <EmailsRenderer
            data={data}
            handleClick={() => {
              setCurrentNotificationConfig(data);
              setIsEmailListModalOpen(true);
            }}
          />
        ),
        minWidth: 480,
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
      ...(userHasPermission(AllPermissions.WRITE_ERROR_NOTIFICATION_EMAIL)
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
      <Can permission={AllPermissions.WRITE_ERROR_NOTIFICATION_EMAIL}>
        <Button
          className="error-notifications__add-button"
          onClick={() => {
            navigate(makeUrl(RouteConstants.SETTINGS_NOTIFICATIONS_TYPE_ADD, { type: 'email' }));
          }}
          type="primary">
          {tc('add')}
        </Button>
      </Can>
      <AgTable
        className={cx('error-notifications__list', !emails?.length && 'empty')}
        domLayout="autoHeight"
        columnDefs={columns}
        rowData={emails}
        noRowsOverlayComponentParams={{
          description: tn('no_records_found_email'),
        }}
      />
      <EmailListModal
        currentNotificationConfig={emails?.find((emailConfig) => emailConfig.id === currentNotificationConfig?.id)}
        isModalOpen={isEmailListModalOpen}
        setIsModalOpen={setIsEmailListModalOpen}
      />
      <DeleteConfirmationModal
        isModalOpen={isDeleteConfirmationModalOpen}
        setIsModalOpen={setIsDeleteConfirmationModalOpen}
      />
      <DisabledStatusModal isModalOpen={isDisabledStatusModalOpen} setIsModalOpen={setIsDisabledStatusModalOpen} />
    </>
  );
}
