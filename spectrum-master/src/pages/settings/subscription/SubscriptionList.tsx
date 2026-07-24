//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, RouteComponentProps } from '@reach/router';
import { Button, Dropdown, Icon, Layout, Menu, message, Modal, Tooltip } from 'antd';
import { capitalize, find } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ghostLogin } from 'actions/specterActions';
import { deleteSubscription, getSubscriptions, showSubscriptionModal } from 'actions/subscriptionActions';
import Can from 'components/Can';
import { HStack } from 'components/layout';
import { PlaceholderRenderer } from 'components/renderers/PlaceholderRenderer';
import StatusBadge, { StatusBadgeType } from 'components/StatusBadge';
import Table from 'components/Table';
import { TableSearchFilter } from 'components/TableFilters';
import { Text } from 'components/typography';
import useUserLocalMoment, { ISO_8601 } from 'hooks/moment';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { useWindowTitle } from 'hooks/windowTitle';
import { Instance } from 'store/instances/slice';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { useIsTrialUser } from 'store/user/selector.hooks';
import { selectUserGhosted } from 'store/user/selectors';
import { getUserInstances } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import CapConstants from 'utils/CapConstants';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { match } from 'utils/StringUtil';

import RequestGhostAccess from './RequestGhostAccessModal';
import { RevokeGhostAccessModal } from './RevokeGhostAccessModal';

import './SubscriptionList.less';

const tn = tNamespaced('Settings.Subscriptions');

const { Content } = Layout;

// eslint-disable-next-line no-empty-pattern
const SubscriptionList = ({}: RouteComponentProps) => {
  useWindowTitle(tn('page_title'));
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const fetchingSubscriptions = useSelector((state) => state.subscription.fetchingSubscriptions);
  const subscriptions = useSelector((state) => state.subscription.subscriptions);
  const userInstances = useSelector((state) => state.user.instances);
  const ghostInstances = useSelector((state) => state.user.activeGhostAccessList);
  const isTrialUser = useIsTrialUser();
  const dispatch = useDispatch();
  const [filterText, setFilterText] = useState('');
  const [requestGhostVisible, setRequestGhostVisible] = useState(false);
  const [revokeGhostModalVisible, setRevokeGhostModalVisible] = useState(false);
  const [activeInstance, setActiveInstance] = useState<Instance | null>(null);
  const ghosted = useSelector(selectUserGhosted);
  const isGhostUser = useSelector((state) => state.user.isGhostUser);
  const userMoment = useUserLocalMoment();

  // Only Super Admins and Org Admins allowed
  const { roles } = useUserRolesForCurrentInstance();

  const SUBSCRIPTION_COLUMNS = [
    {
      title: tc('name'),
      dataIndex: 'name',
    },
    {
      title: tc('status'),
      dataIndex: 'status',
      render: (text: any, record: Record<string, any>) => {
        const dateStr = record.deletedAt ? userMoment(record.deletedAt, ISO_8601).format(SHORT_DATE_TIME_FORMAT) : '';
        let type: StatusBadgeType = StatusBadgeType.DEFAULT;

        switch (text) {
          case AppConstants.SUBSCRIPTION_STATUS.ACTIVE:
            type = StatusBadgeType.SUCCESS;
            break;
          case AppConstants.SUBSCRIPTION_STATUS.DELETED:
            type = StatusBadgeType.ERROR;
            break;
        }

        return (
          <div className="subscription-list__status-cell">
            <StatusBadge type={type}>{capitalize(text || tc('inactive'))}</StatusBadge>
            {type === StatusBadgeType.ERROR && (
              <Tooltip
                title={() => (
                  <>
                    <Text>{tn('deleted_by', { deletedBy: record.deletedBy })}</Text>
                    <br />
                    <Text beDangerous>{tn('deleted_at', { deletedAt: dateStr })}</Text>
                  </>
                )}
                mouseEnterDelay={1}>
                <Icon theme="filled" type="question-circle" />
              </Tooltip>
            )}
          </div>
        );
      },
    },
    {
      title: tc('created_by'),
      dataIndex: 'createdBy',
      render: (value: any) => PlaceholderRenderer(value, tc('unknown')),
    },
    {
      title: tc('created_at'),
      dataIndex: 'createdAt',
      render: (value: any) => PlaceholderRenderer(value, tc('unknown'), true),
    },
    {
      title: tn('instance_count'),
      dataIndex: 'instancesCount',
    },
  ];

  useEffect(() => {
    if (roles.superAdmin || ghosted || isGhostUser) {
      dispatch(getSubscriptions());
    } else {
      navigate('/settings');
    }
  }, [dispatch, ghosted, isGhostUser, roles.superAdmin]);

  // Get an updated list of user instances on mount
  useEffect(() => {
    dispatch(getUserInstances());
  }, [dispatch]);

  const onRequestDeleteSubscription = () => {
    if (selectedRowKeys.length !== 1) {
      Modal.error({
        title: tn('select_one_organization_title'),
        content: tn('select_one_organization_content'),
      });
    } else {
      const org = subscriptions.find((sub: any) => sub.id === selectedRowKeys[0]);

      const onOk = () => {
        message.info(tn('deleting_subscription'));

        dispatch(deleteSubscription(org.id)).then((response: any) => {
          if (response.success) {
            message.success(tn('successfully_deleted_subscription'));
            setSelectedRowKeys([]);
          } else {
            message.error(tn('error_deleting_subscription'));
          }
        });
      };

      Modal.confirm({
        title: tn('confirm_delete'),
        content: tn('delete_sub_content', { orgName: org.name }),
        okText: tc('delete'),
        cancelText: tc('cancel'),
        onOk,
      });
    }
  };

  const INSTANCE_COLUMNS = [
    {
      title: tn('instance_display_name'),
      dataIndex: 'displayName',
    },
    {
      title: tn('syncari_id'),
      dataIndex: 'syncariId',
    },
    {
      title: tc('status'),
      dataIndex: 'status',
      render: (text: string, record: Record<string, any>) => {
        const dateStr = record.deletedAt ? userMoment(record.deletedAt, ISO_8601).format(SHORT_DATE_TIME_FORMAT) : '';
        let type: StatusBadgeType = StatusBadgeType.DEFAULT;

        switch (text) {
          case AppConstants.INSTANCE_STATUS.ACTIVE:
            type = StatusBadgeType.SUCCESS;
            break;
          case AppConstants.INSTANCE_STATUS.DELETING:
          case AppConstants.INSTANCE_STATUS.DELETED:
            type = StatusBadgeType.ERROR;
            break;
        }

        return (
          <div className="subscription-list__status-cell">
            <StatusBadge type={type}>{capitalize(text || tc('inactive'))}</StatusBadge>
            {type === StatusBadgeType.ERROR && (
              <Tooltip
                title={() => (
                  <>
                    <Text>{tn('deleted_by', { deletedBy: record.deletedBy })}</Text>
                    <br />
                    <Text beDangerous>{tn('deleted_at', { deletedAt: dateStr })}</Text>
                  </>
                )}
                mouseEnterDelay={1}>
                <Icon theme="filled" type="question-circle" />
              </Tooltip>
            )}
          </div>
        );
      },
    },
    {
      title: tc('created_by'),
      dataIndex: 'createdBy',
      render: (value: any) => PlaceholderRenderer(value, tc('unknown')),
    },
    {
      title: tc('created_at'),
      dataIndex: 'createdAt',
      render: (value: any) => PlaceholderRenderer(value, tc('unknown'), true),
    },
    {
      title: tc('action'),
      render: (instance: Instance) => {
        const hasInstanceAccessAccess = !!find(userInstances, { syncariId: instance.syncariId });
        const hasInstanceGhostAccess = !!find(ghostInstances, (syncariId) => syncariId === instance.syncariId);
        const isDeleting =
          instance.status === AppConstants.INSTANCE_STATUS.DELETED ||
          instance.status === AppConstants.INSTANCE_STATUS.DELETING;

        return (
          <>
            {!(hasInstanceAccessAccess || hasInstanceGhostAccess) && !isDeleting ? (
              <Button
                type="link"
                onClick={() => {
                  setActiveInstance(instance);
                  setRequestGhostVisible(true);
                }}>
                {tn('request_access')}
              </Button>
            ) : (
              <Button
                type="link"
                className="synri-revoke-ghost-access-link"
                disabled={!hasInstanceGhostAccess || isDeleting}
                onClick={() => {
                  setActiveInstance(instance);
                  setRevokeGhostModalVisible(true);
                }}>
                {tn('revoke_access')}
              </Button>
            )}
            <Button
              type="link"
              disabled={!hasInstanceAccessAccess}
              onClick={() => {
                dispatch(ghostLogin(instance.syncariId));
              }}>
              {tn('switch_instance')}
            </Button>
          </>
        );
      },
      align: 'right',
    },
  ];

  const getExpandedRowRender = (record: any) => {
    return <Table columns={INSTANCE_COLUMNS} rowKey="syncariId" dataSource={record.instances} pagination={false} />;
  };

  const actionsMenu = (
    <Menu>
      <Menu.Item key="deleteSubscription" onClick={onRequestDeleteSubscription}>
        {tc('delete')}
      </Menu.Item>
    </Menu>
  );

  const instanceFilter = useCallback(
    (instance: any, filterText: string) => match(filterText, instance?.name) || match(filterText, instance?.syncariId),
    []
  );

  const dataSource = useMemo(() => {
    if (filterText) {
      let hasSubscriptionNameMatch = false;
      const filteredSubs = subscriptions?.filter((sub: any) => {
        if (!match(filterText, sub?.name)) {
          return sub.instances?.filter((instance: any) => instanceFilter(instance, filterText)).length;
        }
        // Return all instances if theres a subscription name match
        hasSubscriptionNameMatch = true;
        return true;
      });
      if (hasSubscriptionNameMatch) {
        // Don't do any further instance filtering when theres a subscription name match
        return filteredSubs;
      } else {
        return filteredSubs.map((subs: any) => ({
          ...subs,
          instances: subs.instances.filter((instance: any) => instanceFilter(instance, filterText)),
        }));
      }
    }
    return subscriptions;
  }, [filterText, instanceFilter, subscriptions]);

  return (
    <>
      <Content>
        <HStack className="synri-subscription-action-toolbar">
          <Can capability={[CapConstants.SUPER_ADMIN]}>
            <Button disabled={isTrialUser} icon="plus" type="primary" onClick={() => dispatch(showSubscriptionModal())}>
              {tn('add_subscription')}
            </Button>
          </Can>
          <Dropdown overlay={actionsMenu} trigger={['click']}>
            <Button>
              {tc('actions')}
              <Icon type="down" />
            </Button>
          </Dropdown>
          <TableSearchFilter onChange={(evt) => setFilterText(evt.target.value)} />
        </HStack>
      </Content>
      <Table
        expandIconColumnIndex={5}
        expandIconAsCell={false}
        columns={SUBSCRIPTION_COLUMNS}
        rowSelection={{
          selectedRowKeys,
          onChange: (selectedRowKeys: any) => setSelectedRowKeys(selectedRowKeys),
        }}
        expandedRowRender={getExpandedRowRender}
        rowKey="id"
        dataSource={dataSource}
        loading={fetchingSubscriptions}
      />
      <RequestGhostAccess visible={requestGhostVisible} setVisible={setRequestGhostVisible} instance={activeInstance} />
      <RevokeGhostAccessModal
        visible={revokeGhostModalVisible}
        setVisible={setRevokeGhostModalVisible}
        instance={activeInstance}
      />
    </>
  );
};

export default SubscriptionList;
