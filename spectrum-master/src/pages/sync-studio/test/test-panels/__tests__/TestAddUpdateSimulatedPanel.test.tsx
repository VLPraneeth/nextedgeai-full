//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { fieldPipelinePicklistValues, pipeline, pipelineTests } from 'store/test/__tests__/selectors.test';
import * as TestActions from 'store/test/thunks';
import { fireEvent, render } from 'tests/helpers';
import { configureMockStoreWithReducer } from 'tests/helpers/StoreHelper';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import TestAddUpdateSimulatedPanel from '../TestAddUpdateSimulatedPanel';

const tn = tNamespaced('TestAddUpdateSimulatedPanel');

describe('TestAddUpdateSimulatedPanel', () => {
  test('TestAddUpdateSimulatedPanel render the form without any problem', async () => {
    const { findByText, queryAllByText } = render(
      <TestAddUpdateSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        testState: {
          entityPipeline: {
            pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
          },
          fieldPipeline: {
            fieldPipeline: pipeline,
          },
          test: {
            createTestVisible: true,
            fieldPipelineTests: pipelineTests,
            // @ts-expect-error: using string is conflicting with the datatype which is a string union
            fieldPipelinePicklistValues,
          },
        },
      }
    );

    expect(await findByText(tn('title'))).toBeInTheDocument();
    expect(await findByText(tc('save'))).toBeInTheDocument();
    expect(queryAllByText(tc('plus_add'))?.length).toEqual(2);
  });

  test('TestAddUpdateSimulatedPanel render with edit title', async () => {
    const { findByText } = render(
      <TestAddUpdateSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        testState: {
          entityPipeline: {
            pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
          },
          fieldPipeline: {
            fieldPipeline: pipeline,
          },
          test: {
            createTestVisible: true,
            fieldPipelineTests: pipelineTests,
            // @ts-expect-error: using string is conflicting with the datatype which is a string union
            fieldPipelinePicklistValues,
            editTestId: '1234',
          },
        },
      }
    );

    expect(await findByText(tn('edit_title'))).toBeInTheDocument();
  });

  test('TestAddUpdateSimulatedPanel validate on visible', async () => {
    const test = {
      validate: () => {},
    };

    const validateSpy = jest.spyOn(test, 'validate');

    const initialState = {
      entityPipeline: {
        pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
      },
      fieldPipeline: {
        fieldPipeline: pipeline,
      },
      test: {
        createTestVisible: true,
        fieldPipelineTests: pipelineTests,
        fieldPipelinePicklistValues,
      },
    };

    const CREATE_TEST_ACTION = 'test/showCreateTest';
    const store = configureMockStoreWithReducer(initialState, (draft: any, action: any) => {
      switch (action.type) {
        case CREATE_TEST_ACTION:
          draft.test.createTestVisible = action.payload.visible;
      }
      return draft;
    });

    const { findByText } = render(
      <TestAddUpdateSimulatedPanel
        pipelineId="1234"
        pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD}
        validate={test.validate}
      />,
      {
        store,
      }
    );
    store.dispatch({
      type: CREATE_TEST_ACTION,
      payload: { visible: true },
    });

    expect(await findByText(tn('title'))).toBeInTheDocument();
    expect(validateSpy).toHaveBeenCalledTimes(1);
  });

  test('TestAddUpdateSimulatedPanel validate blank displayName', async () => {
    const testState = {
      entityPipeline: {
        pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
      },
      fieldPipeline: {
        fieldPipeline: pipeline,
      },
      test: {
        createTestVisible: true,
        fieldPipelineTests: pipelineTests,
        fieldPipelinePicklistValues,
      },
    };

    const { findByText } = render(
      <TestAddUpdateSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        // @ts-expect-error: using string is conflicting with the datatype which is a string union
        testState,
      }
    );

    fireEvent.click(await findByText(tc('save')));
    expect(await findByText(tc('cannot_be_empty', { name: tn('display_name') }))).toBeInTheDocument();
  });

  test('TestAddUpdateSimulatedPanel should not crash if no pipeline nodes are available', async () => {
    const testState = {
      entityPipeline: {
        pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
      },
      fieldPipeline: {
        // Pipeline has no nodes property
        fieldPipeline: {},
      },
      test: {
        createTestVisible: true,
        fieldPipelineTests: pipelineTests,
        fieldPipelinePicklistValues,
      },
    };

    const { findByText } = render(
      <TestAddUpdateSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        // @ts-expect-error: using string is conflicting with the datatype which is a string union
        testState,
      }
    );

    fireEvent.click(await findByText(tc('save')));
    expect(await findByText(tc('cannot_be_empty', { name: tn('display_name') }))).toBeInTheDocument();
  });

  test('TestAddUpdateSimulatedPanel save new test', async () => {
    const saveSpy = jest.spyOn(TestActions, 'saveFieldPipelineTest');
    const testState = {
      entityPipeline: {
        pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
      },
      fieldPipeline: {
        fieldPipeline: pipeline,
      },
      test: {
        createTestVisible: true,
        fieldPipelineTests: pipelineTests,
        fieldPipelinePicklistValues,
      },
    };

    const { findByText } = render(
      <TestAddUpdateSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        // @ts-expect-error: using string is conflicting with the datatype which is a string union
        testState,
      }
    );

    const displayName = document.querySelector('input[name="displayName"]');
    displayName && fireEvent.change(displayName, { target: { value: 'testname' } });
    fireEvent.click(await findByText(tc('save')));
    expect(saveSpy).toHaveBeenCalled();
  });

  test('Edit a test with invalid node values', async () => {
    const saveSpy = jest.spyOn(TestActions, 'saveFieldPipelineTest');
    const { findByText } = render(
      <TestAddUpdateSimulatedPanel pipelineId="1234" pipelineContext={AppConstants.PIPELINE_CONTEXT.FIELD} />,
      {
        testState: {
          user: {
            email: 'admin@syncari.com',
          },
          entityPipeline: {
            pipelineContext: AppConstants.PIPELINE_CONTEXT.FIELD,
          },
          fieldPipeline: {
            fieldPipeline: pipeline,
          },
          test: {
            createTestVisible: true,
            fieldPipelineTests: pipelineTests,
            // @ts-expect-error: using string is conflicting with the datatype which is a string union
            pipelineTest,
            // @ts-expect-error: using string is conflicting with the datatype which is a string union
            fieldPipelinePicklistValues,
            editTestId: '1234',
          },
        },
      }
    );

    expect(await findByText(tn('with_invalid_node_plural'))).toBeInTheDocument();
    expect(await findByText(tn('invalid_node_name', { name: 'Sync from Account Description' }))).toBeInTheDocument();
    expect(await findByText(tn('invalid_node_name', { name: 'Sync to Account Description' }))).toBeInTheDocument();

    fireEvent.click(await findByText(tc('save')));
    expect(saveSpy).toHaveBeenCalledWith({
      fieldPipelineId: '1234',
      pipelineContext: 'field',
      test: {
        description: null,
        displayName: 'testcapitalizedescription',
        id: '6001e4d03a0d47cc3f954a58',
        ownerEmail: 'admin@syncari.com',
        tags: [],
        testData: {
          expectedResult: [],
          input: [],
        },
      },
      testId: '1234',
    });
  });
});

const pipelineTest = {
  id: '6001e4d03a0d47cc3f954a58',
  displayName: 'testcapitalizedescription',
  description: null,
  tags: [],
  testData: {
    input: [
      {
        nodeId: '6001deaf3a0d47cc3f9548eb',
        nodeName: 'Sync from Account Description',
        apiName: 'Description',
        displayName: 'Account Description',
        dataType: 'textarea',
        value: 'description',
        failed: false,
      },
    ],
    expectedResult: [
      {
        nodeId: '6001deaf3a0d47cc3f9548ed',
        nodeName: 'Sync to Account Description',
        apiName: 'Description',
        displayName: null,
        dataType: null,
        value: 'Description',
        failed: false,
      },
    ],
    actualResult: null,
  },
  ownerFirstName: 'Syncari',
  ownerLastName: 'Admin',
  ownerEmail: 'admin@syncari.com',
  result: null,
};
