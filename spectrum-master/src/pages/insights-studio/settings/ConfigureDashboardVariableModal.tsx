//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useCallback, useEffect, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';
import InlineMessage from 'components/InlineMessage';
import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { useGetDashboardQuery, useSetDasbhardVariablePreferencesMutation } from 'store/insights-studio';
import { updateUserDataCardConfig } from 'store/insights-studio/slice';
import { DashboardVariablePreferences, DataCardSettingsValue, VariableValue } from 'store/insights-studio/types';

import ConfigureDashboardVariableForm from './ConfigureDashboardVariableForm';
import { useDataCardSettingsContext } from './DataCardSettingsContext';

const ConfigureDashboardVariableModal = () => {
  const {
    configureDashboardVariableOptions,
    currentValue,
    showConfigureDashboardVariable,
    dashboardSettingsVisible: visible,
  } = useDataCardSettingsContext();
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();
  const dashboardId = configureDashboardVariableOptions?.dashboardId;
  const { data: dashboard } = useGetDashboardQuery(dashboardId || '', { skip: !Boolean(dashboardId) });
  const [errorMessage, setErrorMessage] = useState('');
  const [setDashboardVariablePref] = useSetDasbhardVariablePreferencesMutation();

  const closeHandler = useCallback(() => showConfigureDashboardVariable(false), [showConfigureDashboardVariable]);

  const applyDataCardConfiguration = useCallback(
    (dashboardId: string, dataCardId: string, configuration: DataCardSettingsValue) => {
      dispatch(
        updateUserDataCardConfig({
          dashboardId,
          dataCardId,
          configuration,
        })
      );
    },
    [dispatch]
  );

  const applyHandler = useCallback(() => {
    if (dashboard) {
      const dataCards = dashboard.dataCards;
      const dashboardId = configureDashboardVariableOptions?.dashboardId;
      // Save the dashboard variable values

      if (dashboardId) {
        // Consolidate the variable values
        const dataCardsVariableMappings: DashboardVariablePreferences = {};
        dataCards?.forEach((dataCard) => {
          const datasetVariables: Record<string, VariableValue> = {};

          if (dataCard.configuration) {
            Object.keys(dataCard.configuration).forEach((apiName) => {
              const variableDefaultValue =
                currentValue[apiName] ||
                dataCard?.configuration?.[apiName] ||
                dataCard?.contents?.configuration?.variablesMap?.[apiName] ||
                {};
              datasetVariables[apiName] = {
                ...variableDefaultValue,
                datatype: variableDefaultValue.datatype || '',
              };
            });

            dataCardsVariableMappings[dataCard.id] = datasetVariables;
          }

          if (dashboardId && Object.keys(datasetVariables).length) {
            applyDataCardConfiguration(dashboardId, dataCard.id, datasetVariables);
          }
        });
        setDashboardVariablePref({ dashboardId, dataCardsVariableMappings });
      }

      closeHandler();
    }
  }, [
    applyDataCardConfiguration,
    closeHandler,
    configureDashboardVariableOptions?.dashboardId,
    currentValue,
    dashboard,
    setDashboardVariablePref,
  ]);

  useEffect(() => setErrorMessage(''), [visible]);

  return (
    <DrawerPanel
      absolutePositioning
      afterVisibleChange={() => {}}
      className="insights-studio-settings"
      footer={
        <HStack justify="end">
          <Button onClick={closeHandler}>
            <TranslatedText namespace="Common" text="cancel" />
          </Button>
          <Button type="primary" onClick={applyHandler}>
            <TranslatedText namespace="Common" text="apply" />
          </Button>
        </HStack>
      }
      mask
      maskClosable={false}
      onClose={closeHandler}
      title={tn('title', { name: dashboard?.displayName })}
      visible={visible}
      width="large">
      <InlineMessage type="error" title={errorMessage}>
        {errorMessage}
      </InlineMessage>

      {visible && <ConfigureDashboardVariableForm />}
    </DrawerPanel>
  );
};

export default withI18n(ConfigureDashboardVariableModal, 'InsightsStudio.Settings');
