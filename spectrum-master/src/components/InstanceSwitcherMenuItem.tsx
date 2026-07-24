import { Empty, Popover, Spin, Tooltip } from 'antd';
import Search from 'antd/lib/input/Search';
import cx from 'classnames';
import * as React from 'react';
import { useEffect, useState } from 'react';

import { DropdownDisclosureArrow } from 'components/dropdown-disclosure-arrow/DropdownDisclosureArrow';
import Image from 'components/Image';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { useFocusRef } from 'hooks/useFocusRef';
import useRerenderAfterDelay from 'hooks/useRerenderAfterDelay';
import { InstanceType } from 'store/instances/slice';
import {
  selectCurrentInstance,
  selectFetchingInstances,
  selectOrg,
  useFilteredInstancesByOrg,
} from 'store/user/selectors';
import { getUserInstances } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import ChangeAwareLink from './ChangeAwareLink';
import { Text } from './typography';

import './InstanceSwitcherMenuItem.less';

const ts = tNamespaced('InstanceSwitcher');
const tc = tNamespaced('Common');

const SandboxBadge = () => <div className="instance-sandbox-badge">{ts('sandbox')}</div>;

interface AccountInfoProps {
  orgName: string;
  currentInstanceName: string;
  isSandboxInstance?: boolean;
}

function AccountInfo({ orgName, currentInstanceName, isSandboxInstance }: AccountInfoProps) {
  return (
    <div className="account-info">
      <Image className="account-info-logo" src={'/arcade/api/v1/organization/photo'} alt="Subscription Logo" />
      <div className="account-info-container">
        <span className="org-name">
          <Tooltip title={orgName} mouseEnterDelay={1}>
            {orgName}
          </Tooltip>
        </span>
        <div className="instance-name-container">
          <Tooltip title={currentInstanceName} mouseEnterDelay={1}>
            <span className="instance-name">{currentInstanceName}</span>
          </Tooltip>
          {isSandboxInstance && <SandboxBadge />}
        </div>
      </div>
    </div>
  );
}

interface OrganizationInstancesProps {
  name: string | null;
  id: string;
  index: number;
  children?: React.ReactNode;
}

const OrganizationInstances = ({ name, id, children, index }: OrganizationInstancesProps) => {
  return (
    <ul className={cx('organization-instance-list', index > 0 && 'border')}>
      <li key={id} className="organization-instance-list-title">
        {name}
      </li>
      {children}
    </ul>
  );
};

interface InstanceItemProps {
  instanceName: string;
  instanceId?: string;
  className?: string;
  onClick?: (evt?: React.MouseEvent<HTMLLIElement>) => void;
  isSelected: boolean;
  type?: InstanceType;
}

const InstanceItem = ({
  instanceName,
  instanceId,
  className,
  onClick,
  type = 'production',
  isSelected,
}: InstanceItemProps) => {
  return (
    <ChangeAwareLink to={`/switch-instance/${instanceId}`}>
      <li
        tabIndex={0}
        role="button"
        data-testid={`instance-${instanceId}`}
        onClick={onClick}
        onKeyDown={(e) => {
          if (e.key === AppConstants.KEYBOARD_EVENT_KEYS.enter) {
            onClick?.();
          }
        }}
        className={cx('instance-item', { selected: isSelected }, className)}>
        <div className="instance-item-content">
          <Text className="ellipse-text">{instanceName}</Text>
          {instanceId && (
            <Text size="sm" color="gray-500" className="instance-id">
              ({instanceId})
            </Text>
          )}
          {type === 'sandbox' && <SandboxBadge />}
        </div>
      </li>
    </ChangeAwareLink>
  );
};

function InstanceSwitcher() {
  const [popoverShowing, setPopoverShowing] = useState(false);

  const [filterText, setFilter] = useState('');

  useEffect(() => {
    if (!popoverShowing) {
      setFilter('');
    }
  }, [popoverShowing]);

  const dispatch = useDispatch();
  const org = useSelector(selectOrg);
  const currentInstance = useSelector(selectCurrentInstance);
  const [allInstances, instancesByOrg, filteredInstances] = useFilteredInstancesByOrg(filterText);
  const instancesLoadingStatus = useSelector(selectFetchingInstances);

  const instancesLoading = instancesLoadingStatus === AppConstants.FETCH_STATUS.LOADING;
  const searchRef = useFocusRef({ autoFocus: true });

  const readyToGetInstances = useRerenderAfterDelay(6000);
  const needToGetInstances = !instancesLoading && !allInstances.length;

  useEffect(() => {
    if (readyToGetInstances && needToGetInstances) {
      dispatch(getUserInstances());
    }
  }, [dispatch, needToGetInstances, readyToGetInstances]);

  const toggle = () => {
    if (!popoverShowing && needToGetInstances) {
      dispatch(getUserInstances());
    }
    setPopoverShowing((prev) => !prev);
  };

  return (
    <div className="synri-instance-switcher">
      <Popover
        onVisibleChange={setPopoverShowing}
        trigger="click"
        placement="bottomRight"
        destroyTooltipOnHide
        overlayClassName="synri-instance-list-container"
        visible={popoverShowing}
        content={
          <div data-testid="instance-menu">
            <Search
              ref={searchRef.refCallback as any}
              value={filterText}
              className="instance-filter"
              onKeyDown={(e) => {
                if (e.key === AppConstants.KEYBOARD_EVENT_KEYS.escape) {
                  setPopoverShowing(false);
                }
              }}
              placeholder={tc('filter')}
              onChange={(e) => setFilter(e.target.value)}
            />
            {Object.values(instancesByOrg).map(({ name, id, instances }, index) => (
              <OrganizationInstances key={id} name={name} id={id} index={index}>
                {instances?.map((instance) => (
                  <InstanceItem
                    key={instance.syncariId}
                    instanceId={instance.syncariId}
                    onClick={() => setPopoverShowing(false)}
                    type={instance.type}
                    isSelected={instance.syncariId === currentInstance.id}
                    instanceName={instance.displayName}
                  />
                ))}
              </OrganizationInstances>
            ))}
            {allInstances.length > 0 && filteredInstances.length === 0 && (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tc('no_results')} />
            )}
            {instancesLoading && <Spin spinning tip={ts('loading')} />}
          </div>
        }>
        <div
          onClick={toggle}
          className={cx('instance-switcher-menu-item', {
            'instance-switcher-popover-open': popoverShowing,
          })}>
          <AccountInfo
            orgName={org.name}
            currentInstanceName={currentInstance.name}
            isSandboxInstance={currentInstance.type === 'sandbox'}
          />
          <DropdownDisclosureArrow isOpen={popoverShowing} />
        </div>
      </Popover>
    </div>
  );
}

export default InstanceSwitcher;
