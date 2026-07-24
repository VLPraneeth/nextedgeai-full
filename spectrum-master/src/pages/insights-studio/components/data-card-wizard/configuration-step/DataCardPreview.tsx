import { Spin } from 'antd';
import { useEffect, useMemo } from 'react';

import Button from 'components/Button';
import { HStack } from 'components/layout';
import { Vizer } from 'components/vizer/Vizer';
import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { useGetVariableQuery, usePreviewDataCardMutation } from 'store/insights-studio';
import { DataCardVizConfig, Dataset, UnsavedDataCard, VariableValue } from 'store/insights-studio/types';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { DataCardError } from '../../data-card-error/DataCardError';
import TitleBar from '../../data-card/TitleBar';
import { FadeInOut } from '../../insights-toolbar/InsightsToolbar';

import './DataCardPreview.scss';

const tn = tNamespaced('InsightsStudio');

export interface DataCardPreviewProps {
  dataCard: UnsavedDataCard;
  dataset?: Dataset;

  // Attemp to show the preview regardless of the configuration
  showPreview?: boolean;

  // Hide the header of preview page. Hidden by default
  hideHeader?: boolean;
}

export const DataCardPreview = ({ dataCard, dataset, showPreview, hideHeader }: DataCardPreviewProps) => {
  const [previewDataCard, { data: dataCardPreview, isUninitialized, isLoading }] = usePreviewDataCardMutation();
  const {
    displayName,
    description,
    variables: unifiedDatasetVariables,
    dataCardWithNewDataset,
  } = useUnifiedDataCardAuthoring();
  const previewCard = {
    configuration: dataCard.contents?.configuration!,
    data: dataCardPreview?.contents.data!,
  };

  const { preselectedDatasetId } = useDataCardAuthoringContext();
  const { data: existingDatasetVariables } = useGetVariableQuery({ datasetId: preselectedDatasetId });

  const datasetVariables = useMemo(() => {
    return dataCardWithNewDataset ? unifiedDatasetVariables : existingDatasetVariables;
  }, [dataCardWithNewDataset, existingDatasetVariables, unifiedDatasetVariables]);

  const canGetPreview = hasConfiguration(dataCard.contents?.configuration);
  const canViewPreview = canGetPreview && !!previewCard.data;

  useEffect(() => {
    const prev = hasConfiguration(dataCard.contents?.configuration);

    if (showPreview && prev) {
      fetchPreview();
    }
    // Only on load.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const previewVariableValue = useMemo(() => {
    const variableValues: Record<string, VariableValue> = {};
    if (!dataCard.contents?.configuration?.variablesMap) {
      return variableValues;
    }
    Object.values(dataCard.contents.configuration.variablesMap).forEach((variable, idx) => {
      if (variable.apiName) {
        variableValues[variable.apiName] = variable.variableDefaultValue;
      }
    });

    datasetVariables?.forEach((variable) => {
      if (variable.apiName && !variableValues[variable.apiName]) {
        variableValues[variable.apiName] = variable.variableDefaultValue;
      }
    });

    return variableValues;
  }, [dataCard?.contents?.configuration?.variablesMap, datasetVariables]);

  if (!dataCard.contents || !previewCard.configuration) {
    return null;
  }

  const fetchPreview = () => {
    previewDataCard({ datacard: dataCard, dataset: dataset ?? {} });
  };

  return (
    <div className="data-card-preview">
      {hideHeader !== true && (
        <HStack justify="space-between" className="data-card-preview__header">
          <div className="synri-label">{tc('preview')}</div>

          {!isUninitialized && (
            <Button onClick={fetchPreview} disabled={!canGetPreview}>
              {tc('refresh')}
            </Button>
          )}
        </HStack>
      )}
      <Spin spinning={isLoading}>
        <div className="data-card-preview__content">
          {canViewPreview && (
            <div className="data-card-preview__content--wrapper">
              <TitleBar
                name={displayName || ''}
                description={description || ''}
                dataCard={{
                  ...dataCardPreview!,
                  contents: {
                    ...dataCardPreview?.contents!,
                    configuration: dataCard.contents?.configuration!,
                  },
                  displayName: displayName || '',
                }}
                showConfigButton={false}
                showEditControls={false}
              />
              <Vizer
                dataCardContent={previewCard}
                graphHeight={300}
                dataConfiguration={previewVariableValue}
                key={dataCard.contents?.configuration.vizType}
              />
            </div>
          )}

          <FadeInOut
            visible={canGetPreview && isUninitialized}
            config={{ friction: 12, tension: 500, bounce: 0 }}
            exitBeforeEnter>
            <Button onClick={fetchPreview} type="primary">
              {tc('preview')}
            </Button>
          </FadeInOut>

          <FadeInOut visible={!canGetPreview} config={{ friction: 12, tension: 500, bounce: 0 }} exitBeforeEnter>
            <DataCardError error={{ title: tn('incomplete_config_title'), body: tn('incomplete_config_body') }} />
          </FadeInOut>
        </div>
      </Spin>
    </div>
  );
};

export const hasConfiguration = (config?: DataCardVizConfig) => {
  if (!config || !config.vizType) {
    return false;
  }
  if (['BAR', 'LINE', 'COLUMN'].includes(config.vizType)) {
    return Boolean(
      config.xaxis &&
        config.xaxis.column &&
        config.xaxis.name &&
        config.xaxis.displayFormat &&
        config.yaxis &&
        config.yaxis[0] &&
        config.yaxis[0].column &&
        config.yaxis[0].name &&
        config.yaxis[0].displayFormat
    );
  }

  if (['PIE'].includes(config.vizType)) {
    return Boolean(
      config.category &&
        config.category.column &&
        config.category.name &&
        config.category.displayFormat &&
        config.value &&
        config.value.column &&
        config.value.name &&
        config.value.displayFormat
    );
  }

  if (['METRIC', 'TABLE', 'GAUGE'].includes(config.vizType)) {
    return Boolean(
      config.columns && config.columns.length >= 1 && config.columns[0].name && config.columns[0].displayFormat
    );
  }

  if (['FUNNEL'].includes(config.vizType)) {
    return Boolean(
      config.measure &&
        config.measure.column &&
        config.measure.name &&
        config.measure.displayFormat &&
        config.dataField &&
        config.dataField.column &&
        config.dataField.name
    );
  }

  return false;
};
