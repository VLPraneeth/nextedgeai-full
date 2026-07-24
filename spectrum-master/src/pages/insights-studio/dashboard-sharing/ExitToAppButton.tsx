import { Button } from 'antd';
import { useSelector } from 'react-redux';

import { useEnhancedDispatch } from 'hooks/redux';
import { switchInstance } from 'store/instances/slice';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { selectCurrentInstance, selectUserRoles } from 'store/user/selectors';
import CapConstants from 'utils/CapConstants';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

export function ExitToAppButton({ className }: { className?: string }) {
  const dispatch = useEnhancedDispatch();
  const { roles: adminRoles } = useUserRolesForCurrentInstance();
  const currentInstance = useSelector(selectCurrentInstance);
  const roles = useSelector(selectUserRoles);
  const instancesWithAdditionalRoles = Object.keys(roles).filter((key) => {
    const instanceRoles = roles[key];
    if (
      instanceRoles.length < 1 ||
      (instanceRoles.length === 1 && instanceRoles[0] === CapConstants.DASHBOARD_LIGHT_VIEWER)
    ) {
      return false;
    }
    return true;
  });

  if (
    !instancesWithAdditionalRoles.length &&
    !(adminRoles.admin || adminRoles.instanceAdmin || adminRoles.orgAdmin || adminRoles.superAdmin)
  ) {
    return null;
  }

  const instance = instancesWithAdditionalRoles.length ? instancesWithAdditionalRoles[0] : currentInstance.id;

  return (
    <div className={className}>
      <Button
        type="primary"
        onClick={() => {
          dispatch(switchInstance(instance));
        }}>
        {tn('InsightsSharing.go_back_to_syncari')}
      </Button>
    </div>
  );
}
