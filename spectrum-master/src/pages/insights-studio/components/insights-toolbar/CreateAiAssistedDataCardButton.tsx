import { Icon } from 'antd';

import { ReactComponent as RobotIcon } from 'assets/icons/robot.svg';
import Button from 'components/Button';
import Can from 'components/Can';
import { PermissionsComparisonOperator } from 'hooks/useUserHasPermission';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { InsightsDashboard } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import './CreateAiAssistedDataCardButton.scss';

const tn = tNamespaced('InsightsStudio.InsightsGPT');

export const CreateAiAssistedDataCardButton = ({ selectedDashboard }: { selectedDashboard: InsightsDashboard }) => {
  const { navigateTo } = useUnifiedDataCardNavigate();

  return (
    <Can
      permissionOperator={PermissionsComparisonOperator.AND}
      permission={[AllPermissions.CREATE_DATACARD, AllPermissions.UPDATE_DATACARD]}>
      <Button
        className="create-ai-assisted-data-card-button"
        type="primary"
        onClick={() => navigateTo('AIASSISTED', 'datacard')}>
        <Icon component={(props) => <RobotIcon {...props} />} aria-label={tn('title')} role="button" />
        {tn('title')}
      </Button>
    </Can>
  );
};
