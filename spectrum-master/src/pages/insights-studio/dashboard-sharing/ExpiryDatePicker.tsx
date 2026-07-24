import { ICellEditor, ICellEditorParams } from 'ag-grid-community';
import { DatePicker, message } from 'antd';
import moment, { Moment } from 'moment';
import { forwardRef, useImperativeHandle } from 'react';

import Can from 'components/Can';
import { useUpdateExpiryMutation } from 'store/insights-studio';
import { disablePastDate, disablePastTime, FULL_DATE_TIME, SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { AllPermissions } from 'utils/PermissionsConstants';
export interface ExpiryDatePickerRef extends Omit<ICellEditor, 'getValue'> {
  getValue: () => string;
}
export interface ExpiryDatePickerParams extends Omit<ICellEditorParams, 'value'> {
  value: string;
}

export const ExpiryDatePicker = forwardRef<ExpiryDatePickerRef, ExpiryDatePickerParams>(
  ({ value, data, colDef }, ref) => {
    useImperativeHandle(ref, () => ({
      getValue: () => {
        return value || '';
      },
    }));

    const [updateExpiry] = useUpdateExpiryMutation();

    function handleOk(date: Moment | null) {
      if (date) {
        updateExpiry({
          sharedItemId: data?.sharedItemId,
          expiryDate: moment(date)?.utc().format(FULL_DATE_TIME),
        })
          .unwrap()
          .catch((error) => message.error(getRtkQueryErrorMessage(error)));
      }
    }

    return (
      <Can permission={AllPermissions.UPDATE_SHARED_DASHBOARD_EXPIRY}>
        <DatePicker
          key={`${data.sharedItemId}${colDef?.field}`}
          className="sharing-details__expiry-date"
          defaultValue={data?.expiryDate ? moment(data?.expiryDate) : undefined}
          disabledDate={disablePastDate}
          disabledTime={disablePastTime}
          format={SHORT_DATE_TIME_FORMAT}
          showTime={{
            format: 'HH:mm',
          }}
          showToday={false}
          allowClear={false}
          onOk={handleOk}
        />
      </Can>
    );
  }
);
