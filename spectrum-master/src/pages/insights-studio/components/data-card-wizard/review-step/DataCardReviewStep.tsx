import { Button, message } from 'antd';
import { useMemo } from 'react';

import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { Text } from 'components/typography';
import { NewCardInfo, useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import DataSetUsedBy from 'pages/insights-studio/dataset/review/UsedByList';
import { useAddCardToDashboard } from 'pages/insights-studio/utils/dashboardUtils';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import {
  useCreateDataCardMutation,
  useEditDataCardAndCreateDatasetMutation,
  useEditDataCardMutation,
  useGetDatasetsQuery,
  useSaveDataCardWithDatasetMutation,
} from 'store/insights-studio';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { DataCardPreview } from '../configuration-step/DataCardPreview';

import './DataCardReviewStep.scss';

const tn = tNamespaced('InsightsStudio');

export interface DataCardReviewStepProps {
  onCancel: () => void;
  onPrevious: () => void;
  onSuccess: () => void;
}
export const DataCardReviewStep = ({ onCancel, onPrevious, onSuccess }: DataCardReviewStepProps) => {
  const { data: datasets, isLoading } = useGetDatasetsQuery();

  const { navigateToCurrentDashboard } = useUnifiedDataCardNavigate();
  const {
    addToDashAfterCreate,
    addToDashInfo,
    newCardInfo,
    resetAuthoring,
    selectedDataCard,
  } = useDataCardAuthoringContext();
  const dataCardId = selectedDataCard?.id;
  const [createDataCard, { error: createDataCardError }] = useCreateDataCardMutation();
  const [editDataCard, { error: editError }] = useEditDataCardMutation();
  const [editDataCardAndCreateNewDataset, { error: editSaveDatasetError }] = useEditDataCardAndCreateDatasetMutation();
  const [saveDataCardWithDataset, { error: saveDataCardWithDatasetError }] = useSaveDataCardWithDatasetMutation();
  const addCardToDashboard = useAddCardToDashboard(addToDashInfo?.dashboardId);
  const {
    displayName,
    description,
    apiName,
    tags,
    isUnifiedWizard,
    dataCardWithNewDataset,
    getDatasetAndDataCard,
    dataCardVizConfig: vizConfig,
  } = useUnifiedDataCardAuthoring();

  const dataset = useMemo(() => {
    if (dataCardWithNewDataset) {
      const dataCardWithDataset = getDatasetAndDataCard(vizConfig);
      if (dataCardWithDataset?.dataset) {
        return dataCardWithDataset.dataset;
      }
    }
    const datasetId = vizConfig?.datasetId || selectedDataCard?.contents?.configuration?.datasetId;
    if (datasetId) {
      return datasets?.find((dataset) => dataset.id === datasetId);
    }
  }, [
    dataCardWithNewDataset,
    datasets,
    getDatasetAndDataCard,
    selectedDataCard?.contents?.configuration?.datasetId,
    vizConfig,
  ]);

  const previewDataCardConfig = useMemo(() => {
    const dataCardWithDataset = getDatasetAndDataCard(vizConfig);
    if (dataCardWithDataset?.datacard?.contents?.configuration?.vizType) {
      return dataCardWithDataset.datacard;
    }
  }, [getDatasetAndDataCard, vizConfig]);

  const saveConfiguration = () => {
    if (!vizConfig) {
      return;
    }
    if (selectedDataCard) {
      if (dataCardWithNewDataset) {
        const dataCardWithDataset = getDatasetAndDataCard(vizConfig);
        if (dataCardWithDataset) {
          editDataCardAndCreateNewDataset(dataCardWithDataset).then((result) => {
            if ('data' in result) {
              message.success('Data card updated');
              navigateToCurrentDashboard();
              onSuccess();
            }
          });
        }
      } else {
        editDataCard({
          ...selectedDataCard,
          displayName: displayName ?? selectedDataCard.displayName,
          description: description ?? selectedDataCard.description,
          name: apiName ?? selectedDataCard.name,
          tags: tags ?? selectedDataCard.tags,
          contents: { configuration: vizConfig },
        }).then((result2) => {
          if ('data' in result2) {
            message.success('Data card updated');
            navigateToCurrentDashboard();
            onSuccess();
          }
        });
      }
      // Do not continue in this method if there is a selectedDataCard. Rest of method is for new data cards
      return;
    }

    // Unified wizard mode saveAndClose
    if (isUnifiedWizard) {
      if (dataCardWithNewDataset) {
        // Note: its important to pass the vizConfig here
        // because of a bug that vizConfig is blank in context on save!? 🤔
        const dataCardWithDataset = getDatasetAndDataCard(vizConfig);
        if (dataCardWithDataset) {
          saveDataCardWithDataset(dataCardWithDataset).then((result) => {
            if ('data' in result) {
              if (result.data.datacard.id) {
                addCardToDashboard(result.data.datacard.id);
              }
              navigateToCurrentDashboard();

              onSuccess();
            }
          });
        }
        return;
      }
    }
    if (!selectedDataCard && (newCardInfo || displayName)) {
      let basicInfo: NewCardInfo | undefined;
      if (newCardInfo) {
        basicInfo = newCardInfo;
      } else {
        // Api name will be auto generated by the backend now
        basicInfo = displayName
          ? { displayName, name: apiName || '', description: description || '', tags: tags || [] }
          : undefined;
      }

      // This creation method is only used for creating a data card from dragging a dataset. Other creation methods
      // are handled by saveDataCardWithDatasetAndClose
      // TODO: Tie this into unified save when in more stable state
      if (basicInfo) {
        createDataCard(basicInfo).then((result) => {
          if ('data' in result) {
            editDataCard({
              ...result.data,
              contents: {
                configuration: vizConfig,
              },
            }).then((result2) => {
              if ('data' in result2) {
                message.success(tn('data_card_created'));

                if (addToDashAfterCreate && addToDashInfo) {
                  addCardToDashboard(result.data.id, addToDashInfo.layouts, addToDashInfo.item).then(() => {
                    resetAuthoring();
                  });
                } else {
                  // Just reset if the add datacard is triggered
                  // from the data card list pane new button
                  resetAuthoring();
                }
                navigateToCurrentDashboard();
                onSuccess();
              }
            });
          }
        });
      }
    }
  };

  const localErrorMessage = useMemo(() => {
    return getRtkQueryErrorMessage(
      createDataCardError || editError || editSaveDatasetError || saveDataCardWithDatasetError
    );
  }, [createDataCardError, editError, editSaveDatasetError, saveDataCardWithDatasetError]);

  return (
    <div>
      <InlineMessage allowMultiline type={InlineMessageTypes.ERROR} title={localErrorMessage}>
        {localErrorMessage}
      </InlineMessage>
      {!isLoading && (
        <div style={{ width: '50%' }}>
          <Text color="gray-900" lineHeight="loose" size="lg" weight="bold">
            {displayName || ''}
          </Text>
          {previewDataCardConfig && (
            // @ts-expect-error dataset missing id
            <DataCardPreview dataCard={previewDataCardConfig} dataset={dataset} hideHeader showPreview />
          )}
        </div>
      )}

      {dataCardId && <DataSetUsedBy usedById={dataCardId} type="DATACARD" />}
      <div className="synri-drawer-panel__footer">
        <Button onClick={onCancel}>{tc('cancel')}</Button>
        <Button onClick={onPrevious}>{tc('previous')}</Button>
        <Button type="primary" htmlType="submit" form="data-card-form" onClick={saveConfiguration}>
          {tc('save')} & {tc('finish')}
        </Button>
      </div>
    </div>
  );
};
