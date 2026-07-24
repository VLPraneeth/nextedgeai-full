//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import * as React from 'react';

import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { ValidateStatuses } from 'components/inputs/types';
import Modal from 'components/Modal';
import useUserLocalMoment from 'hooks/moment';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { runFieldTests, setTestPanelView, showRunTest } from 'store/test/actions';
import {
  selectRunFieldTestsErrorMessage,
  selectRunFieldTestsStatus,
  selectTestRunTestIds,
  selectTestRunVisible,
} from 'store/test/selectors';
import { PipelineContextTypes, TestPanelView } from 'store/test/types';
import AppConstants from 'utils/AppConstants';
import { LONG_DATETIME_FORMAT_WITH_TZ } from 'utils/DateUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('TestRunSimulatedTitleModal');
const { FETCH_STATUS } = AppConstants;

export interface TestRunSimulatedTitleModalProps {
  pipelineId: string;
  pipelineContext: PipelineContextTypes;
  errorMessage?: string;
}

const TestRunSimulatedTitleModal = ({ pipelineContext, pipelineId }: TestRunSimulatedTitleModalProps) => {
  const visible = useSelector(selectTestRunVisible);
  const testIds = useSelector(selectTestRunTestIds);
  const dispatch = useDispatch();
  const [runName, setRunName] = useState('');
  const runFieldTestsStatus = useSelector(selectRunFieldTestsStatus);
  const errorMessage = useSelector(selectRunFieldTestsErrorMessage);
  const previousRunFieldTestsStatus = usePreviousValue(runFieldTestsStatus);
  const previousVisible = usePreviousValue(visible);
  const moment = useUserLocalMoment();
  const [validation, setValidation] = useState<Record<string, string>>({});

  const close = useCallback(() => {
    dispatch(showRunTest(false));
  }, [dispatch]);

  useEffect(() => {
    if (
      previousRunFieldTestsStatus === FETCH_STATUS.LOADING &&
      runFieldTestsStatus === FETCH_STATUS.SUCCESS &&
      !errorMessage
    ) {
      close();
      dispatch(setTestPanelView(TestPanelView.SIMULATED_RESULTS));
    }
  }, [previousRunFieldTestsStatus, runFieldTestsStatus, close, errorMessage, dispatch]);

  useEffect(() => {
    if (!previousVisible && visible) {
      setRunName(moment().format(LONG_DATETIME_FORMAT_WITH_TZ).trim());
    } else if (previousVisible && !visible) {
      setValidation({});
    }
  }, [previousVisible, visible, moment]);

  const formValidate = () => {
    if (!runName) {
      setValidation({
        ...validation,
        runNameStatus: ValidateStatuses.ERROR,
        runNameHelp: tc('cannot_be_empty', { name: tn('test_run_name') }),
      });
      return false;
    }
    return true;
  };

  const run = (evt: React.FormEvent) => {
    evt?.target && evt.preventDefault();
    if (!formValidate()) {
      return;
    }
    dispatch(
      runFieldTests({
        name: runName,
        pipelineContext,
        fieldPipelineId: pipelineId,
        testIds,
      })
    );
  };

  const onTextChange = (evt: React.ChangeEvent<HTMLInputElement>) => setRunName(evt.target.value);

  return (
    <Modal
      title={tn('title', { count: testIds?.length })}
      className="share-fragment-modal"
      centered
      visible={visible}
      footer={
        <>
          <Button key="cancel" onClick={close}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" onClick={run}>
            {tn('run')}
          </Button>
        </>
      }
      onOk={() => close()}
      onCancel={() => close()}
      destroyOnClose>
      <div className="content-container">
        {errorMessage && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
            {errorMessage}
          </InlineMessage>
        )}
        <form onSubmit={run}>
          <InputWithLabel
            name="runName"
            datatype="string"
            label={tn('test_run_name')}
            value={runName}
            onChange={onTextChange}
            validateStatus={validation?.runNameStatus}
            help={validation?.runNameHelp}
          />
        </form>
      </div>
    </Modal>
  );
};

export default TestRunSimulatedTitleModal;
