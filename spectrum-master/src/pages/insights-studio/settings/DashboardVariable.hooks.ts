import { useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch } from 'hooks/redux';
import { useEnhancedSelector } from 'hooks/redux';
import { useGetDashboardQuery, dashboardEndpoints } from 'store/insights-studio';
import { DashListDataCard, DataCardWithData, DatasetVariable } from 'store/insights-studio/types';
import { makeUserDataCardConfigKey } from 'store/insights-studio/util';

import { isVariableVisible } from './DashboardVariable.utils';
import { useDataCardSettingsContext } from './DataCardSettingsContext';

export const useDashBoardDataCards = () => {
  const {
    dashboardSettingsVisible,
    configureDashboardVariableOptions,
    dashboardVariableSettingsVisible,
    dashboardVariableSettingsOptions,
  } = useDataCardSettingsContext();
  const [dataCardsWithData, setDataCardsWithData] = useState<DataCardWithData[]>([]);
  const dashboardId = configureDashboardVariableOptions?.dashboardId || dashboardVariableSettingsOptions?.dashboardId;
  const { data: dashboard } = useGetDashboardQuery(dashboardId || '', { skip: !Boolean(dashboardId) });
  const dispatch = useEnhancedDispatch();
  const userDataCardConfig = useEnhancedSelector((state) => state.insightsStudio.userDataCardConfig);

  useEffect(() => {
    if (dashboard && (dashboardSettingsVisible || dashboardVariableSettingsVisible)) {
      const dataCards = dashboard.dataCards;
      const processDataCard = async (dataCard: DashListDataCard) => {
        if (!dashboardId) {
          return;
        }

        const userConfig = userDataCardConfig[makeUserDataCardConfigKey(dashboardId, dataCard.id)] || {};
        const dataCardWithPref = (
          await dispatch(
            dashboardEndpoints.getDashDataCardWithConfiguration.initiate(
              { dashboardId, dataCardId: dataCard.id, configuration: userConfig.configuration || {} },
              { forceRefetch: false }
            )
          )
        ).data;
        if (dataCardWithPref) {
          setDataCardsWithData((current) => {
            return [...current, dataCardWithPref];
          });
        }
      };
      dataCards?.forEach((dataCards) => {
        processDataCard(dataCards);
      });
    }
    if (!dashboardSettingsVisible && !dashboardVariableSettingsVisible) {
      setDataCardsWithData([]);
    }
  }, [
    dashboard,
    dashboardId,
    dashboardSettingsVisible,
    dashboardVariableSettingsVisible,
    dispatch,
    userDataCardConfig,
  ]);

  const dashboardVariables = useMemo(() => {
    let variables: Record<string, DatasetVariable> = {};
    dataCardsWithData?.forEach((dataCard) => {
      // Current configuration from the variablesMap
      const variablesMap = dataCard.contents?.configuration.variablesMap;
      if (variablesMap) {
        let visibleVariablesMap: Record<string, DatasetVariable> = {};
        Object.keys(variablesMap).forEach((apiName) => {
          // Check if the variable is visible for the dashboard datacard user
          // configurationMeta will have the list of user editable variables
          if (isVariableVisible(apiName, dataCard.configurationMeta) && dataCard?.configuration?.[apiName]) {
            visibleVariablesMap[apiName] = {
              ...variablesMap[apiName],
              // Get our current configuration value from here
              variableDefaultValue: dataCard.configuration[apiName],
            };
          }
        });
        variables = {
          ...variables,
          ...visibleVariablesMap,
        };
      }
    });
    return variables;
  }, [dataCardsWithData]);

  return { dataCardsWithData, dashboardVariables };
};
