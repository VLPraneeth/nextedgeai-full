import { Button, DatePicker } from 'antd';
import { RangePickerPresetRange, RangePickerValue } from 'antd/lib/date-picker/interface';
import Input from 'antd/lib/input';
import { useCallback, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import ClearFilterButton from 'components/filter-components/ClearFilterButton';
import { useI18nContext } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { Moment } from 'hooks/moment';
import useFiltersInQueryParams from 'hooks/useFiltersInQueryParams';
import { LONG_TIME_FORMAT, SHORT_DATE_TIME_FORMAT_WITH_SEC } from 'utils/DateUtil';

import { LogsFilterValues } from './PipelineLogsTable';

const { RangePicker } = DatePicker;

export interface PipelineLogsFilterPanelProps {
  visible?: boolean;
  onClose: () => void;
}

const PipelineLogsFilterPanel = ({ visible, onClose }: PipelineLogsFilterPanelProps) => {
  const { tn, tc } = useI18nContext();

  const onCalendarChange = useCallback((rangeValue: RangePickerValue | RangePickerPresetRange) => {
    if (Array.isArray(rangeValue) && rangeValue.length === 2) {
      setFormFilters((prev) => ({
        ...prev,
        startDate: rangeValue[0] as Moment,
        endDate: rangeValue[1] as Moment,
      }));
    }
  }, []);

  const handleParamsChange = useCallback((updatedQueryParams) => {
    // When the query params changed due to browser navigation we fetch data
    // based on the new params. We also need to update the local form filters so
    // the inputs reflect the new filter values.
    setFormFilters(updatedQueryParams);
  }, []);

  const { filterValues: activeFilters, defaultFilters, updateQueryParams } = useFiltersInQueryParams<LogsFilterValues>(
    {
      syncariRecordId: '',
    },
    handleParamsChange
  );

  const [formFilters, setFormFilters] = useState<LogsFilterValues>(activeFilters);

  const applyFilters = useCallback(() => {
    updateQueryParams(formFilters);
    onClose();
  }, [formFilters, onClose, updateQueryParams]);

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
          label={tn('syncari_id')}
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

        <ClearFilterButton onClear={resetFilters} />
      </Stack>
    </DrawerPanel>
  );
};

export default PipelineLogsFilterPanel;
