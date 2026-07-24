import { Button, DatePicker } from 'antd';
import { RangePickerPresetRange, RangePickerValue } from 'antd/lib/date-picker/interface';
import Input from 'antd/lib/input';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { getConnectors } from 'actions/connectorActions';
import DrawerPanel from 'components/DrawerPanel';
import ClearFilterButton from 'components/filter-components/ClearFilterButton';
import { useI18nContext } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Stack } from 'components/layout';
import { Moment } from 'hooks/moment';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useFiltersInQueryParams from 'hooks/useFiltersInQueryParams';
import useSyncariEntities from 'hooks/useSyncariEntities';
import { EMPTY_ARRAY } from 'store/constants';
import { SyncErrorsParams } from 'store/logs/thunks';
import { TransactionOperation } from 'store/transactions';
import { LONG_TIME_FORMAT, SHORT_DATE_TIME_FORMAT_WITH_SEC } from 'utils/DateUtil';
import { createUniqueEntityTitle } from 'utils/FieldUtil';

const { RangePicker } = DatePicker;

const operationSelectStyle = { minWidth: '12rem', backgroundColor: 'white' };

const entitySelectStyle = { ...operationSelectStyle, minWidth: '18rem' };

const ALL_VALUE = 'all';

export const defaultSyncErrorFilters = {
  connectorName: ALL_VALUE,
  operation: ALL_VALUE,
  syncariEntityName: ALL_VALUE,
  entityId: '',
  syncariRecordId: '',
};

export const Operations = [
  'create',
  'update',
  'delete',
  'external_delete',
  'syncari_delete',
  'disconnect',
  'merge',
  'merge_report_only',
] as const;

export interface LogsFilterPanelProps {
  visible?: boolean;
  onClose: () => void;
  onFilterUpdate: () => void;
}

const SyncErrorsFilterPanel = ({ visible, onClose, onFilterUpdate }: LogsFilterPanelProps) => {
  const { tn, tc } = useI18nContext();
  const dispatch = useEnhancedDispatch();

  const { data: entities } = useSyncariEntities();

  const connectors = useEnhancedSelector((state) => state.connector.connectors);

  useEffect(() => {
    if (!connectors) {
      dispatch(getConnectors());
    }
  }, [dispatch, connectors]);

  const entityOptions = useMemo(() => {
    return [
      {
        label: tn(ALL_VALUE),
        value: ALL_VALUE,
      },
    ].concat(
      entities.map((entity) => {
        return {
          label: createUniqueEntityTitle(entity.displayName, entity.apiName),
          value: entity.apiName,
        };
      })
    );
  }, [entities, tn]);

  const operationOptions = useMemo(
    () => [
      {
        label: tn(ALL_VALUE),
        value: ALL_VALUE,
      },
      ...Operations.map((operation) => ({
        label: tn(`operations.${operation}`),
        value: operation,
      })),
    ],
    [tn]
  );

  const onCalendarChange = useCallback((rangeValue: RangePickerValue | RangePickerPresetRange) => {
    if (Array.isArray(rangeValue) && rangeValue.length === 2) {
      setFormFilters((prev) => ({
        ...prev,
        startDate: rangeValue[0] as Moment,
        endDate: rangeValue[1] as Moment,
      }));
    }
  }, []);

  const handleParamsChange = useCallback((updatedQueryParams: any) => {
    // When the query params changed due to browser navigation we fetch data
    // based on the new params. We also need to update the local form filters so
    // the inputs reflect the new filter values.
    setFormFilters(updatedQueryParams);
  }, []);

  const { filterValues: activeFilters, defaultFilters, updateQueryParams } = useFiltersInQueryParams<SyncErrorsParams>(
    defaultSyncErrorFilters,
    handleParamsChange
  );

  const [formFilters, setFormFilters] = useState<SyncErrorsParams>(activeFilters);

  const applyFilters = useCallback(() => {
    updateQueryParams(formFilters);
    onFilterUpdate();
    onClose();
  }, [formFilters, onClose, onFilterUpdate, updateQueryParams]);

  const connectorOptions = connectors
    ? connectors.map((c) => ({
        label: c.name,
        value: c.name,
      }))
    : EMPTY_ARRAY;

  const resetFilters = useCallback(() => {
    setFormFilters(defaultFilters);
  }, [defaultFilters]);

  const footer = (
    <>
      <Button onClick={onClose}>{tc('cancel')}</Button>
      <Button onClick={applyFilters} type="primary">
        {tc('apply')}
      </Button>
    </>
  );

  return (
    <DrawerPanel
      className="logs-filter-panel"
      footer={footer}
      mask
      maskClosable
      width={390}
      maskStyle={{ backgroundColor: 'transparent' }}
      onClose={onClose}
      title={tc('filter_no_ellipse')}
      visible={visible}>
      <Stack>
        <InputWithLabel
          label={tc('entity')}
          input={
            <Select
              showSearch
              dropdownMatchSelectWidth
              style={entitySelectStyle}
              value={formFilters.syncariEntityName}
              onChange={(newValue) => {
                setFormFilters((prev) => ({
                  ...prev,
                  syncariEntityName: newValue,
                }));
              }}
              optionData={entityOptions}
            />
          }
        />

        <InputWithLabel
          label={tn('synapse')}
          input={
            <Select
              onChange={(connectorName) => {
                setFormFilters((prev) => ({
                  ...prev,
                  connectorName,
                }));
              }}
              value={formFilters.connectorName}
              showSearch
              optionData={[
                {
                  label: tn('all'),
                  value: ALL_VALUE,
                },
                ...connectorOptions,
              ]}
            />
          }
        />

        <InputWithLabel
          label={tn('syncari_record_id')}
          input={
            <Input
              placeholder={tn('enter_record_id')}
              value={formFilters.syncariRecordId}
              onChange={(evt) => {
                const { value } = evt.target;

                setFormFilters((prev) => ({
                  ...prev,
                  syncariRecordId: value || '',
                }));
              }}
            />
          }
        />

        <InputWithLabel
          label={tc('date_range')}
          input={
            <RangePicker
              className="pipeline-logs__range-picker"
              showTime={{ format: LONG_TIME_FORMAT }}
              format={SHORT_DATE_TIME_FORMAT_WITH_SEC}
              value={[formFilters.startDate, formFilters.endDate]}
              placeholder={[tc('start_time'), tc('end_time')]}
              onCalendarChange={onCalendarChange}
              onOk={onCalendarChange}
            />
          }
        />

        <InputWithLabel
          label={tn('operation')}
          input={
            <Select
              style={operationSelectStyle}
              dropdownMatchSelectWidth
              value={formFilters.operation}
              onChange={(value) => {
                setFormFilters((prev) => ({
                  ...prev,
                  operation: value !== ALL_VALUE ? (value as TransactionOperation) : undefined,
                }));
              }}
              showSearch
              optionData={operationOptions}
            />
          }
        />

        <ClearFilterButton onClear={resetFilters} />
      </Stack>
    </DrawerPanel>
  );
};

export default SyncErrorsFilterPanel;
