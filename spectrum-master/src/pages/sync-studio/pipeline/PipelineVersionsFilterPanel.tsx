import moment from 'moment';
import { useCallback, useEffect } from 'react';

import Button from 'components/Button';
import DateRangePicker from 'components/DateRangePicker';
import DrawerPanel from 'components/DrawerPanel';
import ClearFilterButton from 'components/filter-components/ClearFilterButton';
import { useI18nNamespace } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { tCommon } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import { pipelineVersionsFiltersInitialState } from './PipelineVersions';
import { VersionFilterOptionsData } from './PipelineVersions.hooks';

export interface PipelineVersionsFilterPanelProps {
  visible: boolean;
  onClose: () => void;
  versionFilterData: VersionFilterOptionsData;
  activeFilters: VersionFilterOptionsData;
  setActiveFilters: (
    newState: Partial<VersionFilterOptionsData> | ((prevState: VersionFilterOptionsData) => VersionFilterOptionsData)
  ) => void;
}

const PipelineVersionsFilterPanel = ({
  visible,
  onClose,
  activeFilters,
  setActiveFilters,
  versionFilterData,
}: PipelineVersionsFilterPanelProps) => {
  const tn = useI18nNamespace('PipelineVersions');

  const [editingFilters, setEditingFilters] = useSetState(activeFilters);
  useEffect(() => {
    setEditingFilters(activeFilters);
  }, [activeFilters, setEditingFilters]);

  const clearFilters = useCallback(() => {
    setEditingFilters(pipelineVersionsFiltersInitialState);
  }, [setEditingFilters]);

  return (
    <DrawerPanel
      className="filter-detail-panel"
      title={<TranslatedText text="filter" size="lg" />}
      mask
      maskClosable
      maskStyle={{ backgroundColor: 'transparent' }}
      onClose={onClose}
      visible={visible}
      footer={
        <>
          <Button key="cancel" onClick={onClose}>
            {tCommon('cancel')}
          </Button>
          <Button
            key="ok"
            type="primary"
            onClick={() => {
              setActiveFilters(editingFilters);
              onClose();
            }}>
            {tCommon('apply')}
          </Button>
        </>
      }>
      <Stack spacing="lg">
        <InputWithLabel
          label={tn('version_pound')}
          placeholder={tn('select_a_version')}
          input={
            <Select
              mode="multiple"
              onChange={(val) => setEditingFilters({ versionNumber: val })}
              optionData={versionFilterData.versionNumber.map((num) => ({
                label: num,
                value: num,
              }))}
              value={editingFilters.versionNumber}
            />
          }
        />

        <InputWithLabel
          label={tn('version_name')}
          placeholder={tn('select_a_name')}
          input={
            <Select
              mode="multiple"
              onChange={(val) => setEditingFilters({ name: val })}
              optionData={versionFilterData.name.map((num) => ({
                label: num,
                value: num,
              }))}
              value={editingFilters.name}
            />
          }
        />

        <InputWithLabel
          label={tn('saved_by')}
          placeholder={tn('select_a_user')}
          input={
            <Select
              mode="multiple"
              onChange={(val) => setEditingFilters({ createdBy: val })}
              optionData={versionFilterData.createdBy.map((num) => ({
                label: num,
                value: num,
              }))}
              value={editingFilters.createdBy}
            />
          }
        />

        <InputWithLabel
          label={tn('action_type')}
          placeholder={tn('select_an_action')}
          input={
            <Select
              mode="multiple"
              onChange={(val) => setEditingFilters({ actionType: val })}
              optionData={versionFilterData.actionType.map((num) => ({
                label: num,
                value: num,
              }))}
              value={editingFilters.actionType}
            />
          }
        />

        <InputWithLabel
          label={tn('saved_on')}
          input={
            <DateRangePicker
              onChange={(start, end) => setEditingFilters({ startDate: start?.format(), endDate: end?.format() })}
              startDate={editingFilters.startDate ? moment(editingFilters.startDate) : undefined}
              endDate={editingFilters.endDate ? moment(editingFilters.endDate) : undefined}
            />
          }
        />

        <ClearFilterButton onClear={clearFilters} />
      </Stack>
    </DrawerPanel>
  );
};

export default PipelineVersionsFilterPanel;
