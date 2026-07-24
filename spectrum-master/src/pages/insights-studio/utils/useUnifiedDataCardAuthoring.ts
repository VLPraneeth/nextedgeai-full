import { navigate } from '@reach/router';
import { useCallback, useEffect, useMemo } from 'react';

import { removeBlankFilter } from 'components/inputs/filter/utils';
import { useEnhancedSelector } from 'hooks/redux';
import { useUnifiedDataCardAuthoringContext } from 'pages/insights-studio/context/UnifiedDataCardAuthoringContext';
import { publishedEntityStatuses } from 'store/entity/selectors';
import {
  useCreateDatasetMutation,
  useGetDatasetAndEntityInfoQuery,
  useSaveDataCardWithDatasetMutation,
  useUpdateDatasetMutation,
} from 'store/insights-studio';
import {
  DataCardVizConfig,
  DataCardWithDataset,
  Dataset,
  DatasetSort,
  Group,
  Joins,
  UnifiedDataCardAuthoringMode,
} from 'store/insights-studio/types';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useDataCardAuthoringContext } from '../context/DataCardAuthoringContext';
import { useInsightsViewContext } from '../context/InsightsViewContext';
import {
  isSelectedDataSourceFieldsEmpty,
  removeBlankGroupsValue,
  removeBlankSortsValue,
  toDataCardColumnOptions,
  toSelectedDataSources,
  toSelectedFields,
  toVariableMap,
} from './UnifiedDataCard.util';
import { useDatasetConfig } from './useDatasetConfig';
import { useUnifiedDataCardNavigate } from './useUnifiedDataCardNavigate';

enum UnifiedWizardSteps {
  Basic = 0,
  DatasetConfiguration = 1,
  DataCardConfiguration = 2,
}

export interface ShowUnifiedParams {
  // Automatically add the datacard to this dashboard id if present
  dashboardId?: string;

  // Edit this dataset in dataset only mode
  datasetId?: string;

  // Edit this datacard in dataset datacard mode
  dataCardId?: string;
}

export const useUnifiedDataCardAuthoring = () => {
  const authoringContextProp = useUnifiedDataCardAuthoringContext();

  const { navigateToCurrentDashboard } = useUnifiedDataCardNavigate();
  const { selectedDataSources, selectedDataSourceFields, variables, unifiedMode } = authoringContextProp;
  const { loadDataset } = useDatasetConfig();
  const { isThoughtSpotView } = useInsightsViewContext();

  const {
    showUnifiedDataCardWizard,
    setShowUnifiedDataCardWizard,
    createCardAndAddToDashboard,
    selectedDataCard,
    selectDataCardById,
    createDataCardFromDataset,
    preselectedDatasetId,
    setPreselectedDatasetId,
  } = useDataCardAuthoringContext();
  const [saveDataCardWithDataset] = useSaveDataCardWithDatasetMutation();

  const { data: dataSources } = useGetDatasetAndEntityInfoQuery({
    isThoughtspot: isThoughtSpotView,
    withEntityInfo: true,
  });

  useEffect(() => {
    const { unifiedMode, setDataCardWithNewDataset } = authoringContextProp;

    if (
      !preselectedDatasetId &&
      selectedDataCard?.contents?.configuration?.datasetId &&
      unifiedMode === 'DATACARD_WITH_DATASET'
    ) {
      // Only set this if its not initialized yet
      setDataCardWithNewDataset(false);
      setPreselectedDatasetId(selectedDataCard.contents.configuration.datasetId);
    }
    // Only update when the user pick different datacard
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedDataCard]);

  const showUnifiedWizard = useCallback(
    (
      visible: boolean,
      unifiedMode: UnifiedDataCardAuthoringMode,
      { dashboardId, datasetId, dataCardId }: ShowUnifiedParams
    ) => {
      const { setUnifiedMode, setDataCardWithNewDataset } = authoringContextProp;
      setUnifiedMode(unifiedMode);

      if (!visible) {
        if (isThoughtSpotView) {
          setShowUnifiedDataCardWizard(visible);
          navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATASETS));
        } else {
          navigateToCurrentDashboard();
          setShowUnifiedDataCardWizard(visible);
        }
        return;
      }

      if (unifiedMode === 'DATASET_ONLY') {
        if (datasetId) {
          // Edit dataset
          loadDataset(datasetId);
        }
      } else {
        if (dataCardId) {
          // Edit datacard
          selectDataCardById(dataCardId);
        } else if (datasetId && dashboardId) {
          // Drag and drop dataset
          createDataCardFromDataset(datasetId, { dashboardId });
          setDataCardWithNewDataset(false);
        } else {
          // Create new datacard and add to dashboard
          dashboardId && createCardAndAddToDashboard(dashboardId);
        }
      }
      setShowUnifiedDataCardWizard(visible);
    },
    [
      authoringContextProp,
      createCardAndAddToDashboard,
      createDataCardFromDataset,
      loadDataset,
      navigateToCurrentDashboard,
      selectDataCardById,
      setShowUnifiedDataCardWizard,
      isThoughtSpotView,
    ]
  );

  const getDatasetPayload = useCallback(
    (basicInfo: Record<string, undefined | string | string[]>) => {
      const {
        blendedData,
        filter,
        calculatedFields,
        groupBy,
        sort,
        limit,
        group,
        sql,
        configMode,
      } = authoringContextProp;

      const { fromDataset } = toSelectedDataSources(selectedDataSources);

      // Pick only the BE join attributes
      const joins: Joins[] =
        blendedData?.map(({ field1, field2, joinType }) => {
          return {
            field1,
            field2,
            joinType,
          };
        }) || [];

      // Pick only the BE group attributes
      const groups: Group[] = group
        ? removeBlankGroupsValue(
            groupBy?.map((group) => {
              return {
                datasetField: group.datasetField,
              };
            }) || []
          )
        : [];

      const sorts: DatasetSort[] = removeBlankSortsValue(
        sort?.map(({ ascending, field }) => ({
          ascending,
          field,
        })) || []
      );

      const variablesMap = toVariableMap(variables);

      return {
        ...basicInfo,
        datasetConfig: {
          fromDataset,
          selectedFields: toSelectedFields(selectedDataSourceFields, dataSources),
          calculatedFields,
          joins,
          filter: removeBlankFilter(filter),
          group,
          groupBy: groups,
          sort: sorts,
          limit,
          configMode,
        },
        variablesMap,
        sql,
      };
    },
    [authoringContextProp, dataSources, selectedDataSourceFields, selectedDataSources, variables]
  );

  // Build the payload for the previews and save
  const getDatasetAndDataCard = useCallback(
    (vizConfig?: DataCardVizConfig) => {
      const { displayName, apiName: name, description, tags } = authoringContextProp;
      const basicInfo = { displayName, name, description, tags };

      if (displayName) {
        const dataCardWithDataset: DataCardWithDataset = {
          dataset: getDatasetPayload(basicInfo),
          datacard: {
            ...basicInfo,
            id: selectedDataCard?.id,
            contents: {
              // @ts-ignore
              configuration: {
                ...vizConfig,
              },
            },
          },
        };

        return dataCardWithDataset;
      }
    },
    [authoringContextProp, getDatasetPayload, selectedDataCard?.id]
  );

  const [updateDataset, { isLoading: isDatasetUpdating }] = useUpdateDatasetMutation();
  const [saveDataset, { isLoading: isDatasetCreating }] = useCreateDatasetMutation();
  // Save and close the data card with dataset
  const saveAndClose = useCallback(
    (vizConfig?: DataCardVizConfig) => {
      const dataCardWithDataset = getDatasetAndDataCard(vizConfig);
      const { displayName, apiName: name, description, tags, datasetId, unifiedMode } = authoringContextProp;
      const basicInfo = { displayName, name, description, tags, id: datasetId };

      if (unifiedMode === 'DATASET_ONLY') {
        const payload = getDatasetPayload(basicInfo);
        if (datasetId) {
          // Update dataset
          return updateDataset(payload as Dataset);
        } else {
          // New dataset
          return saveDataset(payload as Dataset);
        }
      }
      if (dataCardWithDataset) {
        // Save new datacard and dataset
        return saveDataCardWithDataset(dataCardWithDataset);
      }
    },
    [
      authoringContextProp,
      getDatasetAndDataCard,
      getDatasetPayload,
      saveDataCardWithDataset,
      saveDataset,
      updateDataset,
    ]
  );

  const validateToStep = useCallback(
    (requestedStep: number) => {
      const {
        displayName,
        selectedDataSources,
        dataCardWithNewDataset,
        dataCardVizConfig,
        selectedDataSourceFields,
        calculatedFields,
      } = authoringContextProp;

      if (requestedStep > UnifiedWizardSteps.Basic) {
        if (!displayName || displayName.length <= 0) {
          return 'Display name cannot be empty.';
        }
      }
      if (requestedStep > UnifiedWizardSteps.DatasetConfiguration) {
        if (dataCardWithNewDataset) {
          if (selectedDataSources.length <= 0) {
            return 'Select Data cannot be empty.';
          }
          if (isSelectedDataSourceFieldsEmpty(selectedDataSourceFields) && !calculatedFields.length) {
            return 'Select fields and calculated fields cannot be empty.';
          }
        } else if (!preselectedDatasetId) {
          return 'Select existing Data set cannot be empty.';
        }
      }

      if (unifiedMode === 'DATACARD_WITH_DATASET') {
        if (requestedStep > UnifiedWizardSteps.DataCardConfiguration && !dataCardVizConfig?.vizType) {
          return 'Viz type cannot be empty.';
        }
      }
    },
    [authoringContextProp, preselectedDatasetId, unifiedMode]
  );

  return {
    ...authoringContextProp,
    dataSources,
    showUnifiedWizard,
    getDataCardColumnOptions: () =>
      toDataCardColumnOptions(selectedDataSourceFields, authoringContextProp.calculatedFields),
    isUnifiedWizard: showUnifiedDataCardWizard,
    getDatasetPayload,
    getDatasetAndDataCard,
    validateToStep,
    saveAndClose,
    isDatasetUpdating,
    isDatasetCreating,
  };
};

export const useInsightsRequirements = () => {
  const { entities, entitiesFetching: isEntitiesFetching } = useEnhancedSelector((state) => state.entity);

  const hasPublishedPipeline = useMemo(() => {
    return Boolean(entities?.some((entity) => publishedEntityStatuses.includes(entity.pipelineStatus)));
  }, [entities]);

  return { hasPublishedPipeline, isEntitiesFetching };
};
