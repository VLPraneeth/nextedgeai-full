//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useCallback } from 'react';

import { HStack, Stack } from 'components/layout';
import { FetchDynamicStepsParams, SkullConfig, SkullConfigProvider, useSkullConfig } from 'components/skull';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import {
  useGetDynamicStepMutation,
  useGetInstallDynamicStepMutation,
  useGetQuickStartAuthorListQuery,
  useGetQuickStartMarketplaceListQuery,
  useInstallQuickStartMutation,
} from 'store/quick-start/api';
import { saveQuickStart } from 'store/quick-start/thunk';
import { QuickStart, QuickStartMode, SaveQuickStartRejected } from 'store/quick-start/types';
import { RequestException } from 'utils/AjaxUtil';

import ConfigFooter from '../../node-config/ConfigFooter';
import ConfigPage from '../../node-config/ConfigPage';
import ConfigSteps from '../../node-config/ConfigSteps';

export interface QuickStartContentProps {
  close?: () => void;
  quickStart: QuickStart | null;
  mode: QuickStartMode;
  config: SkullConfig;
}

const QuickStartContent = ({ close, config, mode, quickStart }: QuickStartContentProps) => {
  const [fetchDynamicStep] = useGetDynamicStepMutation();
  const [fetchInstallDynamicStep] = useGetInstallDynamicStepMutation();
  const [installQuickStart] = useInstallQuickStartMutation();
  const dispatch = useDispatch();
  const { refetch: refetchMarketplace } = useGetQuickStartMarketplaceListQuery({});
  const { refetch: refetchAuthorList } = useGetQuickStartAuthorListQuery();

  const executeSaveQuickStartAuthor = useCallback(
    async (params: QuickStart) => {
      const result = await dispatch(saveQuickStart(params));

      if (result.meta.requestStatus === 'rejected') {
        throw new RequestException((result.payload as SaveQuickStartRejected)?.message);
      }

      refetchMarketplace();
      refetchAuthorList();
    },
    [dispatch, refetchAuthorList, refetchMarketplace]
  );

  const executeInstallQuickStart = useCallback(
    async (params: QuickStart) => {
      if (quickStart) {
        await installQuickStart({ ...params, quickStartId: quickStart.id }).unwrap();
      }
    },
    [installQuickStart, quickStart]
  );

  const fetchDynamicStepsHandler = useCallback(
    (params: FetchDynamicStepsParams) => {
      params = { ...params, id: quickStart?.id };
      return mode === QuickStartMode.AUTHOR ? fetchDynamicStep(params) : fetchInstallDynamicStep(params);
    },
    [fetchDynamicStep, fetchInstallDynamicStep, mode, quickStart?.id]
  );

  const executeQuickStartHandler = useCallback(
    (params: QuickStart) => {
      if (quickStart?.id) {
        params = { ...params, id: quickStart?.id };
      }
      return mode === QuickStartMode.AUTHOR ? executeSaveQuickStartAuthor(params) : executeInstallQuickStart(params);
    },
    [executeInstallQuickStart, executeSaveQuickStartAuthor, mode, quickStart?.id]
  );

  const context = useSkullConfig<QuickStart>({
    nodeConfig: config,
    configSteps: config.renderer.steps,
    configTitle: config.renderer.title,
    configInputs: config.configuration,
    fetchDynamicSteps: fetchDynamicStepsHandler,
    executeApplyStep: executeQuickStartHandler,
    close,
    configValue: quickStart,
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

export default QuickStartContent;
