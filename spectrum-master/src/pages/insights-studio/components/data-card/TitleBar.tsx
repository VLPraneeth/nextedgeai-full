//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { navigate } from '@reach/router';
import { Icon, Tooltip } from 'antd';
import { useCallback } from 'react';

import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { useIsTextTruncated } from 'components/typography';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useAddCardToDashboard } from 'pages/insights-studio/utils/dashboardUtils';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { DataCardWithData } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useDataCardSettingsContext } from '../../settings';
import { FullSizeVizButton } from './FullSizeVizButton';

import './TitleBar.scss';

const tn = tNamespaced('InsightsStudio');
export interface TitleBarProps {
  dashboardId?: string;
  dataCard?: DataCardWithData;
  description: string;
  name: string;
  removeFromDashboard?: () => void;
  showConfigButton?: boolean;
  showEditControls?: boolean;
  showAddToDashboard?: boolean;
}

const TitleBar = ({
  dashboardId,
  dataCard,
  description,
  name,
  removeFromDashboard,
  showConfigButton,
  showEditControls = false,
  showAddToDashboard,
}: TitleBarProps) => {
  const { showSettings } = useDataCardSettingsContext();
  const [measuredElement, isTruncated] = useIsTextTruncated<HTMLDivElement>();
  const { aiAssistedMatch, navigateTo, getCurrentDashboard } = useUnifiedDataCardNavigate();
  const { userHasPermission } = useUserHasPermission();
  const addCard = useAddCardToDashboard(aiAssistedMatch?.dashboardId);

  let menuItems = [];

  if (showConfigButton) {
    menuItems.push(
      <MenuItem key="configure" onClick={() => showSettings(true, { dataCard, dashboardId })}>
        {tn('data_card_config_vars')}
      </MenuItem>
    );
  }

  const copyDataCard = useCallback(() => {
    const { dashboardId: currentDashboardId } = getCurrentDashboard();
    if (dataCard && currentDashboardId) {
      navigate(
        makeUrl(RouteConstants.INSIGHTS_STUDIO_DATA_CARD_COPY_ADD, {
          dashboardId: currentDashboardId,
          dataCardId: dataCard.id,
        })
      );
    }
  }, [dataCard, getCurrentDashboard]);

  if (showEditControls && removeFromDashboard && userHasPermission(AllPermissions.UPDATE_DASHBOARD)) {
    menuItems.push(
      <MenuItem key="remove-data-card" onClick={removeFromDashboard}>
        {tn('data_card_remove')}
      </MenuItem>
    );
  }

  if (showEditControls && dataCard?.id && userHasPermission(AllPermissions.UPDATE_DATACARD)) {
    menuItems.push(
      <MenuItem key="edit-data-card" onClick={() => navigateTo('DATACARD', dataCard.id)}>
        {tn('data_card_edit')}
      </MenuItem>
    );
    menuItems.push(
      <MenuItem key="duplicate-data-card" onClick={copyDataCard}>
        {tc('make_copy')}
      </MenuItem>
    );
  }

  if (
    showEditControls &&
    dataCard?.contents?.configuration?.datasetId &&
    userHasPermission(AllPermissions.UPDATE_DATASET)
  ) {
    menuItems.push(
      <MenuItem
        key="edit-data-set"
        onClick={() =>
          dataCard?.contents?.configuration?.datasetId &&
          navigateTo('DATASET', dataCard.contents.configuration.datasetId)
        }>
        {tn('data_set_edit')}
      </MenuItem>
    );
  }
  if (showAddToDashboard) {
    menuItems = [
      <MenuItem
        key="Add to dashboard"
        onClick={() => {
          if (aiAssistedMatch?.dashboardId && dataCard?.id) {
            addCard(dataCard.id);
          }
        }}>
        {tn('InsightsGPT.add_to_dashboard')}
      </MenuItem>,
    ];
  }

  // only show tooltip if text is truncated
  const tooltip = isTruncated ? name : '';

  return (
    <div className="data-card-title-bar">
      <div className="data-card-title-bar__left">
        <h2 className="data-card-title-bar__title" ref={measuredElement}>
          <Tooltip title={tooltip}>{name}</Tooltip>
        </h2>
        {description && (
          <Tooltip title={description}>
            <Icon type="question-circle" theme="filled" data-testid="description-tooltip-icon" />
          </Tooltip>
        )}
      </div>
      <div onMouseDown={(e) => e.stopPropagation()}>
        <FullSizeVizButton dataCard={dataCard} />
      </div>
      <div onMouseDown={(e) => e.stopPropagation()}>
        <KebabMenu
          placement="bottomLeft"
          menuItems={menuItems}
          ariaLabel={tn('data_card_options')}
          buttonTitle={tn('data_card_options')}
        />
      </div>
    </div>
  );
};

export default TitleBar;
