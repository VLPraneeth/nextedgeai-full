//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useCallback, useEffect, useMemo, useState } from 'react';

import { ReactComponent as DataTrendIcon } from 'assets/icons/data-trend.svg';
import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage from 'components/InlineMessage';
import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import {
  useGetDashboardQuery,
  useGetDashboardVariableQuery,
  useSetDashboardVariableMutation,
} from 'store/insights-studio';

import { useDashBoardDataCards } from './DashboardVariable.hooks';
import DashboardVariableSettingsForm from './DashboardVariableSettingsForm';
import { useDataCardSettingsContext } from './DataCardSettingsContext';
import { VariableMapping } from './MultiVariableMapping';
import { NoDashboardVariables } from './NoDashboardVariables';

const DashboardVariableSettingsModal = () => {
  const {
    showDashboardVariableSettings,
    dashboardVariableSettingsOptions,
    dashboardVariableSettingsVisible: visible,
  } = useDataCardSettingsContext();
  const dashboardId = dashboardVariableSettingsOptions?.dashboardId;
  const { data: dashboard } = useGetDashboardQuery(dashboardId || '', { skip: !Boolean(dashboardId) });
  const [setDashboardVariable] = useSetDashboardVariableMutation();
  const { data: dashboardVariable } = useGetDashboardVariableQuery(
    { dashboardId: dashboardId || '' },
    { skip: !Boolean(dashboardId) }
  );
  const [errorMessage, setErrorMessage] = useState('');
  const [variableMappings, setVariableMappings] = useState<VariableMapping[]>([{}]);
  const { tn } = useI18nContext();

  const { dashboardVariables } = useDashBoardDataCards();

  useEffect(() => {
    setVariableMappings(dashboardVariable ? dashboardVariable : []);
  }, [dashboardVariable]);

  const onVariableMappingChange = useCallback((varMap: VariableMapping[]) => {
    setVariableMappings(varMap);
  }, []);

  const closeHandler = useCallback(() => showDashboardVariableSettings(false), [showDashboardVariableSettings]);

  const makeVariablesList = useCallback(
    (apiNames: string[]) => {
      return apiNames
        .map((apiName) => {
          if (dashboardVariables[apiName]) {
            return `${dashboardVariables[apiName].displayName} (${apiName})`;
          }
          return null;
        })
        .filter(Boolean)
        .join(', ');
    },
    [dashboardVariables]
  );

  const validate = useCallback(() => {
    const multipleUse: string[] = [];
    const variablesCount = makeVariablesCount(variableMappings);
    Object.keys(variablesCount).forEach((apiName) => {
      if (variablesCount[apiName] > 1) {
        multipleUse.push(apiName);
      }
    });
    if (multipleUse.length) {
      setErrorMessage(tn('cannot_use_multiple_times', { names: makeVariablesList(multipleUse) }));
      return false;
    }

    return true;
  }, [makeVariablesList, tn, variableMappings]);

  const applyHandler = useCallback(() => {
    if (dashboardVariableSettingsOptions?.dashboardId) {
      if (!validate()) {
        return;
      }
      setDashboardVariable({
        dashboardId: dashboardVariableSettingsOptions.dashboardId,
        variableMappings,
      });
      closeHandler();
    }
  }, [closeHandler, dashboardVariableSettingsOptions?.dashboardId, setDashboardVariable, validate, variableMappings]);

  useEffect(() => setErrorMessage(''), [visible]);

  const emptyDashboardVariables = useMemo(() => {
    return !dashboardVariables || (dashboardVariables && Object.keys(dashboardVariables).length <= 0);
  }, [dashboardVariables]);

  const modalContent = useMemo(() => {
    if (emptyDashboardVariables) {
      return <NoDashboardVariables />;
    } else if (variableMappings?.length <= 0) {
      return (
        <EmptyGraphPanel
          actionButtonType="primary"
          className="synri-create-draft-pipeline-panel"
          onActionClick={() => setVariableMappings([{}])}
          panelIcon={<DataTrendIcon style={{ width: 48, height: 48 }} />}
          actionText={tn('add_variable_mapping')}>
          <TranslatedText text="no_variable_mappings" />
        </EmptyGraphPanel>
      );
    } else {
      return (
        <DashboardVariableSettingsForm
          dashboardVariable={dashboardVariables}
          value={variableMappings}
          onChange={onVariableMappingChange}
        />
      );
    }
  }, [dashboardVariables, emptyDashboardVariables, onVariableMappingChange, tn, variableMappings]);

  return (
    <DrawerPanel
      absolutePositioning
      className="insights-studio-settings"
      footer={
        <HStack justify="end">
          {!emptyDashboardVariables ? (
            <HStack>
              <Button onClick={closeHandler}>
                <TranslatedText namespace="Common" text="cancel" />
              </Button>
              <Button type="primary" onClick={applyHandler}>
                <TranslatedText namespace="Common" text="apply" />
              </Button>
            </HStack>
          ) : (
            <Button type="primary" onClick={closeHandler}>
              <TranslatedText namespace="Common" text="close" />
            </Button>
          )}
        </HStack>
      }
      mask
      maskClosable={false}
      onClose={closeHandler}
      title={tn('variable_settings', { name: dashboard?.displayName })}
      visible={visible}
      width="large">
      <InlineMessage type="error" title={errorMessage}>
        {errorMessage}
      </InlineMessage>
      {modalContent}
    </DrawerPanel>
  );
};

export default withI18n(DashboardVariableSettingsModal, 'InsightsStudio.Settings');

export const makeVariablesCount = (variableMappings: VariableMapping[]) => {
  const variableCount: Record<string, number> = {};
  variableMappings.forEach((variableMapping) => {
    if (!variableMapping?.apiName) {
      return;
    }
    const apiName = variableMapping.apiName;
    variableCount[apiName] = (variableCount[apiName] ?? 0) + 1;
    variableMapping.mappedApiNames?.forEach((mappedApiName) => {
      variableCount[mappedApiName] = (variableCount[mappedApiName] ?? 0) + 1;
    });
  });
  return variableCount;
};
