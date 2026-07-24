//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { merge } from 'lodash';

import { GRAPH_MODE } from 'components/GraphPage';
import { RootState } from 'reducers';
import AppConstants from 'utils/AppConstants';

export const getMinimalEntityPipelineState = (updatedState: Partial<RootState> = {}): Partial<RootState> => {
  return merge(
    {
      connector: {
        connectorsMetadata: [],
      },
      fastMapper: {},
      test: {
        fieldPipelineTests: [],
      },
      editor: {
        mode: GRAPH_MODE.DEFAULT,
      },
      pipeline: {
        displayedGraph: AppConstants.GRAPH_STATUS.NEW,
      },
      fragment: {
        nodeCheckValues: [],
      },
      fieldPipeline: {},
      entityPipeline: {
        entityPipeline: {
          draftStatus: AppConstants.GRAPH_STATUS.NEW,
          nodes: getNodes(),
          edges: [],
          readOnly: false,
          readOnlyReason: '',
        },
        connectorEntities: getConnectorEntities(),
        selectedGraphNode: {
          nodeType: AppConstants.NODE_TYPE.FUNCTION,
          configuration: {
            configId: '60076feca7c7452f076b1683',
          },
        },
        pipelineContext: AppConstants.PIPELINE_CONTEXT.ENTITY,
        nodeConfigModalVisible: true,
      },
      pipelineFunction: {
        entityPipelineFunctions: getEntityPipelineFunctions(),
        entityPipelineFunctionsFetching: false,
      },
      pipelineAction: {
        entityPipelineActions: getEntityPipelineActions(),
      },
      validation: {
        validationToolbarVisible: false,
        errors: [],
        warnings: [],
      },
      pipelineError: {},
    },
    updatedState
  );
};

// TODO: Update the types here when the we add types to the pipelines
export const getNodes = () => [
  {
    id: '60077045a7c7452f076b3c74',
    name: 'Account',
    apiName: 'account',
    label: 'Account',
    subLabel: 'Syncari',
    inputPorts: [],
    outputPorts: [],
    configuration: {},
    nodeType: 'CORE_ENTITY',
    location: {
      y: '400',
      x: '600',
    },
  },
];

export const getEntityPipelineFunctions = () => [
  {
    id: '60076feca7c7452f076b1683',
    name: 'addToMarketoList',
    displayName: 'Add To Marketo List',
    description: null,
    helpSummary: 'Add a lead to a Marketo static list',
    helpPath: '',
    iconPath: '/assets/icons/actions/add-to-list.svg',
    outputType: null,
    positionalParams: [{ name: 'value', datatype: 'object' }],
    engineType: null,
    scope: 'ENTITY',
    type: 'STANDARD',
    configuration: [],
    dynamicConfig: false,
    hidden: false,
    renderer: { renderType: 'form', steps: null },
  },
];

export const getConnectorEntities = () => [
  {
    configuration: [],
    coreNode: false,
    iconPath: '/assets/icons/logos/salesforce.svg',
    id: '60077036a7c7452f076b1704',
    name: 'sfdcone',
    renderer: null,
  },
];

export const getEntityPipelineActions = () => [
  {
    configuration: [],
    description: null,
    displayName: 'Add To Marketo List',
    dynamicConfig: false,
    engineType: null,
    helpPath: '',
    helpSummary: 'Add a lead to a Marketo static list',
    hidden: false,
    iconPath: '/assets/icons/actions/add-to-list.svg',
    id: '60076feca7c7452f076b1683',
    name: 'addToMarketoList',
    outputType: null,
    positionalParams: [],
    renderer: { renderType: 'form', steps: null },
    scope: 'ENTITY',
    type: 'STANDARD',
  },
];
