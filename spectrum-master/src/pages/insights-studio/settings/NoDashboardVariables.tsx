import { ReactComponent as DataTrendIcon } from 'assets/icons/data-trend.svg';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';

import './NoDashboardVariables.scss';

export const NoDashboardVariables = withI18n(() => {
  const { tn } = useI18nContext();
  return (
    <EmptyGraphPanel className="no-dashboard-variables" panelIcon={<DataTrendIcon />}>
      {tn('dashboard_no_variables')}
    </EmptyGraphPanel>
  );
}, 'InsightsStudio.Settings');
