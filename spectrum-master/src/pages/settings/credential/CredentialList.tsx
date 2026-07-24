//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps } from '@reach/router';
import { Button, Dropdown, Icon, Layout, Menu, Modal } from 'antd';
import { Fragment } from 'react';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import AgTable from 'components/AgTable';
import { IconButton } from 'components/Button';
import { useEnhancedDispatch } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useWindowTitle } from 'hooks/windowTitle';
import { useCredentials, useDeleteCredential } from 'store/credentials/hooks';
import { showCredentialModal } from 'store/credentials/slice';
import { ServiceCredential } from 'store/credentials/types';
import { getCredentialType } from 'store/credentials/utils';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './CredentialList.less';

const tn = tNamespaced('Settings.ServiceCredentials');

const { Content } = Layout;

enum CredentialOptionKeys {
  delete = 'deleteCredential',
  update = 'updateCredential',
}

interface DropdownMenuProps {
  data: ServiceCredential;
}

const DropdownMenu = ({ data: credential }: DropdownMenuProps) => {
  const dispatch = useEnhancedDispatch();
  const { deleteCredential, status, error } = useDeleteCredential(credential.id);

  useToastForFetchStatusChange(status, {
    success: tn('credential_deleted'),
    error,
  });

  const showModal = (credentialId: string) => {
    dispatch(showCredentialModal({ visible: true, credentialId }));
  };

  return (
    <Dropdown
      overlay={
        <Menu
          onClick={({ key }) => {
            switch (key) {
              case CredentialOptionKeys.delete:
                Modal.confirm({
                  title: tn('delete_modal_title'),
                  content: tn('delete_modal_content', { name: credential.name }),
                  onOk: deleteCredential,
                  okText: tc('delete'),
                  okType: 'danger',
                });
                break;
              case CredentialOptionKeys.update:
                showModal(credential.id);
                break;
            }
          }}>
          <Menu.Item key={CredentialOptionKeys.update}>{tc('update')}</Menu.Item>
          <Menu.Item key={CredentialOptionKeys.delete}>{tc('delete')}</Menu.Item>
        </Menu>
      }
      trigger={['click']}>
      <IconButton data-testid={`${credential.name}-menu`} icon={() => <KebabIcon />} />
    </Dropdown>
  );
};

const columns = [
  {
    headerName: 'Name',
    field: 'name',
  },
  {
    headerName: 'Type',
    field: 'type',
    cellRenderer: (params: Record<string, string>) => {
      const credType = getCredentialType(params.value);
      return credType ? tn(credType) : '';
    },
  },
  {
    headerName: 'Actions',
    field: 'actions',
    cellRenderer: 'dropdownMenu',
    // Make the actions column fixed size
    flex: 0,
    width: 100,
  },
];

const defaultColDef = { flex: 1 };

const frameworkComponents = {
  dropdownMenu: DropdownMenu,
};

// eslint-disable-next-line no-empty-pattern
const CredentialList = ({}: RouteComponentProps) => {
  useWindowTitle(tn('page_title'));
  const { loading, credentials } = useCredentials();
  const dispatch = useEnhancedDispatch();

  const showModal = () => {
    dispatch(showCredentialModal({ visible: true }));
  };

  return (
    <Fragment>
      <Content>
        <div className="actions-container">
          <Button type="primary" className="apply-action" onClick={showModal}>
            <Icon type="plus" /> {tn('create_modal_title')}
          </Button>
        </div>
      </Content>
      <div className="credentials-table-container">
        <AgTable
          defaultColDef={defaultColDef}
          domLayout="autoHeight"
          suppressCellSelection
          columnDefs={columns}
          frameworkComponents={frameworkComponents}
          rowData={credentials}
          loading={loading}
        />
      </div>
    </Fragment>
  );
};

export default CredentialList;
