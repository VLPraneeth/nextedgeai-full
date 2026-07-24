//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import produce from 'immer';
import { AnyAction, DeepPartial } from 'redux';
import thunk from 'redux-thunk';

// initial states
import { configureMockStore } from '@jedmao/redux-mock-store';
import { _getDefaultState as getInitialConnectorState } from 'reducers/connectorReducer';
import { _getDefaultState as getInitialEntityPipelineState } from 'reducers/entityPipelineReducer';
import { _getDefaultState as getInitialReferenceDataState } from 'reducers/referenceDataReducer';
import { _getDefaultState as getInitialSubscriptionState } from 'reducers/subscriptionReducer';
import { initialTestState as initialApiTestState } from 'store/api';
import { _getDefaultState as getInitialAppState } from 'store/app/reducer';
import { _getDefaultState as getInitialCredentialState } from 'store/credentials/slice';
import { initialState as initialDataQualityState } from 'store/data-quality';
import { _getDefaultState as getInitialEntityState } from 'store/entity/reducer';
import { initialTestState as initialFastMapperState } from 'store/fast-mapper/slice';
import { _getDefaultState as getInitialFragmentState } from 'store/fragment/reducer';
import { _getDefaultState as getInitialInstanceSlice } from 'store/instances/slice';
import { initialState as initialNewDashboardState } from 'store/new-dashboard/slice';
import { _getDefaultState as getInitialPicklistState } from 'store/picklists/reducer';
import { initialState as initialPipelineState } from 'store/pipeline/slice';
import { initialState as initialSchemaState } from 'store/schema/reducer';
import { RootState } from 'store/types';
import { _getDefaultState as getInitialUserState } from 'store/user/reducer';
import { initialState as initialValidationState } from 'store/validation/slice';

// this needs to be a umd import instead of es6 because of a weird bug
const deepmerge = require('deepmerge');

export const INITIAL_STATE: Partial<RootState> = {
  api: initialApiTestState,
  app: getInitialAppState(),
  connector: getInitialConnectorState(),
  credential: getInitialCredentialState(),
  dataQuality: initialDataQualityState,
  entity: getInitialEntityState(),
  entityPipeline: getInitialEntityPipelineState(),
  fastMapper: initialFastMapperState,
  fragment: getInitialFragmentState(),
  instance: getInitialInstanceSlice(),
  newDashboard: initialNewDashboardState,
  picklist: getInitialPicklistState(),
  pipeline: initialPipelineState,
  referenceData: getInitialReferenceDataState(),
  schema: initialSchemaState,
  subscription: getInitialSubscriptionState(),
  user: getInitialUserState(),
  validation: initialValidationState,
};

type StateSlice = Object;

export const combineState = (initialState = INITIAL_STATE, ...states: StateSlice[]) =>
  deepmerge([initialState, ...states]);

const defaultOptions = {
  initialState: INITIAL_STATE,
  middlewares: [thunk],
};

/**
 * Configure the cluster store for testing
 */
export function createAppTestStore(
  testState: DeepPartial<RootState> = {},
  initialState: Partial<RootState> = INITIAL_STATE,
  storeOptions = defaultOptions
) {
  // combine the test state with our reducer initial states, deeply, so that
  // we get all of our initial state set properly
  const state = deepmerge(initialState, testState);

  // return our test store with middleware and state
  // TODO: improve Thunk typing (3rd argument)
  return configureMockStore<Partial<RootState>, AnyAction, any>(storeOptions.middlewares)(state);
}

// TODO: Type this
const createStateWithReducer = (state: any, reducer: any) => {
  const createState = (initialState: any) => (actions: any) => actions.reduce(produce(reducer), initialState);
  return createState(deepmerge(INITIAL_STATE, state));
};

// TODO: Type this
export const configureMockStoreWithReducer = (initialState: any, reducer: any) =>
  configureMockStore(defaultOptions.middlewares)(createStateWithReducer(initialState, reducer));
