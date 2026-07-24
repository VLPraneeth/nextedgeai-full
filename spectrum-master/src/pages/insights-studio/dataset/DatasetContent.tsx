//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMemo } from 'react';

import { HStack } from 'components/layout';
import { SkullConfigProvider, useSkullConfig } from 'components/skull';
import { getConfiguration } from 'pages/sync-studio/action-studio/DataSetConfiguration.skull';
import ConfigFooter from 'pages/sync-studio/node-config/ConfigFooter';
import ConfigPage from 'pages/sync-studio/node-config/ConfigPage';
import ConfigSteps from 'pages/sync-studio/node-config/ConfigSteps';
import { useCreateDatasetMutation, useUpdateDatasetMutation } from 'store/insights-studio';
import { Dataset } from 'store/insights-studio/types';

import './DatasetContent.less';

export interface DatasetContentProps {
  dataset?: Dataset | null;
  close?: () => void;
}

const DatasetContent = ({ dataset = null, close }: DatasetContentProps) => {
  const [createDataset] = useCreateDatasetMutation();
  const [updateDataset] = useUpdateDatasetMutation();

  const onCreateDataset = (result: Dataset) => {
    context.onChange({
      name: 'id',
      value: result.id,
    });
  };

  const save = (params: Dataset) => {
    if (params.id) {
      return updateDataset({
        ...dataset,
        ...params,
      });
    }
    return createDataset(params)
      .unwrap()
      .then((result) => onCreateDataset(result))
      .catch((err) => {});
  };

  const config = useMemo(() => getConfiguration(), []);

  const context = useSkullConfig({
    nodeConfig: config,
    configSteps: config.renderer.steps,
    configTitle: config.renderer.title,
    configInputs: config.configuration,
    executeApplyStep: save,
    configValue: {
      // TODO: Fix the underlying skull type
      // to expect the format type
      // @ts-ignore
      dataFormatType: 'form',
      ...dataset,
    },
    close,
    groupConfiguration: {},
  });

  return (
    <SkullConfigProvider value={context}>
      <HStack spacing="z" align="start" className="dataset-wizard-content">
        <ConfigSteps direction="vertical" />
        <div className="dataset-wizard-content__wrapper">
          <div className="dataset-wizard-content__body">
            <ConfigPage />
          </div>
          <div className="synri-drawer-panel__footer">
            <ConfigFooter onClose={close} />
          </div>
        </div>
      </HStack>
    </SkullConfigProvider>
  );
};

export default DatasetContent;
