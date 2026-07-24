import { navigate, useMatch } from '@reach/router';
import { message, Modal } from 'antd';
import { useMemo, useState } from 'react';

import Can from 'components/Can';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useInsightsSidebarContext } from 'pages/insights-studio/context/InsightsSidebarContext';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import { useDataCardSettingsContext } from 'pages/insights-studio/settings';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { useDeleteDashboardMutation, useDeleteDraftDashboardMutation } from 'store/insights-studio/api';
import { InsightsDashboard } from 'store/insights-studio/types';
import { navigateTo } from 'utils/AppUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { FadeInOut } from '../insights-toolbar/InsightsToolbar';

const tn = tNamespaced('InsightsStudio');

export interface InsightsKebabMenuProps {
  selectedDashboard?: InsightsDashboard;
  availableDashboards?: InsightsDashboard[];
}

export const InsightsKebabMenu = ({ selectedDashboard, availableDashboards }: InsightsKebabMenuProps) => {
  const urlMatch = useMatch('/insights-studio/:dashboardId/*');
  const { userHasPermission } = useUserHasPermission();
  const { getCurrentDashboard } = useUnifiedDataCardNavigate();

  const [deleteDraft] = useDeleteDraftDashboardMutation();
  const { sidebarOpen, openToTab } = useInsightsSidebarContext();
  const [deleteDashboard] = useDeleteDashboardMutation();
  const [kebabDropdownVisible, setKebabDropdownVisible] = useState(false);
  const { showConfigureDashboardVariable, showDashboardVariableSettings } = useDataCardSettingsContext();
  const { setInsightsView, isThoughtSpotEnabled } = useInsightsViewContext();

  const hasUserCreatedDashboards = useMemo(() => {
    return !!availableDashboards?.filter((ds) => !ds.seeded && !ds.isExample).length;
  }, [availableDashboards]);

  const isDraft = Boolean(selectedDashboard?.draftStatus === 'NEW');

  const handleDiscardDraft = () => {
    if (selectedDashboard) {
      deleteDraft(selectedDashboard.id).then((resp) => {
        if ('data' in resp) {
          message.success(
            tn('dashboard_draft_deleted', {
              dashboard: selectedDashboard.displayName,
              interpolation: { escapeValue: false },
            })
          );
          if (urlMatch && urlMatch?.['*'].includes('draft')) {
            // if draft in url, this is a draft of a published version
            navigateTo(makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD, { dashboardId: urlMatch.dashboardId }));
          } else {
            navigateTo(RouteConstants.INSIGHTS_STUDIO);
          }
        }
      });
    }
  };

  const confirmDiscardDraft = () => {
    Modal.confirm({
      title: tc('delete_draft') + '?',
      content: tn('confirm_delete_draft'),
      onOk: handleDiscardDraft,
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
    });
    setKebabDropdownVisible(false);
  };

  const handleDelete = () => {
    if (selectedDashboard) {
      deleteDashboard(selectedDashboard.id).then((resp) => {
        if ('data' in resp) {
          message.success(
            tn('dashboard_deleted', {
              dashboard: selectedDashboard.displayName,
              interpolation: { escapeValue: false },
            })
          );
          if (selectedDashboard.draft) {
            const redirectUrl = makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD, {
              dashboardId: selectedDashboard.draft.id,
            });
            setTimeout(() => {
              navigateTo(redirectUrl);
            }, 500);
          } else {
            navigateTo(RouteConstants.INSIGHTS_STUDIO);
          }
        }
      });
    }
  };

  const handleEditDashboardVariable = () => {
    setKebabDropdownVisible(false);
    selectedDashboard && showConfigureDashboardVariable(true, { dashboardId: selectedDashboard.id });
  };

  const handleSwitchToNewInsightsView = () => {
    setKebabDropdownVisible(false);
    setInsightsView('thoughtspot');
    navigate(RouteConstants.INSIGHTS_STUDIO);
  };

  const handleVariableSettings = () => {
    setKebabDropdownVisible(false);
    selectedDashboard && showDashboardVariableSettings(true, { dashboardId: selectedDashboard.id });
  };

  const confirmDelete = () => {
    Modal.confirm({
      title:
        tn('dashboard_delete', {
          dashboard: selectedDashboard?.displayName,
          interpolation: { escapeValue: false },
        }) + '?',
      content: tn('dashboard_confirm_delete'),
      onOk: handleDelete,
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
    });
    setKebabDropdownVisible(false);
  };

  const openShareWith = () => {
    const { dashboardId } = getCurrentDashboard();
    navigateTo(
      makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD_SHARE_WITH, {
        dashboardId,
      })
    );
    setKebabDropdownVisible(false);
  };

  const openSharingDetails = () => {
    const { dashboardId } = getCurrentDashboard();
    navigateTo(
      makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD_SHARING_DETAILS, {
        dashboardId,
      })
    );
    setKebabDropdownVisible(false);
  };

  const menuItems = [];

  if (selectedDashboard?.draftStatus === 'NEW') {
    menuItems.push(
      <Can permission={AllPermissions.DELETE_DASHBOARD} key="discard_draft">
        <MenuItem key="discard_draft" onClick={confirmDiscardDraft}>
          {tc('delete_draft')}
        </MenuItem>
      </Can>
    );
  }

  if (selectedDashboard?.draftStatus === 'APPROVED' && !selectedDashboard.isExample && !selectedDashboard.seeded) {
    menuItems.push(
      <Can permission={AllPermissions.SHARE_DASHBOARD} key="share_with">
        <MenuItem key="share_with" onClick={openShareWith}>
          {tn('share_with')}
        </MenuItem>
      </Can>,
      <Can permission={AllPermissions.READ_SHARED_DASHBOARD_DETAILS} key="sharing_details">
        <MenuItem key="sharing_details" onClick={openSharingDetails}>
          {tn('sharing_details')}
        </MenuItem>
      </Can>,
      <Can permission={AllPermissions.DELETE_DASHBOARD} key="delete_dashboard">
        <MenuItem key="delete_dashboard" onClick={confirmDelete}>
          {tn('dashboard_delete')}
        </MenuItem>
      </Can>
    );
  }

  if (!sidebarOpen && isDraft) {
    // Hide this menu option when there insufficient permissions since we are
    // hiding the data card tab as well.
    if (userHasPermission(AllPermissions.VIEW_DATACARD)) {
      menuItems.push(
        <MenuItem key="open_data_cards" onClick={() => openToTab('datacards')}>
          {tn('data_cards')}
        </MenuItem>
      );
    }

    menuItems.push(
      <MenuItem key="open_data_sets" onClick={() => openToTab('datasets')}>
        {tn('datasets')}
      </MenuItem>
    );
  }

  if (isDraft) {
    menuItems.push(
      <MenuItem key="dashboard_variable_settings" onClick={handleVariableSettings}>
        {tn('variable_settings')}
      </MenuItem>
    );
  }

  if (userHasPermission(AllPermissions.VIEW_DATACARD)) {
    menuItems.push(
      <MenuItem key="edit_dashboard_variable" onClick={handleEditDashboardVariable}>
        {tn('dashboard_configure_variables')}
      </MenuItem>
    );
  }

  if (isThoughtSpotEnabled && hasUserCreatedDashboards) {
    menuItems.push(
      <MenuItem key="toggle" onClick={handleSwitchToNewInsightsView}>
        {tn('switch_to_new_insights')}
      </MenuItem>
    );
  }

  return (
    <FadeInOut visible={!!menuItems.length} style={{ marginRight: '14px' }}>
      <KebabMenu
        ariaLabel={tn('dashboard_options')}
        menuItems={menuItems}
        onVisibleChange={(visible) => setKebabDropdownVisible(visible)}
        visible={kebabDropdownVisible}
      />
    </FadeInOut>
  );
};
