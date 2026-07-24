//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { PayloadAction } from '@reduxjs/toolkit';
import { DeepPartial } from 'redux';

import { RootState } from 'reducers';
import { getMultipleEmptyConnectorMetadata } from 'store/connectors';
import { GET_ENTITIES_FULFILLED, GET_ENTITIES_PENDING } from 'store/entity/types';
import { mockedAjaxUtils, renderWithRouter, screen, waitFor } from 'tests/helpers';
import { configureMockStoreWithReducer } from 'tests/helpers/StoreHelper';
import AppConstants from 'utils/AppConstants';

import ConnectorEditor from '../ConnectorEditor';

jest.mock('utils/AjaxUtil', () => {
  const actual = jest.requireActual('utils/AjaxUtil');
  return {
    ...actual,
    get: jest.fn(),
    makeBaseQuery: jest.fn(() => jest.fn(async () => ({ data: {} }))),
  };
});

describe('Connector Editor', () => {
  test('Renders and get entities after a successful activation', async () => {
    mockedAjaxUtils().get.mockResolvedValue({ data: {} });

    const initialState: DeepPartial<RootState> = {
      customSynapse: {
        customSynapseApprovalModal: {
          visible: false,
          customSynapse: null,
        },
      },
      connector: {
        connectorsMetadata: getMultipleEmptyConnectorMetadata(),
        activatedConnectorId: '1234',
      },
      test: {
        fieldPipelineTests: [],
      },
      pipeline: {
        displayedGraph: AppConstants.GRAPH_STATUS.NEW,
      },
      fragment: {
        nodeCheckValues: {},
      },
      fieldPipeline: {},
      entityPipeline: {
        entityPipeline: {
          draftStatus: AppConstants.GRAPH_STATUS.NEW,
          nodes: [],
          edges: [],
          readOnly: false,
          readOnlyReason: '',
        },
        connectorEntities: [],
        nodeConfigModalVisible: true,
      },
      pipelineFunction: {
        entityPipelineFunctions: [],
        entityPipelineFunctionsFetching: false,
      },
      pipelineAction: {
        entityPipelineActions: [],
      },
    };
    const ACTIVATED = 'connector/activated';
    const store = configureMockStoreWithReducer(initialState, (draft: RootState, action: PayloadAction<string>) => {
      switch (action.type) {
        case ACTIVATED:
          draft.connector.activatedConnectorId = action.payload;
      }
      return draft;
    });

    renderWithRouter(<ConnectorEditor renderGraph={false} />, { store });

    store.dispatch({
      type: ACTIVATED,
      payload: '4321',
    });

    expect(await screen.findByText(/Synapse Library/)).toBeInTheDocument();

    await waitFor(() => {
      expect(store.getActions()).toContainEqual({ type: GET_ENTITIES_PENDING });
      expect(store.getActions()).toContainEqual({
        type: GET_ENTITIES_FULFILLED,
        payload: { connections: undefined, connectorId: '', entities: undefined },
      });
    });
  });
});
