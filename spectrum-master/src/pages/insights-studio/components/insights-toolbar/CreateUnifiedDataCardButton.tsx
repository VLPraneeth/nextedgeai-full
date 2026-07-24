import Button from 'components/Button';
import Can from 'components/Can';
import { PermissionsComparisonOperator } from 'hooks/useUserHasPermission';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { InsightsDashboard } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

const tn = tNamespaced('InsightsStudio');

export const CreateUnifiedDataCardButton = ({ selectedDashboard }: { selectedDashboard: InsightsDashboard }) => {
  const { navigateTo } = useUnifiedDataCardNavigate();

  return (
    <Can
      permissionOperator={PermissionsComparisonOperator.AND}
      permission={[AllPermissions.CREATE_DATACARD, AllPermissions.UPDATE_DATACARD]}>
      <Button type="primary" onClick={() => navigateTo('DATACARD', 'new', { dashboardId: selectedDashboard.id })}>
        {tn('add_new_card')}
      </Button>
    </Can>
  );
};
