import { Redirect, RouteComponentProps, useMatch } from '@reach/router';
import { Button } from 'antd';
import cx from 'classnames';
import { find, sortBy } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Layout, Responsive, WidthProvider } from 'react-grid-layout';
import InlineSVG from 'react-inlinesvg';

import GraphIcon from 'assets/images/thumbs-up-graph.svg';
import { withI18n } from 'components/I18nProvider';
import Select from 'components/inputs/Select';
import { HStack, Stack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import ProgressBar from 'components/ProgressBar';
import RouteSpin from 'components/RouteSpin';
import { Toolbar } from 'components/toolbar';
import { TranslatedText } from 'components/typography';
import { useForbiddenRedirect } from 'hooks/useForbiddenRedirect';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useDfiRulesForEntity, useSelectDfiRulesRecalculatingProgressForEntity } from 'store/data-quality/hooks';
import { useDashboard, useDashboardsList } from 'store/new-dashboard/hooks';
import { moveItem } from 'utils/ArrayUtil';
import { filterObj } from 'utils/Fp';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { isNotNullOrUndefined } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';

import DfiRuleDetailsPanel from './dfi-rules/DfiRuleDetailsPanel';
import DfiRulesPanel from './dfi-rules/DfiRulesPanel';
import { Dashboard as DashboardType } from './types';
import { sortLayoutsAndMakeFullWidth } from './utils';
import WidgetCard from './WidgetCard';

import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import './DataQualityStudioRoot.scss';

const tn = tNamespaced('DataQualityStudio.Root');

const ResponsiveGridLayout = WidthProvider(Responsive);

const defaultBreakpoints = {
  lg: 1000,
  sm: 480,
};

const defaultColumnConfig = {
  lg: 16,
  sm: 8,
};

const defaultMargins: [number, number] = [14, 14];

export const DQS_DASHBOARDS_KEY = 'dqs';
export const DQS_ROOT_DASHBOARD_ID = 'dqsOverview';

export interface DashboardProps {
  dashboard: DashboardType;
  breakpoints?: typeof defaultBreakpoints;
  columns?: typeof defaultColumnConfig;
  margins?: [number, number];
  layout?: any;
}

const Dashboard = ({
  dashboard,
  breakpoints = defaultBreakpoints,
  columns = defaultColumnConfig,
  margins = defaultMargins,
}: DashboardProps) => {
  const [breakpointSize, setBreakpointSize] = useState('fake'); // set a fake breakpoint here so it will definitely get updated later

  // We're going to create out own layout map, assuming that the layout config from the BE is
  // our normal/lg size. From here, we'll create a smaller layout that spans the full width
  // of the screen
  const layouts = useMemo(() => {
    if (!dashboard?.widgets) {
      return Object.fromEntries(Object.keys(columns).map((col) => [col, []]));
    }

    const defaultLayouts: Layout[] = dashboard.widgets.map((widget) => {
      // We can't store 'static' in the backend because it's a java keyword. We need to convert
      // this here from 'resizable' back to static by inverting the value
      return filterObj(isNotNullOrUndefined, {
        ...widget.layout,
        static: !widget.layout.resizable,
        i: widget.id,
      });
    });

    return {
      lg: defaultLayouts,
      sm: sortLayoutsAndMakeFullWidth(defaultLayouts, columns.sm),
    };
  }, [columns, dashboard]);

  const widgets = useMemo(() => {
    return (dashboard?.widgets ?? []).map((data) => {
      // we have to return an HTMLElement here as the item that gets cloned by grid layout.
      // unfortunately this splits our styling into 2 parts....needs exploration
      return (
        <div key={data.id} className="data-quality-studio-grid-item">
          <WidgetCard
            name={data.name}
            dashboardName={dashboard.name}
            title={data.title}
            loadingText={data.loadingText}
          />
        </div>
      );
    });
  }, [dashboard]);

  return (
    <ResponsiveGridLayout
      // this is needed to work around a layout issue with RGL that doesn't always trigger
      // a reflow when the grid size expands
      key={breakpointSize}
      measureBeforeMount
      className={cx('layout', 'data-quality-studio-grid')}
      breakpoints={breakpoints}
      cols={columns}
      layouts={layouts}
      margin={margins}
      onBreakpointChange={setBreakpointSize}>
      {widgets}
    </ResponsiveGridLayout>
  );
};

export interface DashboardWrapperProps {
  id: string;
  setRulesPanelVisible: (visible: boolean) => void;
}

// Handles the error, loading, and empty states for the dashboard
const DashboardWrapper = ({ id, setRulesPanelVisible }: DashboardWrapperProps) => {
  const { dashboard, error, loading: dashboardLoading, status } = useDashboard(id);

  useToastForFetchStatusChange(status, {
    error: tn('dashboard_not_found', { dashboardId: id }),
  });

  const { data: rulesData, loading: rulesLoading } = useDfiRulesForEntity(dashboard?.entityId || '');

  if (error) {
    return <Redirect noThrow to={RouteConstants.DATA_QUALITY_STUDIO_ROOT} />;
  }

  if (dashboardLoading || rulesLoading) {
    return <RouteSpin title={tn('loading_dashboard')} />;
  }

  if (!dashboard || (!rulesData?.lastPublished && id !== DQS_ROOT_DASHBOARD_ID)) {
    return (
      <CenterLayout>
        <Stack className="synri-dqs-dashboard-empty-state-container">
          <InlineSVG src={GraphIcon} />
          <TranslatedText
            color="gray-900"
            size="lg"
            weight="bold"
            text="configure_rules_title"
            args={{ entityName: dashboard?.title }}
          />
          <TranslatedText color="gray-700" size="md" beDangerous text="configure_rules_content" />
          <Button type="primary" onClick={() => setRulesPanelVisible(true)}>
            <TranslatedText text="configure_rules" />
          </Button>
        </Stack>
      </CenterLayout>
    );
  }

  return <Dashboard dashboard={dashboard} />;
};

export interface DataQualityStudioRootProps extends RouteComponentProps {}

const DataQualityStudioRoot = ({ navigate }: DataQualityStudioRootProps) => {
  const dashboardIdMatch = useMatch(`${RouteConstants.DATA_QUALITY_STUDIO_ROOT}/:dashboardId`);
  const dashboardId = dashboardIdMatch?.dashboardId ?? DQS_ROOT_DASHBOARD_ID;

  const Error403 = useForbiddenRedirect({
    studioPermissions: AllPermissions.ANALYTICS,
  });

  const { loading, dashboards } = useDashboardsList(DQS_DASHBOARDS_KEY);

  const [rulesPanelVisible, setRulesPanelVisible] = useState(false);

  const activeDashboard = find(dashboards, { name: dashboardId });

  // If the user navigates to /data-quality-studio/entityName without the
  // _dqsOverview the backend will return a 200 since the entity name is a valid
  // identifier. This hook redirects to the proper dashboard name.
  useEffect(() => {
    const suffixedDashboardName = [dashboardId, DQS_ROOT_DASHBOARD_ID].join('_');
    const suffixedNameMatch = find(dashboards, { name: suffixedDashboardName });
    if (suffixedNameMatch) {
      const url = makeUrl(RouteConstants.DATA_QUALITY_STUDIO_DASHBOARD, { dashboardId: suffixedDashboardName });
      navigate?.(url);
    }
  }, [dashboardId, dashboards, navigate]);

  const dashboardOptions = useMemo(() => {
    // Sort dashboards alphabetically and move the overview to be first
    let sortedDashboards = sortBy(dashboards, 'title');
    const overviewIndex = sortedDashboards.findIndex((item) => item.name === DQS_ROOT_DASHBOARD_ID);
    if (overviewIndex > 0) {
      sortedDashboards = moveItem(sortedDashboards, overviewIndex, 0);
    }

    return sortedDashboards.map((d) => {
      const subtitle = d.entityApiName && d.entityApiName !== DQS_ROOT_DASHBOARD_ID ? `(${d.entityApiName})` : '';
      return { value: d.name, label: `${d.title} ${subtitle}`.trim() };
    });
  }, [dashboards]);

  const { recalculating, progressPercentage } = useSelectDfiRulesRecalculatingProgressForEntity(
    activeDashboard?.entityId || ''
  );

  const close = useCallback(() => setRulesPanelVisible(false), [setRulesPanelVisible]);

  if (loading) {
    return <RouteSpin delayMs={300} title={tn('loading_dashboard')} />;
  }

  return (
    Error403 ?? (
      <>
        <>
          <DfiRulesPanel open={rulesPanelVisible} selectedEntityId={activeDashboard?.entityId} close={close} />
          <DfiRuleDetailsPanel selectedEntityId={activeDashboard?.entityId || ''} />
        </>

        <div className="data-quality-studio">
          <Stack spacing="md">
            <Toolbar
              leftChildren={
                <HStack>
                  <Select
                    className="data-quality-studio__dashboard-selector"
                    placeholder={tn('dashboard_selector_placeholder')}
                    optionData={dashboardOptions}
                    onChange={(name) => {
                      const dashboard = dashboardOptions.find((dashboard) => dashboard.value === name);
                      if (dashboard) {
                        // Use the root URL for the DQS Overview to keep the breadcrumbs clean
                        const url =
                          dashboard.value === DQS_ROOT_DASHBOARD_ID
                            ? RouteConstants.DATA_QUALITY_STUDIO_ROOT
                            : makeUrl(RouteConstants.DATA_QUALITY_STUDIO_DASHBOARD, { dashboardId: dashboard.value });

                        navigate?.(url);
                      }
                    }}
                    defaultValue={dashboardOptions.find((opt) => opt.value === dashboardId)?.value}
                  />
                </HStack>
              }
              children={
                dashboardId !== DQS_ROOT_DASHBOARD_ID && (
                  <>
                    {recalculating && (
                      <Stack className="synri-dfi-progress-container" spacing="xxs">
                        <ProgressBar progress={progressPercentage} />
                        <TranslatedText
                          namespace="DataQualityRules"
                          color="gray-800"
                          weight="semibold"
                          size="sm"
                          text="progress"
                          args={{ percentage: progressPercentage }}
                        />
                      </Stack>
                    )}
                    <Button onClick={() => setRulesPanelVisible(true)}>{tn('manage_rules')}</Button>
                  </>
                )
              }
            />
            <div className="data-quality-studio-main">
              {dashboardId && <DashboardWrapper id={dashboardId} setRulesPanelVisible={setRulesPanelVisible} />}
            </div>
          </Stack>
        </div>
      </>
    )
  );
};

export default withI18n(DataQualityStudioRoot, 'DataQualityRules');
