//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Tooltip } from 'antd';
import cx from 'classnames';
import { uniqBy } from 'lodash';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import Collapse, { Panel } from 'components/Collapse';
import DrawerPanel from 'components/DrawerPanel';
import Fieldset from 'components/Fieldset';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import MultiFieldValues, { MultiValues } from 'components/inputs/MultiFieldValues';
import { TagValueModel } from 'components/inputs/Tag';
import { ValidateStatuses, Validation } from 'components/inputs/types';
import { PipelineErrorPanel } from 'components/pipeline-error-panel/PipelineErrorPanel';
import TabPanelSpin from 'components/TabPanelSpin';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import {
  selectFieldPipelineCoreNode,
  selectFieldPipelineSinkNodes,
  selectFieldPipelineSourceNodes,
} from 'store/pipeline/selectors';
import {
  getFieldPicklistValues,
  getFieldPipelineTest,
  resetCreateTest,
  saveFieldPipelineTest,
  showCreateTest,
} from 'store/test/actions';
import {
  selectCreateTestVisible,
  selectEditTestId,
  selectPicklistValues,
  selectSaveTestErrorMessage,
  selectSaveTestStatus,
  selectTest,
} from 'store/test/selectors';
import { NodeDetailsResult, PipelineContextTypes } from 'store/test/types';
import { selectUserEmail } from 'store/user/selectors';
import { ENTITY_DRAWER_HEIGHT_OFFSET, FIELD_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './test-styles/TestAddUpdateSimulatedPanel.less';

const { FETCH_STATUS } = AppConstants;

const tn = tNamespaced('TestAddUpdateSimulatedPanel');
const tf = tNamespaced('FragmentModal');

export interface TestAddUpdateSimulatedPanelProps {
  className?: string;
  errorMessage?: string;
  pipelineContext: PipelineContextTypes;
  pipelineId: string;
  validate?: () => void;
  validating?: boolean;
}

const createFakeNode = (nodeResult: NodeDetailsResult) => {
  return {
    id: nodeResult.nodeId,
    label: nodeResult.nodeName,
    invalidNode: true,
  };
};

// TODO: Types
const toMultiValueFormValues = (result: any[], type: string) => {
  let toValue: any = {};
  result.forEach((node: any) => {
    const { nodeId } = node;
    toValue[nodeId] = toValue[nodeId] || {};
    toValue[nodeId].value = toValue[nodeId].value || {};
    toValue[nodeId].type = type;
    toValue[nodeId].nodeId = nodeId;
    toValue[nodeId].value[node.apiName] = {
      id: node.apiName,
      datatype: node.dataType,
      label: node.displayName,
      isMultiValueField: node.isMultiValueField,
      value: node.value,
    };
  });

  return toValue;
};

const toSaveValues = (formValues: Record<string, any>, nodes: any[], type: string) => {
  let values: any = [];
  Object.values(formValues)
    ?.filter((formValue1) => formValue1?.type === type)
    .forEach((formValue2) => {
      values = values.concat(
        Object.values(formValue2.value).map((value: any) => {
          return {
            ...value,
            nodeId: formValue2.nodeId,
            nodeName: nodes.find((node) => node.id === formValue2.nodeId)?.label,
          };
        })
      );
    });
  return values;
};

const TestAddUpdateSimulatedPanel = ({
  className,
  errorMessage,
  pipelineContext,
  pipelineId,
  validate,
  validating,
}: TestAddUpdateSimulatedPanelProps) => {
  const [formValues, setFormValues] = useState<Record<string, any>>({});
  const dispatch = useDispatch();
  const sinkNodes = useSelector(selectFieldPipelineSinkNodes);
  const sourceNodes = useSelector(selectFieldPipelineSourceNodes);
  const coreNode = useSelector(selectFieldPipelineCoreNode);
  const picklistValues = useSelector(selectPicklistValues);
  const userEmail = useSelector(selectUserEmail);
  const visible = useSelector(selectCreateTestVisible);
  const previousVisible = usePreviousValue(visible);
  const saveTestErrorMessage = useSelector(selectSaveTestErrorMessage);
  const saveFieldPipelineTestStatus = useSelector(selectSaveTestStatus);
  const previousSaveFieldPipelineTestStatus = usePreviousValue(saveFieldPipelineTestStatus);
  const editTestId = useSelector(selectEditTestId);
  const editTest = useSelector(selectTest);
  const [validation, setValidation] = useState<Validation>({});
  const [fakeNodesCount, setFakeNodesCount] = useState(0);

  const [inputNodes, setInputNodes] = useState<any[]>([]);
  const [expectedResultNodes, setExpectedResultNodes] = useState<any[]>([]);
  const contentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!previousVisible && visible) {
      // Once get the field picklist values
      sourceNodes.forEach((source: any) => {
        dispatch(getFieldPicklistValues({ pipelineContext, fieldPipelineId: pipelineId, nodeId: source.id }));
      });
      sinkNodes.forEach((sink: any) => {
        dispatch(getFieldPicklistValues({ pipelineContext, fieldPipelineId: pipelineId, nodeId: sink.id }));
      });
      if (sinkNodes.length <= 0) {
        dispatch(getFieldPicklistValues({ pipelineContext, fieldPipelineId: pipelineId, nodeId: coreNode.id }));
      }

      validate?.();

      setInputNodes(sourceNodes);
      setExpectedResultNodes([...(sinkNodes?.length > 0 ? sinkNodes : [coreNode])]);
    }
  }, [sourceNodes, sinkNodes, coreNode, pipelineContext, pipelineId, dispatch, visible, previousVisible, validate]);

  useEffect(() => {
    editTestId && dispatch(getFieldPipelineTest({ pipelineContext, fieldPipelineId: pipelineId, testId: editTestId }));
  }, [dispatch, pipelineContext, pipelineId, editTestId]);

  useEffect(() => {
    if (editTest?.testData) {
      // Create fake nodes for values with missing nodes
      const { testData } = editTest;
      const validNodeIds = validNodes.map((node) => node?.id);

      const fakeInputNodes = uniqBy(
        testData.input?.filter((nodeResult) => !validNodeIds.includes(nodeResult.nodeId)).map(createFakeNode),
        'id'
      );

      const fakeExpectedResultNodes = uniqBy(
        testData.expectedResult?.filter((nodeResult) => !validNodeIds.includes(nodeResult.nodeId)).map(createFakeNode),
        'id'
      );

      setFakeNodesCount(fakeInputNodes.length + fakeExpectedResultNodes.length);
      setInputNodes([...sourceNodes, ...fakeInputNodes]);
      setExpectedResultNodes([...expectedResultNodes, ...fakeExpectedResultNodes]);

      setFormValues({
        ...editTest,
        ...toMultiValueFormValues(editTest.testData.expectedResult || [], 'expectedResult'),
        ...toMultiValueFormValues(editTest.testData.input, 'input'),
      });
    }
    // Note: We are updating the expected result nodes so we do not want that
    // in our dependency
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editTest, coreNode, sourceNodes, sinkNodes]);

  // Clear form values when the window closes
  useEffect(() => {
    if (previousVisible && !visible) {
      setFormValues({});
      dispatch(resetCreateTest());
      setValidation({});
      setInputNodes([]);
      setExpectedResultNodes([]);
      setFakeNodesCount(0);
    }
  }, [previousVisible, visible, dispatch]);

  const validNodes = useMemo(() => [...sourceNodes, ...(sinkNodes?.length > 0 ? sinkNodes : [coreNode])], [
    sourceNodes,
    sinkNodes,
    coreNode,
  ]);

  const close = useCallback(() => {
    setFormValues({});
    dispatch(showCreateTest(false));
  }, [dispatch]);

  useEffect(() => {
    if (
      previousSaveFieldPipelineTestStatus === FETCH_STATUS.LOADING &&
      saveFieldPipelineTestStatus === FETCH_STATUS.SUCCESS &&
      !saveTestErrorMessage
    ) {
      close();
    }
  }, [previousSaveFieldPipelineTestStatus, saveFieldPipelineTestStatus, saveTestErrorMessage, close]);

  const onTextChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
    setFormValues({
      ...formValues,
      [evt.target.name]: evt.target.value,
    });
  };

  const onTagChange = (tags: TagValueModel) => {
    setFormValues({
      ...formValues,
      tags,
    });
  };

  const transformTestData = useCallback((oValue: MultiValues, nodeId: string) => {
    const { label: displayName, id: apiName, datatype: dataType, value, nodeName } = oValue;
    return {
      displayName,
      apiName,
      dataType,
      value,
      nodeId,
      nodeName,
    };
  }, []);

  const formValidate = () => {
    if (!formValues?.displayName) {
      setValidation({
        ...validation,
        displayNameStatus: ValidateStatuses.ERROR,
        displayNameHelp: tc('cannot_be_empty', { name: tn('display_name') }),
      });
      return false;
    }
    return true;
  };

  const showValidation = () => {
    // Scroll to top of panel to make validation messages visible
    contentRef.current?.scrollTo?.({ top: 0, behavior: 'smooth' });
  };

  const save = (evt: React.FormEvent) => {
    evt.target && evt.preventDefault();
    if (!formValidate()) {
      showValidation();
      return;
    }

    const { description, displayName, id, ownerEmail, tags } = formValues;

    // Remove the form values node input/expected values that their nodes were removed
    const validNodeIds = validNodes.map((node) => node?.id);
    let nodeValues = Object.values(formValues).filter((formValue) => validNodeIds.includes(formValue?.nodeId));

    dispatch(
      saveFieldPipelineTest({
        pipelineContext,
        fieldPipelineId: pipelineId,
        testId: editTestId,
        test: {
          id,
          ownerEmail: ownerEmail || userEmail,
          tags,
          displayName,
          description,
          testData: {
            input: toSaveValues(nodeValues, validNodes, 'input').map((iVal: any) =>
              transformTestData(iVal, iVal.nodeId)
            ),
            expectedResult: toSaveValues(nodeValues, validNodes, 'expectedResult').map((iVal: any) =>
              transformTestData(iVal, iVal.nodeId)
            ),
          },
        },
      })
    ).then((res) => {
      if (res?.payload && res.payload?.error) {
        showValidation();
      }
    });
  };

  const onMultiFieldValueChange = (nodeId: string, type: string, value: MultiValues) => {
    setFormValues({
      ...formValues,
      [nodeId]: {
        nodeId,
        type,
        value,
      },
    });
  };

  if (!validating && errorMessage) {
    return <PipelineErrorPanel onClose={close} visible={visible} title={tn(editTestId ? 'edit_title' : 'title')} />;
  }

  return (
    <DrawerPanel
      absolutePositioning
      additionalHeightOffset={pipelineContext === 'entity' ? ENTITY_DRAWER_HEIGHT_OFFSET : FIELD_DRAWER_HEIGHT_OFFSET}
      className={cx('synri-test-modal', 'test-add-update-simulated-panel', className)}
      onClose={close}
      title={tn(editTestId ? 'edit_title' : 'title')}
      visible={visible}
      footer={
        <>
          <Button onClick={close} disabled={validating} className="btn-cancel">
            {tc('cancel')}
          </Button>
          <Button onClick={save} type="primary" disabled={validating}>
            {tc('save')}
          </Button>
        </>
      }>
      <div ref={contentRef}>
        <TabPanelSpin spinning={validating} tip={tf('validating')}>
          <InlineMessage
            className="test-add-update-simulated-panel__message"
            type={InlineMessageTypes.ERROR}
            title={saveTestErrorMessage}>
            {saveTestErrorMessage}
          </InlineMessage>
          {fakeNodesCount > 0 && (
            <InlineMessage
              className="test-add-update-simulated-panel__message"
              title={tn('with_invalid_node', { count: fakeNodesCount })}
              type={InlineMessageTypes.WARNING}>
              {tn('with_invalid_node', { count: fakeNodesCount })}
            </InlineMessage>
          )}
          <form onSubmit={save}>
            <InputWithLabel
              name="displayName"
              datatype="string"
              label={tn('display_name')}
              value={formValues?.displayName}
              onChange={onTextChange}
              validateStatus={validation?.displayNameStatus}
              help={typeof validation?.displayNameHelp === 'string' ? validation?.displayNameHelp : ''}
            />
            <InputWithLabel
              name="description"
              datatype="textarea"
              label={tn('description')}
              value={formValues?.description}
              onChange={onTextChange}
            />
            <InputWithLabel
              name="tags"
              datatype="tag"
              label={tn('tags')}
              defaultValue={formValues?.tags}
              onChange={onTagChange}
            />
            <Fieldset title={tn('inputs')}>
              {inputNodes?.length > 0 &&
                inputNodes.map((node: any) => {
                  return (
                    <Collapse bordered={false} key={node.id} defaultActiveKey={node.id}>
                      <Panel
                        header={
                          <Tooltip title={node.subLabel}>
                            {tn(node.invalidNode ? 'invalid_node_name' : 'node_name', { name: node.label })}
                          </Tooltip>
                        }
                        key={node.id}>
                        <MultiFieldValues
                          name={node.id}
                          disabled={node.invalidNode}
                          value={formValues?.[node.id]?.value}
                          onChange={(value) => onMultiFieldValueChange(node.id, 'input', value)}
                          picklistValues={picklistValues[node.id]}
                        />
                      </Panel>
                    </Collapse>
                  );
                })}
            </Fieldset>
            <Fieldset title={tn('expected_outputs')}>
              {expectedResultNodes?.length > 0 &&
                expectedResultNodes.map((node: any) => {
                  return (
                    <Collapse bordered={false} key={node.id} defaultActiveKey={node.id}>
                      <Panel
                        header={
                          <Tooltip title={node.subLabel}>
                            {tn(node.invalidNode ? 'invalid_node_name' : 'node_name', { name: node.label })}
                          </Tooltip>
                        }
                        key={node.id}>
                        <MultiFieldValues
                          name={node.id}
                          disabled={node.invalidNode}
                          value={formValues?.[node.id]?.value}
                          onChange={(value) => onMultiFieldValueChange(node.id, 'expectedResult', value)}
                          picklistValues={picklistValues[node.id]}
                        />
                      </Panel>
                    </Collapse>
                  );
                })}
            </Fieldset>
          </form>
        </TabPanelSpin>
      </div>
    </DrawerPanel>
  );
};

export default TestAddUpdateSimulatedPanel;
