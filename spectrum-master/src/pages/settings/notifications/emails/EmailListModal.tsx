import { ColDef, ColGroupDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { Button, Icon, Input } from 'antd';
import cx from 'classnames';
import { useMemo, useState } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import DrawerPanel from 'components/DrawerPanel';
import { ErrorNotificationConfig, NotificationEmailConfig } from 'store/error-notifications-v2/types';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { NotificationStatusRenderer } from '../NotificationStatusRenderer';
import { AddEmailBox } from './AddEmailBox';
import { DeleteEmailsModal } from './DeleteEmailsModal';
import { ResendInviteModal } from './ResendInviteModal';

interface EmailListModalProps {
  currentNotificationConfig: ErrorNotificationConfig | undefined;
  isModalOpen: boolean;
  setIsModalOpen: (isModalOpen: boolean) => void;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function EmailListModal({ currentNotificationConfig, isModalOpen, setIsModalOpen }: EmailListModalProps) {
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isResendModalOpen, setIsResendModalOpen] = useState(false);
  const [emailToResendInvite, setEmailToResendInvite] = useState<string>();
  const [showAddEmailBox, setShowAddEmailBox] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    return [
      {
        headerName: '',
        field: 'select',
        minWidth: 48,
        maxWidth: 48,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        suppressMovable: true,
      },
      {
        headerName: tc('email_list'),
        field: 'email',
        resizable: true,
        minWidth: 360,
      },
      {
        headerName: tc('status'),
        resizable: true,
        field: 'status',
        cellRendererFramework: ({ data }: { data: NotificationEmailConfig | undefined }) => {
          return (
            <div className="error-notifications__email-modal__email-status">
              <NotificationStatusRenderer status={data?.status} handleClick={() => {}} />
              {data?.status === 'Pending' && (
                <div
                  className="resend-email-trigger"
                  onClick={() => {
                    setEmailToResendInvite(data?.email);
                    setIsResendModalOpen(true);
                  }}>
                  {tn('resend_email')}
                </div>
              )}
            </div>
          );
        },
      },
    ];
  }, []);

  const [selectedRowsCount, setSelectedRowsCount] = useState(0);
  const [gridApi, setGridApi] = useState<GridApi>();

  const onGridReady = (params: GridReadyEvent) => {
    setGridApi(params.api);
  };

  const onRowSelected = () => {
    gridApi && setSelectedRowsCount(gridApi?.getSelectedRows()?.length);
  };

  const emails = useMemo(() => {
    return (currentNotificationConfig?.configuration?.emails || []).filter(
      (emailConfig) =>
        emailConfig.email?.toLocaleLowerCase()?.includes(searchQuery.toLocaleLowerCase()) ||
        emailConfig.status?.toLocaleLowerCase()?.includes(searchQuery.toLocaleLowerCase())
    );
  }, [currentNotificationConfig, searchQuery]);

  return (
    <DrawerPanel
      absolutePositioning
      onClose={() => setIsModalOpen(false)}
      title={<div className="error-notifications__email-modal__title">{tn('manage_subscribers')}</div>}
      visible={isModalOpen}
      width="xlarge"
      destroyOnClose
      footer={
        <Button onClick={() => setIsModalOpen(false)} type="primary">
          {tc('close')}
        </Button>
      }>
      <div className="error-notifications__email-modal">
        <div className="error-notifications__email-modal__header">
          <Input
            placeholder={tn('search_email_status')}
            prefix={<Icon type="search" />}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />

          <div className="error-notifications__email-modal__header-buttons">
            <Button disabled={!selectedRowsCount} onClick={() => setIsDeleteModalOpen(true)}>
              {tn('delete_selected')}
            </Button>
            <Button
              onClick={() => {
                setShowAddEmailBox(true);
              }}
              className={showAddEmailBox ? 'active' : ''}>
              {tn('add_email')}
            </Button>
          </div>
        </div>
        <AddEmailBox showAddEmailBox={showAddEmailBox} setShowAddEmailBox={setShowAddEmailBox} />
        <AgTable
          className={cx('error-notifications__list', !emails?.length && 'empty')}
          domLayout="autoHeight"
          colResizeDefault="shift"
          sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
          enableCellTextSelection
          columnDefs={columns}
          rowData={emails}
          rowSelection="multiple"
          getRowNodeId={(data) => data?.email}
          suppressCellSelection
          suppressRowClickSelection
          onGridReady={onGridReady}
          onRowSelected={onRowSelected}
          singleClickEdit
        />
        <DeleteEmailsModal
          isModalOpen={isDeleteModalOpen}
          setIsModalOpen={setIsDeleteModalOpen}
          closeDrawerPanel={() => setIsModalOpen(false)}
          emailsToDelete={gridApi?.getSelectedRows()?.map((row) => row.email) || []}
          currentNotificationConfig={currentNotificationConfig}
        />
        <ResendInviteModal
          isModalOpen={isResendModalOpen}
          setIsModalOpen={setIsResendModalOpen}
          email={emailToResendInvite}
        />
      </div>
    </DrawerPanel>
  );
}
