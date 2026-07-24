import { Dropdown, Menu, Tooltip } from 'antd';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import { IconButton } from 'components/Button';
import Can from 'components/Can';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useSelectInstanceById } from 'selectors/instanceSelectors';
import {
  deleteInstance,
  Instance,
  InstanceCopyModalType,
  setManageTrialInstanceId,
  showInstanceCopyModal,
  showInstanceEditModal,
} from 'store/instances/slice';
import AppConstants from 'utils/AppConstants';
import CapConstants from 'utils/CapConstants';
import { tc, tCommon, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

const tn = tNamespaced('Settings.Instances');

enum InstanceAction {
  COPY = 'copyInstance',
  EDIT = 'editInstance',
  DELETE = 'deleteInstance',
  MANAGE = 'manage',
}

interface InstanceKebabMenuProps {
  data: Instance;
}

/**
 * Renders a kebab menu for an instance in Ant Table
 * @param {Instance} data instance for the menu, comes from Ant Table component
 */
const InstanceKebabMenu = ({ data }: InstanceKebabMenuProps) => {
  const dispatch = useEnhancedDispatch();

  const instance = useSelectInstanceById(data.syncariId);
  const status = useEnhancedSelector(
    (state) =>
      (instance && state.instance.pendingInstanceUpdates?.[instance.syncariId]) || AppConstants.FETCH_STATUS.IDLE
  );
  const showConfirmationModal = useUserInputConfirmationModal();
  const { userHasPermission } = useUserHasPermission();

  useToastForFetchStatusChange(status, {
    success: tn('deleted_instance'),
    error: tn('error_deleting_instance', { name: instance?.name || '' }),
  });

  // NOTE: not using the Can component here due to some issues with the tooltip
  const deleteButtonByViaPermission = userHasPermission(AllPermissions.DELETE_INSTANCE) ? (
    <Menu.Item key={InstanceAction.DELETE}>{tc('delete')}</Menu.Item>
  ) : (
    <Menu.Item disabled>
      <Tooltip title={tCommon('permission_error')} placement="left">
        {tc('delete')}
      </Tooltip>
    </Menu.Item>
  );

  const deleteButton = deleteButtonByViaPermission;

  if (!instance) {
    return null;
  }

  return (
    <Dropdown
      overlay={
        <Menu
          onClick={({ key }) => {
            switch (key) {
              case InstanceAction.COPY:
                dispatch(
                  showInstanceCopyModal({
                    visible: true,
                    modalType: InstanceCopyModalType.ORG_ONLY,
                    syncariId: instance.syncariId,
                  })
                );
                break;
              case InstanceAction.EDIT:
                dispatch(showInstanceEditModal({ visible: true, instance }));
                break;
              case InstanceAction.DELETE:
                showConfirmationModal({
                  title: tn('delete_instance'),
                  content: tn('delete_instance_content', { name: instance.name, syncariId: instance.syncariId }),
                  onOk: () => dispatch(deleteInstance(instance.syncariId)),
                  okText: tc('delete'),
                  okType: 'danger',
                  okButtonProps: { type: 'danger' },
                });
                break;
              case InstanceAction.MANAGE:
                dispatch(setManageTrialInstanceId(instance.syncariId));
                break;
            }
          }}>
          <Can key={InstanceAction.COPY} capability={[CapConstants.SUPER_ADMIN]}>
            <Menu.Item>{tn('copy_instance')}</Menu.Item>
          </Can>
          <Can key={InstanceAction.EDIT} permission={AllPermissions.EDIT_INSTANCE}>
            <Menu.Item>{tn('edit_instance')}</Menu.Item>
          </Can>
          {deleteButton}
          {instance.type === 'trial' && (
            <Can capability={[CapConstants.SUPER_ADMIN]} key={InstanceAction.MANAGE}>
              <Menu.Item>{tn('manage_trial')}</Menu.Item>
            </Can>
          )}
        </Menu>
      }
      trigger={['click']}>
      <IconButton data-testid={instance.syncariId + '-actions'} icon={() => <KebabIcon />} />
    </Dropdown>
  );
};

export default InstanceKebabMenu;
