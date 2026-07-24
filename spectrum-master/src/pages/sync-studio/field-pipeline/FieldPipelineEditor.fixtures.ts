//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { merge } from 'lodash';
import { DeepPartial } from 'redux';

import { RootState } from 'reducers/index';
import { TestRenderOptions } from 'tests/helpers';

export const getMinimalFieldPipelineState = (updatedState: DeepPartial<RootState> = {}): TestRenderOptions => {
  const defaultState: DeepPartial<RootState> = {
    entityPipeline: {
      schemas: [],
    },
    fieldPipeline: {
      fieldPipeline: {
        graphVersion: 'approved',
        id: 'abcdef123456',
        draft: null,
      },
    },
    pipelineAction: {
      fieldPipelineActions: [],
    },
    pipelineFunction: {
      fieldPipelineFunctions: [],
    },
    test: {},
    fragment: {},
    validation: {
      errors: [],
      warnings: [],
    },
    pipelineError: {},
  };

  const testState = merge(defaultState, updatedState);

  return { testState };
};
