//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useCallback, useMemo } from 'react';

import { CustomAction } from 'components/custom-action/types';
import { HStack, Stack } from 'components/layout';
import { FetchDynamicStepsParams, SkullConfigProvider, useSkullConfig } from 'components/skull';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import ConfigFooter from 'pages/sync-studio/node-config/ConfigFooter';
import ConfigPage from 'pages/sync-studio/node-config/ConfigPage';
import ConfigSteps from 'pages/sync-studio/node-config/ConfigSteps';
import { useGetCustomActionListQuery } from 'store/custom-action/api';
import { saveCustomAction } from 'store/custom-action/thunks';
import { RequestException } from 'utils/AjaxUtil';

import { configuration as config } from './CustomAction.skull';
import { makeCustomActionPayload } from './CustomAction.util';

export interface CustomActionContentProps {
  customAction: CustomAction | null;
  close?: () => void;
}

const CustomActionContent = ({ customAction, close }: CustomActionContentProps) => {
  const dispatch = useDispatch();
  const { refetch: refreshcustomActionList } = useGetCustomActionListQuery();

  const save = useCallback(
    async (params: CustomAction) => {
      const result = await dispatch(
        saveCustomAction(
          makeCustomActionPayload(
            {
              ...params,
            },
            customAction?.id
          )
        )
      );
      if (result.meta.requestStatus === 'rejected') {
        throw new RequestException(result.payload.message);
      }

      refreshcustomActionList();
    },
    [customAction?.id, dispatch, refreshcustomActionList]
  );

  // TODO: Save each time the user go to the next page
  const saveNextStep = useCallback((params: FetchDynamicStepsParams) => {}, []);

  const customActionConfig = useMemo(() => {
    const editMode = Boolean(customAction);
    return {
      ...config,
      configuration: editMode
        ? config.configuration.map((config) => {
            return {
              ...config,
              // Disable the apiName on edit mode
              disabled: config.name === 'apiName',
            };
          })
        : config.configuration,
    };
  }, [customAction]);

  const context = useSkullConfig({
    nodeConfig: customActionConfig,
    configSteps: customActionConfig.renderer.steps,
    configTitle: customActionConfig.renderer.title,
    configInputs: customActionConfig.configuration,
    fetchDynamicSteps: saveNextStep,
    executeApplyStep: save,
    configValue: customAction,
    close,
    groupConfiguration: {},
  });

  return (
    <SkullConfigProvider value={context}>
      <Stack spacing="z">
        <HStack spacing="z" align="start" className="synri-quick-start-wizard-container">
          <ConfigSteps direction="vertical" />
          <div className="synri-quick-start-body-wrapper">
            <div className="synri-quick-start-wrapper">
              <ConfigPage className="synri-config-page-quick-start-v2" />
            </div>
            <ConfigFooter onClose={close} />
          </div>
        </HStack>
      </Stack>
    </SkullConfigProvider>
  );
};

export default CustomActionContent;
