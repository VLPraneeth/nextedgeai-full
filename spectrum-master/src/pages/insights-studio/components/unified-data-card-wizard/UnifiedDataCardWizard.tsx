import { navigate } from '@reach/router';
import { message, Modal, Spin } from 'antd';
import { isFunction } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack } from 'components/layout';
import { Step, Steps } from 'components/steps';
import usePreviousValue from 'hooks/usePreviousValue';
import useQueryParams from 'hooks/useQueryParams';
import { AddToDashInfo, useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import { useUnifiedDataCardAuthoringContext } from 'pages/insights-studio/context/UnifiedDataCardAuthoringContext';
import { DatasetReviewStep } from 'pages/insights-studio/dataset/review/ReviewStep';
import { useDatasetConfig } from 'pages/insights-studio/utils/useDatasetConfig';
import {
  useInsightsRequirements,
  useUnifiedDataCardAuthoring,
} from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { useGetAllDataCardsQuery, useGetDatasetsQuery } from 'store/insights-studio';
import { UnifiedDataCardAuthoringMode } from 'store/insights-studio/types';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { DatasetConfigurationStep } from '../../dataset/configuration/ConfigurationStep';
import { DataCardConfigStep } from '../data-card-wizard/configuration-step/DataCardConfigStep';
import { DataCardReviewStep } from '../data-card-wizard/review-step/DataCardReviewStep';
import { BasicStep } from './BasicStep';
import { RequirementNotMet } from './RequirementNotMet';

import './UnifiedDataCardWizard.less';

export interface DataCardWizardProps {
  close: () => void;
  visible?: boolean;
}

export const UnifiedDataCardWizard = withI18n(() => {
  const {
    resetAuthoring,
    showUnifiedDataCardWizard,
    selectedDataCard,
    setShowUnifiedDataCardWizard,
  } = useDataCardAuthoringContext();
  const { reset, unifiedMode, datasetId, configMode } = useUnifiedDataCardAuthoringContext();
  const { showUnifiedWizard, validateToStep } = useUnifiedDataCardAuthoring();
  const { loadDataset } = useDatasetConfig();
  const { isThoughtSpotView } = useInsightsViewContext();
  // Make sure the dataset is also loaded
  // When in thoughtspot routes, always use thoughtspot datasets (matching DatasetList behavior)
  const isThoughtspotRoute = window.location.pathname.toLowerCase().includes('insights-studio/ts/');
  const { data: datasets, isLoading: isDatasetsLoading, isFetching: isDatasetsFetching } = useGetDatasetsQuery(
    isThoughtspotRoute || isThoughtSpotView
  );
  const { data: dataCards } = useGetAllDataCardsQuery();
  const [queryParams] = useQueryParams<AddToDashInfo>();
  const { tc, tn } = useI18nContext();

  const {
    datasetMatch,
    thoughtspotDatasetMatch,
    dataCardMatch,
    navigateToCurrentDashboard,
    isDatasetCurrentUrl,
    isThoughtspotDatasetCurrentUrl,
    isDataCardCurrentUrl,
  } = useUnifiedDataCardNavigate();

  const closeAndReset = useCallback(() => {
    if (isThoughtSpotView) {
      navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATASETS)).then(() => {
        setShowUnifiedDataCardWizard(false);
      });
    } else {
      navigateToCurrentDashboard();
    }
    setCurrentStep(0);
    resetAuthoring();
    reset();
  }, [navigateToCurrentDashboard, reset, resetAuthoring, isThoughtSpotView]);

  const { hasPublishedPipeline, isEntitiesFetching: isInsightsRequirementFetching } = useInsightsRequirements();

  const confirmBeforeClose = useCallback(
    (onOkCallback?: () => void, onCancelCallback?: () => void) => {
      if (!hasPublishedPipeline) {
        closeAndReset();
      } else {
        // Note: Add a generic message for now
        // TODO: Track if there are any changes in any of the steps
        Modal.confirm({
          title: `${tc('discard_changes')}?`,
          content:
            unifiedMode === 'DATACARD_WITH_DATASET'
              ? tn('discard_data_card_confirmation_message')
              : tn('discard_dataset_confirmation_message'),
          onOk: () => {
            isFunction(onOkCallback) ? onOkCallback() : closeAndReset();
          },
          onCancel: () => {
            onCancelCallback?.();
          },
          okText: tc('yes_discard_changes'),
          cancelText: tc('keep_editing'),
          okType: 'primary',
        });
      }
    },
    [closeAndReset, hasPublishedPipeline, tc, tn, unifiedMode]
  );

  // Deeplink controller for show/hiding the unified wizard
  const prevShowUnifiedDataCardWizard = usePreviousValue(showUnifiedDataCardWizard);
  useEffect(() => {
    if (!showUnifiedDataCardWizard && !prevShowUnifiedDataCardWizard) {
      // Show if it was completely hidden
      if (datasetMatch?.datasetId && isDatasetCurrentUrl() && datasets?.length) {
        // Show it in dataset mode
        showUnifiedWizard(true, 'DATASET_ONLY', {});
        if (datasetMatch.datasetId?.toLowerCase() !== 'new') {
          loadDataset(datasetMatch?.datasetId);
        }
      } else if (
        isThoughtSpotView &&
        thoughtspotDatasetMatch?.datasetId &&
        isThoughtspotDatasetCurrentUrl() &&
        (datasets?.length || thoughtspotDatasetMatch.datasetId === 'new') // If its a new instance then the datasets?.length will always be 0
      ) {
        showUnifiedWizard(true, 'DATASET_ONLY', {});
        if (thoughtspotDatasetMatch.datasetId?.toLowerCase() !== 'new') {
          loadDataset(thoughtspotDatasetMatch?.datasetId);
        }
      } else if (dataCardMatch?.dataCardId && isDataCardCurrentUrl() && dataCards?.length) {
        // Show it in datacard mode
        if (dataCardMatch?.dataCardId !== 'new') {
          showUnifiedWizard(true, 'DATACARD_WITH_DATASET', { dataCardId: dataCardMatch.dataCardId });
        } else {
          showUnifiedWizard(true, 'DATACARD_WITH_DATASET', queryParams);
        }
      }
    } else if (
      showUnifiedDataCardWizard &&
      prevShowUnifiedDataCardWizard &&
      !datasetMatch?.datasetId &&
      !dataCardMatch?.datasetId &&
      !thoughtspotDatasetMatch?.datasetId
    ) {
      closeAndReset();
    }
  }, [
    closeAndReset,
    dataCardMatch?.dataCardId,
    dataCards?.length,
    datasetMatch?.datasetId,
    datasets?.length,
    isDataCardCurrentUrl,
    isDatasetCurrentUrl,
    loadDataset,
    prevShowUnifiedDataCardWizard,
    queryParams,
    showUnifiedDataCardWizard,
    showUnifiedWizard,
    thoughtspotDatasetMatch?.datasetId,
    isThoughtspotDatasetCurrentUrl,
    isThoughtSpotView,
  ]);

  const [currentStep, setCurrentStep] = useState(0);

  const nextStep = () => navigateToStep(currentStep + 1);
  const previousStep = () => setCurrentStep(currentStep - 1);

  const navigateToStep = (stepNumber: number) => {
    if (configMode === 'SQL') {
      setCurrentStep(stepNumber);
      return;
    }
    const errorMessage = validateToStep(stepNumber);
    errorMessage ? message.error(errorMessage) : setCurrentStep(stepNumber);
  };

  // TODO: Consolidate the basic step of the stand along data card and data card with dataset.
  const contentMap: Record<UnifiedDataCardAuthoringMode, JSX.Element[]> = {
    DATACARD_WITH_DATASET: [
      <BasicStep onCancel={confirmBeforeClose} onPrevious={() => {}} onSuccess={nextStep} />,
      <DatasetConfigurationStep onCancel={confirmBeforeClose} onPrevious={previousStep} onSuccess={nextStep} />,
      <DataCardConfigStep onCancel={confirmBeforeClose} onPrevious={previousStep} onSuccess={nextStep} />,
      <DataCardReviewStep onCancel={confirmBeforeClose} onPrevious={previousStep} onSuccess={closeAndReset} />,
    ],
    DATASET_ONLY: [
      <BasicStep onCancel={confirmBeforeClose} onPrevious={() => {}} onSuccess={nextStep} />,
      <DatasetConfigurationStep onCancel={confirmBeforeClose} onPrevious={previousStep} onSuccess={nextStep} />,
      <DatasetReviewStep onCancel={confirmBeforeClose} onPrevious={previousStep} onSuccess={closeAndReset} />,
    ],
  };

  const contentSteps: Record<UnifiedDataCardAuthoringMode, JSX.Element[]> = {
    DATACARD_WITH_DATASET: [
      <Step title="Describe card" />,
      <Step title="Configure data" />,
      <Step title="Add card" />,
      <Step title="Preview" />,
    ],
    DATASET_ONLY: [<Step title="Describe Data set" />, <Step title="Configure data" />, <Step title="Preview" />],
  };

  const title = useMemo(() => {
    if (unifiedMode === 'DATACARD_WITH_DATASET') {
      if (selectedDataCard?.id) {
        return tn('edit_data_card') + ': ' + selectedDataCard.displayName;
      }
      return tn('create_data_card');
    } else {
      if (datasetId) {
        const selectedDataset = datasets?.find((set) => set.id === datasetId);
        return tn('edit_data_set') + (!!selectedDataset?.displayName ? `: ${selectedDataset.displayName}` : '');
      }
      return tn('create_data_set');
    }
  }, [datasetId, selectedDataCard?.id, unifiedMode, datasets, selectedDataCard?.displayName, tn]);

  return (
    <DrawerPanel
      destroyOnClose
      maskClosable
      noPadding
      onClose={() => confirmBeforeClose()}
      title={title}
      visible={showUnifiedDataCardWizard}
      width="full">
      {isInsightsRequirementFetching ? (
        <HStack className="unified-data-card-wizard">
          <Spin className="unified-data-card-wizard__spinner" />
        </HStack>
      ) : hasPublishedPipeline ? (
        <HStack spacing="z" align="start" className="unified-data-card-wizard">
          <Steps direction="vertical" current={currentStep} onChange={(newStep: number) => navigateToStep(newStep)}>
            {contentSteps[unifiedMode]}
          </Steps>
          <div key={unifiedMode} className="data-card-wizard__content">
            {contentMap[unifiedMode][currentStep]}
          </div>
        </HStack>
      ) : (
        <RequirementNotMet onClose={closeAndReset} />
      )}
    </DrawerPanel>
  );
}, 'InsightsStudio');
