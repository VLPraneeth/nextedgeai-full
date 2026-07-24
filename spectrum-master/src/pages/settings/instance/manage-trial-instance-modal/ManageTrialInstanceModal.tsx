//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button, Col, DatePicker, message, Modal, Row, Spin } from 'antd';
import moment, { Moment } from 'moment';
import { useCallback, useEffect, useMemo, useState } from 'react';

import InlineMessage from 'components/InlineMessage';
import Select from 'components/inputs/Select';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { InstanceState, setManageTrialInstanceId } from 'store/instances/slice';
import {
  EnhancedInstanceState,
  makeEnhancedInstanceState,
  useCurrentInstanceState,
} from 'store/instances/useCurrentInstanceState';
import { get, put } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { format, FULL_DATE_TIME, SHORT_DATE_DISPLAY_FORMAT } from 'utils/DateUtil';
import { tc, tNamespaced, numberFormat } from 'utils/i18nUtil';
import { makeUrl } from 'utils/UrlUtil';

import './ManageTrialInstanceModal.less';

const tn = tNamespaced('ManageTrialInstanceModal');

const recordIncreaseValues = [
  { value: '5000', label: numberFormat(5000) },
  { value: '10000', label: numberFormat(10000) },
  { value: '15000', label: numberFormat(15000) },
];

interface ExtendTrialRequest {
  extendedDate?: string;
  extendedRecordLimit?: string;
  instanceId: string;
}
// TODO: RTK-Query

export const ManageTrialInstanceModal = () => {
  const { refresh, id: currentinstanceId } = useCurrentInstanceState();
  const dispatch = useEnhancedDispatch();
  const { manageTrialInstanceId: instanceId } = useEnhancedSelector((state) => state.instance);
  const [instanceState, setInstanceState] = useState<EnhancedInstanceState>({} as EnhancedInstanceState);
  const [requestError, setError] = useState<string>();
  const [loading, setLoading] = useState(true);

  const [extendedRecordLimit, setExtendRecordLimit] = useState<string>();
  const [newEndDate, setNewEndDate] = useState<Moment>();

  /**
   * Fetch state for selected instance
   */
  const getInstanceState = useCallback(() => {
    setLoading(true);
    get<InstanceState>(makeUrl(DataUrlConstants.INSTANCE_STATE, { instanceId }))
      .then((resp) => {
        const enhancedState = makeEnhancedInstanceState(resp.data);
        setInstanceState(enhancedState);
        setLoading(false);
      })
      .catch((error) => {
        setError(tn('get_instance_error'));
      });
  }, [instanceId]);

  const extendTrial = useCallback(async () => {
    setLoading(true);
    // Set time to end of day so end date is inclusive
    const extendedDate = newEndDate?.utc().set({ hour: 23, minute: 59, second: 59 }).format(FULL_DATE_TIME);
    const payload: ExtendTrialRequest = { extendedRecordLimit, extendedDate, instanceId };

    try {
      await put<InstanceState>(makeUrl(DataUrlConstants.EXTEND_TRIAL_INSTANCE, null, payload));
      getInstanceState();
      setNewEndDate(undefined);
      setExtendRecordLimit(undefined);
      setLoading(false);
      setError('');
      message.success(tn('instance_updated'));
      if (instanceId === currentinstanceId && refresh) {
        refresh();
      }
    } catch {
      setLoading(false);
      setError(tn('update_failed'));
    }
  }, [newEndDate, extendedRecordLimit, instanceId, refresh, currentinstanceId, getInstanceState]);

  const closeModal = useCallback(() => {
    setInstanceState({} as EnhancedInstanceState);
    dispatch(setManageTrialInstanceId(''));
  }, [setInstanceState, dispatch]);

  function disabledDate(current: Moment | null) {
    if (!current) {
      return false;
    }
    // Can not select days before today and today
    return current && current < moment().endOf('day');
  }

  useEffect(() => {
    if (instanceId && !instanceState?.id) {
      getInstanceState();
    }
  }, [instanceId, instanceState, getInstanceState]);

  const footer = useMemo(
    () => (
      <>
        <Button onClick={closeModal}>{tc('close')}</Button>
        <Button disabled={!extendedRecordLimit && !newEndDate} type="primary" onClick={extendTrial}>
          {tc('save')}
        </Button>
      </>
    ),
    [closeModal, extendedRecordLimit, newEndDate, extendTrial]
  );

  const onChange = useCallback((value: string) => setExtendRecordLimit(value.toString()), [setExtendRecordLimit]);

  return (
    <Modal
      className="manage-trial-modal"
      centered
      destroyOnClose
      footer={footer}
      onCancel={closeModal}
      title={tn('manage_trial_instance')}
      visible={!!instanceId}>
      <Spin spinning={loading}>
        <InlineMessage title={requestError} type="error">
          {requestError}
        </InlineMessage>
        <h3 className="manage-trial-modal__heading">{tn('records')}</h3>
        <Row>
          <Col span={6}>
            <label>{tn('used')}</label>
            <div className="manage-trial-modal__attribute">
              {numberFormat(+instanceState.recordLimit - instanceState.numberOfRecordsLeft)}
            </div>
          </Col>
          <Col span={6}>
            <label>{tn('remaining')}</label>
            <div className="manage-trial-modal__attribute">{numberFormat(instanceState?.numberOfRecordsLeft)}</div>
          </Col>
          <Col span={6}>
            <label>{tn('max')}</label>
            <div className="manage-trial-modal__attribute">{numberFormat(instanceState.recordLimit)}</div>
          </Col>
          <Col span={6}>
            <label>{tn('increase_records')}</label>
            <Select onChange={onChange} optionData={recordIncreaseValues} value={extendedRecordLimit} />
          </Col>
        </Row>

        <h3 className="manage-trial-modal__heading">{tn('duration')}</h3>
        <Row>
          <Col span={9}>
            <label>{tn('days_remaining')}</label>
            <div className="manage-trial-modal__attribute">{instanceState.trialDaysLeft || '<1'}</div>
          </Col>
          <Col span={9}>
            <label>{tn('end_date')}</label>
            <div className="manage-trial-modal__attribute">
              {format(moment(instanceState.expiryDate).utc() ?? '', SHORT_DATE_DISPLAY_FORMAT)}
            </div>
          </Col>
          <Col span={6}>
            <label>{tn('new_end_date')}</label>
            <DatePicker
              allowClear={false}
              className={'date-picker'}
              disabledDate={disabledDate}
              format={SHORT_DATE_DISPLAY_FORMAT}
              onChange={(date) => {
                setNewEndDate(date ?? undefined);
              }}
              showTime={false}
              value={newEndDate?.startOf('day')}
            />
          </Col>
        </Row>
      </Spin>
    </Modal>
  );
};
