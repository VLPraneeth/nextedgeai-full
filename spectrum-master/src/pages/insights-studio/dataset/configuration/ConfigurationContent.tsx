import { Button, Modal, Radio } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import Fieldset from 'components/Fieldset';
import InlineMessage from 'components/InlineMessage';
import { Divider, HStack } from 'components/layout';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import SqlEditor from 'components/SQLEditor';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useUnifiedDataCardAuthoringContext } from 'pages/insights-studio/context/UnifiedDataCardAuthoringContext';
import { useDatasetPreview } from 'pages/insights-studio/utils/useDatasetPreview';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { useGetQueryMutation } from 'store/insights-studio';
import { Dataset } from 'store/insights-studio/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import DatasetSampleOutput from '../DatasetSampleOutput';
import { ConfigurationExistingDataset } from './ConfigurationExistingDataset';
import { DataSourcePicker } from './DataSourcePicker';
import { BlendDataPicker } from './sections/BlendDataPicker';
import CalculatedFields from './sections/CalculatedFields';
import { DatasetAlias } from './sections/DatasetAlias';
import { DataSourceFieldTreePicker } from './sections/DataSourceFieldTreePicker';
import { FilterPicker } from './sections/FilterPicker';
import { GroupPicker } from './sections/GroupPicker';
import { LimitPicker } from './sections/LimitPicker';
import { SortPicker } from './sections/SortPicker';
import { VariablePicker } from './sections/VariablePicker';

import './ConfigurationContent.scss';

const tn = tNamespaced('Dataset');
const advanceDatasetTn = tNamespaced('InsightsStudio');

export interface ConfigurationContentProps {
  errorMessage?: string;
}
export const ConfigurationContent = ({ errorMessage }: ConfigurationContentProps) => {
  const [sectionsCollapsed, setSectionsCollapsed] = useState<Record<string, boolean>>({});
  const { userHasPermission } = useUserHasPermission();

  const {
    unifiedMode,
    setDataCardWithNewDataset,
    dataCardWithNewDataset,
    getDatasetPayload,
    datasetConfigPreviewChanged,
    getDatasetAndDataCard,
  } = useUnifiedDataCardAuthoring();

  const sections = useMemo(() => {
    return [
      {
        title: tn('blend_data_config'),
        tooltip: tn('blend_data_config_tooltip'),
        component: <BlendDataPicker />,
      },
      {
        title: tn('select_fields_config'),
        tooltip: tn('select_fields_config_tooltip'),
        component: <DataSourceFieldTreePicker />,
      },
      {
        title: tn('add_calculated_field_config'),
        tooltip: tn('add_calculated_field_config_tooltip'),
        component: <CalculatedFields />,
      },
      {
        title: tn('select_group'),
        tooltip: tn('select_group_config_tooltip'),
        component: <GroupPicker />,
      },
      {
        title: tn('add_filters_config'),
        tooltip: tn('add_filters_config_tooltip'),
        component: <FilterPicker />,
      },
      {
        title: tn('sort_config'),
        tooltip: tn('sort_config_tooltip'),
        component: <SortPicker />,
      },
      {
        title: tn('set_limit_config'),
        tooltip: tn('set_limit_config_tooltip'),
        component: <LimitPicker />,
      },
      {
        title: tn('variables_config'),
        tooltip: tn('variables_config_tooltip'),
        component: <VariablePicker />,
      },
    ];
  }, []);

  const authoringContextProp = useUnifiedDataCardAuthoringContext();

  const {
    configMode,
    setConfigMode,
    displayName,
    apiName: name,
    description,
    tags,
    datasetId,
    setSql,
  } = authoringContextProp;

  const [modalOpen, setModalOpen] = useState(false);

  const [getQuery, { isLoading }] = useGetQueryMutation();
  const { getDatasetPreview, datasetPreviewResult } = useDatasetPreview({ getDatasetAndDataCard });

  function handleClose() {
    setModalOpen(false);
  }
  function handleOk() {
    const commentPlaceholder = advanceDatasetTn('AdvanceDataset.sql_comment');
    if (configMode === 'BASIC') {
      const basicInfo = { displayName, name, description, tags, id: datasetId };
      const dataset = getDatasetPayload(basicInfo) as Dataset;
      getQuery(dataset)
        .unwrap()
        .then((data) => {
          setSql(data?.sql || commentPlaceholder);
        })
        .catch(() => {
          setSql(commentPlaceholder);
        });
    }
    setConfigMode(configMode === 'BASIC' ? 'SQL' : 'BASIC');
    handleClose();
  }

  useEffect(() => {
    const { setDataCardWithNewDataset } = authoringContextProp;

    if (!userHasPermission(AllPermissions.CREATE_DATASET)) {
      setDataCardWithNewDataset(false);
    }
  }, [authoringContextProp, userHasPermission]);

  const nextModeName =
    configMode === 'BASIC'
      ? advanceDatasetTn('AdvanceDataset.basic_mode')
      : advanceDatasetTn('AdvanceDataset.sql_mode');

  return (
    <div className="configuration-content">
      <InlineMessage title={errorMessage} type="error">
        {errorMessage}
      </InlineMessage>
      {unifiedMode === 'DATACARD_WITH_DATASET' && (
        <>
          <HStack>
            <Radio.Group
              value={dataCardWithNewDataset ? 'new' : 'existing'}
              onChange={(evt) => setDataCardWithNewDataset(evt.target.value === 'new')}>
              <Radio value={'new'} disabled={!userHasPermission(AllPermissions.CREATE_DATASET)}>
                {tn('new_dataset_option')}
              </Radio>
              <Radio value={'existing'}>{tn('existing_dataset_option')}</Radio>
            </Radio.Group>
          </HStack>
          <Divider />
        </>
      )}
      <DatasetSampleOutput
        getDatasetPreview={getDatasetPreview}
        datasetPreviewResult={datasetPreviewResult}
        datasetConfigPreviewChanged={datasetConfigPreviewChanged}
      />
      {dataCardWithNewDataset ? (
        <>
          {/* When a popover is open don't allow scrolling */}
          <ScrollableArea scrollable={configMode !== 'SQL' && !authoringContextProp.popupIsOpen} bottomOffset={325}>
            <Radio.Group
              className="configuration-content__config-mode"
              onChange={() => setModalOpen(true)}
              value={configMode}>
              <Radio.Button value="BASIC">{advanceDatasetTn('AdvanceDataset.basic_mode')}</Radio.Button>
              <Radio.Button value="SQL">{advanceDatasetTn('AdvanceDataset.sql_mode')}</Radio.Button>
            </Radio.Group>

            <Divider />

            {configMode === 'BASIC' && (
              <>
                <DataSourcePicker />
                <DatasetAlias />
                {sections.map(({ title, tooltip, component }) => (
                  <div className="configuration-content__fieldset-wrapper" key={title}>
                    <Fieldset
                      collapsible
                      tooltip={tooltip}
                      collapsed={sectionsCollapsed[title] ?? true}
                      onToggleCollapse={() =>
                        setSectionsCollapsed((current) => ({
                          ...current,
                          [title]: !Boolean(current[title] ?? true),
                        }))
                      }
                      title={title}>
                      <div className="configuration-content__fieldset-container">{component}</div>
                    </Fieldset>
                  </div>
                ))}
              </>
            )}
            {configMode === 'SQL' && <SqlEditor getDatasetPreview={getDatasetPreview} />}
          </ScrollableArea>
        </>
      ) : (
        <ConfigurationExistingDataset />
      )}

      <Modal
        title={nextModeName}
        onCancel={handleClose}
        onOk={handleOk}
        centered
        visible={modalOpen}
        footer={
          <>
            <Button onClick={handleClose}>
              {advanceDatasetTn('AdvanceDataset.back_to_mode', { mode: nextModeName })}
            </Button>
            <Button type="primary" onClick={handleOk} loading={isLoading}>
              {tc('continue')}
            </Button>
          </>
        }>
        {configMode === 'BASIC'
          ? advanceDatasetTn('AdvanceDataset.to_sql_mode_modal_text')
          : advanceDatasetTn('AdvanceDataset.to_basic_mode_modal_text')}
      </Modal>
    </div>
  );
};
