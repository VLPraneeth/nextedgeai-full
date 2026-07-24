import { navigate, useMatch } from '@reach/router';
import { message } from 'antd';

import Button from 'components/Button';
import Can from 'components/Can';
import Modal from 'components/Modal';
import { usePublishDraftDashboardMutation } from 'store/insights-studio/api';
import { InsightsDashboard } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';

const tn = tNamespaced('InsightsStudio');

export interface InsightsKebabMenuProps {
  selectedDashboard?: InsightsDashboard;
}
export const PublishDraftButton = ({ selectedDashboard }: InsightsKebabMenuProps) => {
  const urlMatch = useMatch('/insights-studio/:dashboardId/*');
  const [publishDraft] = usePublishDraftDashboardMutation();

  const handlePublish = () => {
    if (selectedDashboard) {
      publishDraft(selectedDashboard.id).then((result) => {
        if ('data' in result) {
          message.success(
            tn('dashboard_published', {
              dashboard: selectedDashboard.displayName,
              interpolation: { escapeValue: false },
            }),
            1.5
          );
          navigate(RouteConstants.INSIGHTS_STUDIO + '/' + urlMatch?.dashboardId);
        }
      });
    }
  };

  const confirmPublish = () => {
    Modal.confirm({
      title: tn('dashboard_publish'),
      content: tn('dashboard_publish_confirm_text'),
      onOk: handlePublish,
      okText: tc('publish'),
    });
  };

  return (
    <Can permission={AllPermissions.PUBLISH_DASHBOARD}>
      <Button onClick={confirmPublish} type="primary">
        {tc('publish')}
      </Button>
    </Can>
  );
};
