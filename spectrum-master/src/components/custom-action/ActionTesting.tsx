//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { ICellEditor, ICellEditorParams } from 'ag-grid-community';
import { message } from 'antd';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { cloneDeep } from 'lodash/fp';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useState } from 'react';

import AgTable from 'components/AgTable';
import Button from 'components/Button';
import FieldTypeBadge from 'components/FieldTypeBadge';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage from 'components/InlineMessage';
import { Divider, HStack, Stack } from 'components/layout';
import { FieldDataType } from 'components/types';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import { makeCustomActionPayload } from 'pages/sync-studio/action-studio/CustomAction.util';
import { validateCustomAction, testCustomAction } from 'store/custom-action/thunks';
import { CustomActionTestingResponse } from 'store/custom-action/types';
import { useHttpCustomSypapseTestMutation } from 'store/custom-synapse/http/api';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';
import AppConstants from 'utils/AppConstants';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { humanize } from 'utils/StringUtil';

import ActionRequestResponse from './ActionRequestResponse';
import { CustomAction } from './types';

import './ActionTesting.less';

const DefaultColDef = { flex: 1 };

export interface VariableEditorRef extends Omit<ICellEditor, 'getValue'> {
  getValue: () => string;
}
export interface VariableEditorParams extends Omit<ICellEditorParams, 'value'> {
  value: string;
}

export const VariableEditor = forwardRef<VariableEditorRef, VariableEditorParams>(({ value, data, colDef }, ref) => {
  useImperativeHandle(ref, () => ({
    getValue: () => {
      return value || '';
    },
  }));
  const { dataType } = data;
  return (
    <>
      <FieldTypeBadge dataType={dataType as FieldDataType} description={humanize(dataType)} disableTooltip />
      <span>{data.displayName || data.name}</span>
    </>
  );
});

export interface ActionTestingValue {
  variableValues?: VariableValue[];
  requestResponse?: CustomActionTestingResponse;
}

export interface VariableValue {
  id: string;
  name?: string;
  displayName?: string;
  value?: string;
}
export interface ActionTestingProps {
  className?: string;
  customAction?: CustomAction;
  defaultValue?: ActionTestingValue;
  onChange?: (value: ActionTestingValue) => void;
  readOnly?: boolean;
  httpSynapseEntity?: HTTPCustomSynapseEntity;
}

export const ActionTesting = ({
  className,
  customAction,
  defaultValue,
  onChange,
  readOnly,
  httpSynapseEntity,
}: ActionTestingProps) => {
  const { tn } = useI18nContext();
  const dispatch = useDispatch();
  const [variableValues, setVariableValues] = useState<VariableValue[]>(() => {
    const vars = customAction?.actionConfiguration?.variables?.map((variable) => {
      return {
        id: ObjectID.generate(),
        name: variable.name,
        displayName: variable.displayName,
        dataType: variable.dataType,
        value: defaultValue?.variableValues?.find((val) => val.name === variable.name)?.value,
      };
    });
    return vars?.length ? vars : [{ id: ObjectID.generate(), dataType: AppConstants.INPUT_TYPE.STRING }];
  });
  const [runTestLabel, setRunTestLabel] = useState(tn('run_test'));
  const [validationErrorMsg, setValidateErrorMsg] = useState('');
  const [testingErrorMsg, setTestingErrorMsg] = useState('');
  const [requestResponse, setRequestResponse] = useState<CustomActionTestingResponse | undefined>(
    defaultValue?.requestResponse
  );

  const [
    testHttpCustomSynapseEntity,
    { data: enitityTestResponse, isLoading: isTesting },
  ] = useHttpCustomSypapseTestMutation();

  const onRowEditingStopped = useCallback((evt: any) => {
    if (evt?.data) {
      const { data } = evt;
      setVariableValues((prev) => prev?.map((variableValue) => (variableValue.id === data.id ? data : variableValue)));
    }
  }, []);

  useEffect(() => {
    onChange?.({
      variableValues,
      requestResponse,
    });
  }, [onChange, requestResponse, variableValues]);

  const runTest = useCallback(() => {
    if (httpSynapseEntity) {
      testHttpCustomSynapseEntity({
        ...httpSynapseEntity,
        metadataId: httpSynapseEntity.metaId,
        body: httpSynapseEntity.body ? JSON.stringify(httpSynapseEntity.body) : '',
        variableValues: variableValues?.map((val) => ({
          name: val.name,
          value: val.value,
        })),
      })
        .unwrap()
        .then((result) => setRequestResponse(result))
        .catch((err) => message.error(getRtkQueryErrorMessage(err)));
    } else if (customAction) {
      setValidateErrorMsg('');
      setTestingErrorMsg('');
      setRunTestLabel(tn('validation_in_progress'));
      const customActionPayload = makeCustomActionPayload(customAction);
      dispatch(validateCustomAction(customActionPayload)).then((result) => {
        if (result.meta.requestStatus === 'rejected') {
          setValidateErrorMsg(result.payload?.message);
          setRunTestLabel(tn('run_test'));
        } else {
          setRunTestLabel(tn('testing_in_progress'));
          dispatch(
            testCustomAction({
              ...customActionPayload,
              variableValues: variableValues?.map((val) => ({
                name: val.name,
                value: val.value,
              })),
            })
          ).then((result) => {
            if (result.meta.requestStatus === 'rejected') {
              setTestingErrorMsg(result.payload?.message);
            } else {
              setRequestResponse(result.payload);
            }
            setRunTestLabel(tn('run_test'));
          });
        }
      });
    }
  }, [customAction, dispatch, tn, variableValues, httpSynapseEntity, testHttpCustomSynapseEntity]);

  const clearAll = useCallback(() => {
    setVariableValues((prev) => {
      return prev.map((variableValue) => ({ ...variableValue, value: '' }));
    });
    setRequestResponse(undefined);
    setValidateErrorMsg('');
    setTestingErrorMsg('');
  }, []);

  const columns = useMemo(() => {
    return [
      {
        headerName: tn('variable'),
        field: 'displayName',
        editable: !readOnly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        cellEditorFramework: VariableEditor,
        cellRendererFramework: VariableEditor,
        resizable: true,
      },
      {
        headerName: tn('test_value'),
        field: 'value',
        editable: !readOnly,
        suppressKeyboardEvent: () => true,
        suppressMovable: true,
        resizable: true,
      },
    ];
  }, [tn, readOnly]);

  // AgTable mutate the object and states are readonly hence cloning it here before passing to AgTable
  const rowVariables = useMemo(() => cloneDeep(variableValues), [variableValues]);

  return (
    <Stack className={cx('synri-action-testing', className)} spacing="md">
      {!!validationErrorMsg && (
        <InlineMessage type={'error'} className="synri-action-testing-error-msg" title={validationErrorMsg}>
          {validationErrorMsg}
        </InlineMessage>
      )}
      <AgTable
        defaultColDef={DefaultColDef}
        suppressCellSelection
        columnDefs={columns}
        rowData={rowVariables}
        editType="fullRow"
        stopEditingWhenGridLosesFocus
        singleClickEdit
        onRowEditingStopped={onRowEditingStopped}
      />
      <HStack justify="end">
        <Button onClick={clearAll} disabled={readOnly}>
          <TranslatedText text="clear_all" />
        </Button>
        <Button
          type="primary"
          loading={tn('run_test') !== runTestLabel || isTesting}
          onClick={runTest}
          disabled={readOnly}>
          {runTestLabel}
        </Button>
      </HStack>
      <Divider />
      <ActionRequestResponse errorMsg={testingErrorMsg} requestResponse={requestResponse || enitityTestResponse} />
    </Stack>
  );
};

export default withI18n(ActionTesting, 'ActionSetup');
