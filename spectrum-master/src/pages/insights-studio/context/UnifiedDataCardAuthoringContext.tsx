import { createContext, Dispatch, SetStateAction, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { FilterValue } from 'components/inputs/types';
import {
  ConfigMode,
  DataCardVizConfig,
  DatasetSort,
  DatasetVariable,
  DataSource,
  DataSourceFields,
  Group,
  Joins,
  UnifiedDataCardAuthoringMode,
} from 'store/insights-studio/types';

import { blankDataCardConfig } from '../components/data-card-wizard/configuration-step/DataCardConfigStep';
import { CalculatedField } from '../dataset/configuration/sections/CalculatedFields.types';
import { DataSourceFieldTreePickerValue } from '../dataset/configuration/sections/DataSourceFieldTreePicker';
import { makeInitialGroupsValue, makeInitialSortsValue } from '../utils/UnifiedDataCard.util';

export interface UnifiedDataCardAuthoringContextValue {
  datasetId?: string | undefined;
  setDatasetId: Dispatch<SetStateAction<string | undefined>>;
  displayName?: string;
  setDisplayName: Dispatch<SetStateAction<string | undefined>>;
  description?: string;
  setDescription: Dispatch<SetStateAction<string | undefined>>;
  apiName?: string;
  setApiName: Dispatch<SetStateAction<string | undefined>>;
  tags: string[];
  setTags: Dispatch<SetStateAction<string[]>>;
  popupIsOpen: boolean;
  setPopupIsOpen: Dispatch<SetStateAction<boolean>>;
  dataCardWithNewDataset: boolean;
  setDataCardWithNewDataset: Dispatch<SetStateAction<boolean>>;
  selectedDataSources: DataSource[];
  setSelectedDataSources: Dispatch<SetStateAction<DataSource[]>>;
  selectedDataSourceFields?: DataSourceFieldTreePickerValue;
  setSelectedDataSourceFields: (dataSourceFields: DataSourceFieldTreePickerValue) => void;
  blendedData?: Joins[];
  setBlendedData: Dispatch<SetStateAction<Joins[] | undefined>>;
  filter?: FilterValue;
  setFilter: Dispatch<SetStateAction<FilterValue | undefined>>;
  group: boolean;
  setGroup: Dispatch<SetStateAction<boolean>>;
  groupBy?: Group[];
  setGroupBy: Dispatch<SetStateAction<Group[] | undefined>>;
  sort?: DatasetSort[];
  setSort: Dispatch<SetStateAction<DatasetSort[] | undefined>>;
  calculatedFields: CalculatedField[];
  setCalculatedFields: Dispatch<SetStateAction<CalculatedField[]>>;
  limit: string;
  setLimit: Dispatch<SetStateAction<string>>;
  variables?: DatasetVariable[];
  setVariables: Dispatch<SetStateAction<DatasetVariable[] | undefined>>;
  datasetConfigPreviewChanged: boolean;
  setDatasetConfigPreviewChanged: Dispatch<SetStateAction<boolean>>;
  dataCardVizConfig?: DataCardVizConfig;
  setDataCardVizConfig: Dispatch<SetStateAction<DataCardVizConfig | undefined>>;
  unifiedMode: UnifiedDataCardAuthoringMode;
  setUnifiedMode: Dispatch<SetStateAction<UnifiedDataCardAuthoringMode>>;
  dsFields: Record<string, DataSourceFields[]>;
  setDsFields: Dispatch<SetStateAction<Record<string, DataSourceFields[]>>>;
  sql: string;
  setSql: Dispatch<SetStateAction<string>>;
  configMode: ConfigMode;
  setConfigMode: Dispatch<SetStateAction<ConfigMode>>;
  reset: () => void;
}

const UnifiedDataCardAuthoringContext = createContext<UnifiedDataCardAuthoringContextValue>({
  datasetId: undefined,
  setDatasetId: () => {},
  displayName: '',
  setDisplayName: () => {},
  description: '',
  setDescription: () => {},
  apiName: '',
  setApiName: () => {},
  tags: [],
  setTags: () => {},
  popupIsOpen: false,
  setPopupIsOpen: () => {},
  dataCardWithNewDataset: true,
  setDataCardWithNewDataset: () => {},
  selectedDataSources: [],
  setSelectedDataSources: () => {},
  selectedDataSourceFields: undefined,
  setSelectedDataSourceFields: () => {},
  blendedData: [],
  setBlendedData: () => {},
  filter: undefined,
  setFilter: () => {},
  group: true,
  setGroup: () => {},
  groupBy: [],
  setGroupBy: () => {},
  sort: [],
  setSort: () => {},
  limit: '',
  setLimit: () => {},
  variables: [],
  setVariables: () => {},
  calculatedFields: [],
  setCalculatedFields: () => {},
  datasetConfigPreviewChanged: false,
  setDatasetConfigPreviewChanged: () => {},
  dataCardVizConfig: blankDataCardConfig,
  setDataCardVizConfig: () => {},
  unifiedMode: 'DATACARD_WITH_DATASET',
  setUnifiedMode: () => {},
  dsFields: {},
  setDsFields: () => {},
  sql: '',
  setSql: () => {},
  configMode: 'BASIC',
  setConfigMode: () => {},
  reset: () => {},
});

export const useUnifiedDataCardAuthoringContext = () => useContext(UnifiedDataCardAuthoringContext);

export const UnifiedDataCardAuthoringContextProvider = ({ children }: { children?: React.ReactNode }) => {
  const [datasetId, setDatasetId] = useState<UnifiedDataCardAuthoringContextValue['datasetId']>();
  const [displayName, setDisplayName] = useState<UnifiedDataCardAuthoringContextValue['displayName']>('');
  const [description, setDescription] = useState<UnifiedDataCardAuthoringContextValue['description']>('');
  const [apiName, setApiName] = useState<UnifiedDataCardAuthoringContextValue['apiName']>('');
  const [tags, setTags] = useState<UnifiedDataCardAuthoringContextValue['tags']>([]);
  const [popupIsOpen, setPopupIsOpen] = useState(false);
  const [selectedDataSources, setSelectedDataSources] = useState<
    UnifiedDataCardAuthoringContextValue['selectedDataSources']
  >([]);
  const [sort, setSort] = useState<UnifiedDataCardAuthoringContextValue['sort']>(makeInitialSortsValue());
  const [limit, setLimit] = useState<UnifiedDataCardAuthoringContextValue['limit']>('');
  const [group, setGroup] = useState<UnifiedDataCardAuthoringContextValue['group']>(false);
  const [groupBy, setGroupBy] = useState<UnifiedDataCardAuthoringContextValue['groupBy']>(makeInitialGroupsValue());
  const [selectedDataSourceFields, setSelectedDataSourceFields] = useState<
    UnifiedDataCardAuthoringContextValue['selectedDataSourceFields']
  >();
  const [blendedData, setBlendedData] = useState<UnifiedDataCardAuthoringContextValue['blendedData']>([]);
  const [filter, setFilter] = useState<UnifiedDataCardAuthoringContextValue['filter']>();
  const [variables, setVariables] = useState<UnifiedDataCardAuthoringContextValue['variables']>([]);
  const [dataCardVizConfig, setDataCardVizConfig] = useState<UnifiedDataCardAuthoringContextValue['dataCardVizConfig']>(
    blankDataCardConfig
  );
  const [datasetConfigPreviewChanged, setDatasetConfigPreviewChanged] = useState<
    UnifiedDataCardAuthoringContextValue['datasetConfigPreviewChanged']
  >(false);
  const [calculatedFields, setCalculatedFields] = useState<UnifiedDataCardAuthoringContextValue['calculatedFields']>(
    []
  );
  const [dataCardWithNewDataset, setDataCardWithNewDataset] = useState<
    UnifiedDataCardAuthoringContextValue['dataCardWithNewDataset']
  >(true);
  const [unifiedMode, setUnifiedMode] = useState<UnifiedDataCardAuthoringContextValue['unifiedMode']>(
    'DATACARD_WITH_DATASET'
  );
  const [dsFields, setDsFields] = useState<UnifiedDataCardAuthoringContextValue['dsFields']>({});
  const [sql, setSql] = useState<string>('');
  const [configMode, setConfigMode] = useState<ConfigMode>('BASIC');

  const reset = useCallback(() => {
    setDatasetId(undefined);
    setDisplayName('');
    setDescription('');
    setApiName('');
    setTags([]);
    setPopupIsOpen(false);
    setSelectedDataSources([]);
    setSelectedDataSourceFields(undefined);
    setBlendedData([]);
    setGroupBy(makeInitialGroupsValue());
    setSort(makeInitialSortsValue());
    setLimit('');
    setDataCardVizConfig(blankDataCardConfig);
    setFilter(undefined);
    setVariables([]);
    setCalculatedFields([]);
    setDataCardWithNewDataset(true);
    setUnifiedMode('DATACARD_WITH_DATASET');
    setGroup(false);
    setDatasetConfigPreviewChanged(false);
    setSql('');
    setConfigMode('BASIC');
  }, []);

  // Track if there are any configuration changes
  useEffect(() => {
    setDatasetConfigPreviewChanged(true);
  }, [selectedDataSources, selectedDataSourceFields, blendedData, limit]);

  const value = useMemo(() => {
    return {
      datasetId,
      setDatasetId,
      displayName,
      setDisplayName,
      apiName,
      setApiName,
      description,
      setDescription,
      tags,
      popupIsOpen,
      setPopupIsOpen,
      setTags,
      dataCardWithNewDataset,
      setDataCardWithNewDataset,
      selectedDataSources,
      setSelectedDataSources,
      selectedDataSourceFields,
      setSelectedDataSourceFields,
      blendedData,
      setBlendedData,
      filter,
      setFilter,
      group,
      setGroup,
      groupBy,
      setGroupBy,
      sort,
      setSort,
      limit,
      setLimit,
      variables,
      setVariables,
      calculatedFields,
      setCalculatedFields,
      sql,
      setSql,
      configMode,
      setConfigMode,
      datasetConfigPreviewChanged,
      setDatasetConfigPreviewChanged,
      dataCardVizConfig,
      setDataCardVizConfig,
      unifiedMode,
      setUnifiedMode,
      dsFields,
      setDsFields,
      reset,
    };
  }, [
    datasetId,
    displayName,
    apiName,
    description,
    tags,
    popupIsOpen,
    dataCardWithNewDataset,
    selectedDataSources,
    selectedDataSourceFields,
    blendedData,
    filter,
    group,
    groupBy,
    sort,
    limit,
    variables,
    calculatedFields,
    datasetConfigPreviewChanged,
    dataCardVizConfig,
    unifiedMode,
    dsFields,
    sql,
    configMode,
    reset,
  ]);

  return <UnifiedDataCardAuthoringContext.Provider value={value}>{children}</UnifiedDataCardAuthoringContext.Provider>;
};
