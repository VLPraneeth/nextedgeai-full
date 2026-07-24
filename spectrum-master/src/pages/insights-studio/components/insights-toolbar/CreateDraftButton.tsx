import { navigate } from '@reach/router';
import { message } from 'antd';

import Button from 'components/Button';
import Can from 'components/Can';
import { useCreateDraftDashboardMutation } from 'store/insights-studio/api';
import { InsightsDashboard } from 'store/insights-studio/types';
import { tc, t } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';

export interface InsightsKebabMenuProps {
  selectedDashboard?: InsightsDashboard;
}
export const CreateDraftButton = ({ selectedDashboard }: InsightsKebabMenuProps) => {
  const [createDraftFrom] = useCreateDraftDashboardMutation();

  const handleCreateDraft = () => {
    if (selectedDashboard) {
      createDraftFrom(selectedDashboard.id).then((resp) => {
        if ('data' in resp) {
          message.success(
            t('InsightsStudio.dashboard_draft_created', {
              dashboard: selectedDashboard.displayName,
              interpolation: { escapeValue: false },
            }),
            1.5
          );
          navigate(RouteConstants.INSIGHTS_STUDIO + '/' + selectedDashboard.id + '/draft');
        }
      });
    }
  };

  return (
    <Can permission={AllPermissions.UPDATE_DASHBOARD}>
      <Button onClick={handleCreateDraft}>{tc('create_draft')}</Button>
    </Can>
  );
};
