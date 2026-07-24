import { Button, DatePicker } from 'antd';
import { RangePickerPresetRange, RangePickerValue } from 'antd/lib/date-picker/interface';
import Input from 'antd/lib/input';
import { useCallback, useMemo, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import ClearFilterButton from 'components/filter-components/ClearFilterButton';
import { useI18nContext } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Stack } from 'components/layout';
import useUserLocalMoment, { Moment } from 'hooks/moment';
import useFiltersInQueryParams from 'hooks/useFiltersInQueryParams';
import useSyncariEntities from 'hooks/useSyncariEntities';
import { ALL_ENTITIES_VALUE, ALL_OPERATIONS_VALUE, TransactionOperation } from 'store/transactions';
import { LONG_TIME_FORMAT, SHORT_DATE_TIME_FORMAT_WITH_SEC } from 'utils/DateUtil';
import { createUniqueEntityTitle } from 'utils/FieldUtil';

import { LOGS_DEFAULT_DAYS_RANGE } from '../TransactionList';
import { DraftTransactionsParams } from '../types';

const { RangePicker } = DatePicker;

const operationSelectStyle = { minWidth: '12rem', backgroundColor: 'white' };

const entitySelectStyle = { ...operationSelectStyle, minWidth: '18rem' };

export const Operations = [
  'create',
  'update',
  'delete',
  'external_create',
  'external_delete',
  'external_update',
  'syncari_delete',
  'disconnect',
  'merge',
  'merge_report_only',
  'merge_skip',
] as const;

export interface LogsFilterPanelProps {
  visible?: boolean;
  onClose: () => void;
  onFilterUpdate: () => void;
}

const LogsFilterPanel = ({ visible, onClose, onFilterUpdate }: LogsFilterPanelProps) => {
  const { tn, tc } = useI18nContext();
  const moment = useUserLocalMoment();

  const { data: entities } = useSyncariEntities();

  const entityOptions = useMemo(() => {
    return [
      {
        label: tn(ALL_ENTITIES_VALUE),
        value: ALL_ENTITIES_VALUE,
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
        label: tn(ALL_OPERATIONS_VALUE),
        value: ALL_OPERATIONS_VALUE,
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

  const {
    filterValues: activeFilters,
    defaultFilters,
    updateQueryParams,
  } = useFiltersInQueryParams<DraftTransactionsParams>(
    {
      entityName: ALL_ENTITIES_VALUE,
      operation: ALL_OPERATIONS_VALUE,
      syncariId: '',
      startDate: moment().subtract(LOGS_DEFAULT_DAYS_RANGE, 'days').startOf('day'),
    },
    handleParamsChange
  );

  const [formFilters, setFormFilters] = useState<DraftTransactionsParams>(activeFilters);

  const applyFilters = useCallback(() => {
    updateQueryParams(formFilters);
    onFilterUpdate();
    onClose();
  }, [formFilters, onClose, onFilterUpdate, updateQueryParams]);

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
              value={formFilters.entityName}
              onChange={(newValue) => {
                setFormFilters((prev) => ({
                  ...prev,
                  entityName: newValue,
                }));
              }}
              optionData={entityOptions}
            />
          }
        />

        <InputWithLabel
          label={tn('operations_label')}
          input={
            <Select
              style={operationSelectStyle}
              dropdownMatchSelectWidth
              value={formFilters.operation}
              onChange={(value) => {
                setFormFilters((prev) => ({
                  ...prev,
                  operation: value !== ALL_OPERATIONS_VALUE ? (value as TransactionOperation) : undefined,
                }));
              }}
              showSearch
              optionData={operationOptions}
            />
          }
        />

        <InputWithLabel
          label={tn('syncari_record_id')}
          input={
            <Input
              placeholder={tn('enter_record_id')}
              value={formFilters.syncariId}
              onChange={(evt) => {
                const { value } = evt.target;

                setFormFilters((prev) => ({
                  ...prev,
                  syncariId: value || '',
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
              ranges={{
                Now: [moment(), moment()],
              }}
            />
          }
        />

        <ClearFilterButton onClear={resetFilters} />
      </Stack>
    </DrawerPanel>
  );
};

export default LogsFilterPanel;
