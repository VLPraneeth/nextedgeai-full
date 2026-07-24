import { navigate } from '@reach/router';
import { Icon, Tooltip } from 'antd';
import classNames from 'classnames';
import React, { CSSProperties, useState } from 'react';
import { animated, SpringConfig, useTransition } from 'react-spring';

import { ReactComponent as CollapseIcon } from 'assets/syncari-icons/color/collapse.svg';
import Button from 'components/Button';
import Can from 'components/Can';
import { Dropdown, Toolbar } from 'components/toolbar';
import { useInsightsSidebarContext } from 'pages/insights-studio/context/InsightsSidebarContext';
import { useInsightsEnabled } from 'pages/insights-studio/utils';
import { useGetDashboardQuery } from 'store/insights-studio';
import { InsightsDashboard } from 'store/insights-studio/types';
import { FeatureFlagName, isFeatureEnabled } from 'utils/FeatureFlagUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';

import { DashboardFormDrawer } from '../dashboard-form-drawer/DashboardFormDrawer';
import { InsightsKebabMenu } from '../insights-kebab-menu/InsightsKebabMenu';
import { CreateAiAssistedDataCardButton } from './CreateAiAssistedDataCardButton';
import { CreateDraftButton } from './CreateDraftButton';
import { CreateUnifiedDataCardButton } from './CreateUnifiedDataCardButton';
import { InsightsVersionSelector } from './InsightsVersionSelector';
import { PublishDraftButton } from './PublishDraftButton';

import './InsightsToolbar.less';

const tn = tNamespaced('InsightsStudio');

export interface InsightsToolbarProps {
  availableDashboards: InsightsDashboard[];
  selectedDashboard: InsightsDashboard;
}

export const InsightsToolbar = ({ availableDashboards, selectedDashboard }: InsightsToolbarProps) => {
  const isInsightsEnabled = useInsightsEnabled();

  const [dashDrawerOpen, setDashDrawerOpen] = useState(false);
  const [editExisting, setEditExisting] = useState(false);

  const { data: selectedDashboardFromApi } = useGetDashboardQuery(selectedDashboard.id);

  const { toggleSidebar, sidebarOpen } = useInsightsSidebarContext();

  const openCreateDashboard = () => {
    setDashDrawerOpen(true);
    setEditExisting(false);
  };

  const openEditDashboard = () => {
    setDashDrawerOpen(true);
    setEditExisting(true);
  };

  const isDraft = selectedDashboard?.draftStatus === 'NEW';
  const isDraftOfPublished = isDraft && !!selectedDashboard?.parentId;
  const hasDraft = Boolean(selectedDashboard?.draftStatus === 'APPROVED' && selectedDashboard?.draft);

  // If system has data, there will always be more than one dashboard in the list.
  // If no data, only sample dash will be available
  const insightsUsable = isInsightsEnabled && availableDashboards.length > 1;
  const showControls = insightsUsable && !selectedDashboard?.seeded && !selectedDashboard?.isExample;
  const showDraftControls = showControls && isDraft;
  const showPublishedControls = showControls && !isDraft;

  return (
    <>
      <Toolbar
        className={classNames('insights-toolbar', { draft: showDraftControls })}
        leftChildren={
          <div className="insights-toolbar__spaced-container">
            {insightsUsable && (
              <Can permission={AllPermissions.CREATE_DASHBOARD}>
                <Button aria-label={`${tc('create')} ${tc('dashboard')}`} onClick={openCreateDashboard} type="primary">
                  <Icon type="plus" />
                  {tc('new')}
                </Button>
              </Can>
            )}
            {availableDashboards.length > 0 && !!selectedDashboard && (
              <Dropdown
                onChange={(opt) => navigate(RouteConstants.INSIGHTS_STUDIO + '/' + opt.id)}
                options={availableDashboards.map(makeDashOption)}
                selected={makeDashOption(selectedDashboard)}
              />
            )}
            <FadeInOut visible={showControls}>
              <InsightsVersionSelector isDraft={isDraft} hasDraft={hasDraft} isDraftOfPublished={isDraftOfPublished} />
            </FadeInOut>
            <div className="insights-toolbar__button-container">
              {/* Buttons are grouped and absolute-positioned to allow them to fade in/out in same location
    rather than shifting one to the right during the transition */}

              <FadeInOut visible={showPublishedControls && !hasDraft} style={{ position: 'absolute' }}>
                <CreateDraftButton selectedDashboard={selectedDashboard} />
              </FadeInOut>
              <div style={{ position: 'absolute', display: 'flex', gap: '14px' }}>
                <FadeInOut visible={showDraftControls}>
                  <PublishDraftButton selectedDashboard={selectedDashboard} />
                </FadeInOut>

                <FadeInOut visible={showDraftControls}>
                  <CreateUnifiedDataCardButton selectedDashboard={selectedDashboard} />
                </FadeInOut>

                {isFeatureEnabled(FeatureFlagName.INSIGHTS_GPT) && (
                  <FadeInOut visible={showDraftControls}>
                    <CreateAiAssistedDataCardButton selectedDashboard={selectedDashboard} />
                  </FadeInOut>
                )}
              </div>
            </div>
          </div>
        }>
        <div className="insights-toolbar__spaced-container">
          <FadeInOut visible={showDraftControls}>
            <Can permission={AllPermissions.UPDATE_DASHBOARD}>
              <Button aria-label="Settings" onClick={openEditDashboard}>
                Settings
              </Button>
            </Can>
          </FadeInOut>
          {insightsUsable && (
            <>
              <InsightsKebabMenu selectedDashboard={selectedDashboard} availableDashboards={availableDashboards} />
              {isDraft && (
                <Tooltip
                  placement="left"
                  title={sidebarOpen ? tc('panel_hide') : tc('panel_show')}
                  mouseEnterDelay={0.5}>
                  <Button
                    className="toolbar-button toolbar-button--right"
                    onClick={toggleSidebar}
                    aria-label={tn('toggle_sidebar')}>
                    <CollapseIcon className={sidebarOpen ? '' : 'reverse'} />
                  </Button>
                </Tooltip>
              )}
            </>
          )}
        </div>
      </Toolbar>
      <DashboardFormDrawer
        onClose={() => setDashDrawerOpen(false)}
        visible={dashDrawerOpen}
        dashboardToEdit={editExisting && selectedDashboardFromApi}
      />
    </>
  );
};

function makeDashOption(dash: InsightsDashboard) {
  const textTag = dash.draftStatus === 'NEW' ? tc('draft') : dash.isExample ? tc('example') : '';
  return {
    id: dash.id,
    name: dash.displayName,
    textTag,
  };
}

export interface FadeInOutProps {
  config?: SpringConfig;
  delay?: number;
  exitBeforeEnter?: boolean;
  visible: boolean;
  style?: CSSProperties;
  children?: React.ReactNode;
}

export const FadeInOut = ({ children, config, delay, exitBeforeEnter, visible, style }: FadeInOutProps) => {
  const fadeStyles = useTransition(visible, {
    from: { opacity: 0, transform: 'scale(0.75)' },
    enter: { opacity: 1, transform: 'scale(1)' },
    leave: { opacity: 0, transform: 'scale(0.75)' },

    config: { tension: 160, friction: 12, ...config },
    delay,
    exitBeforeEnter,
  });

  return (
    <>
      {React.Children.map(children, (child) => {
        return fadeStyles(
          (transitionStyle, visible) =>
            visible && <animated.div style={{ ...style, ...transitionStyle }}>{child}</animated.div>
        );
      })}
    </>
  );
};
