//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { renderWithRouter, screen } from 'tests/helpers';

import NodePanel, { NodeUIModel } from '../NodePanel';
import { getNode, getNodePanelTestState } from '../NodePanel.fixtures';

const PATH = '/sync-studio/entity/:entityId/field/:fieldId/pipeline/:graphVersion';
const ROUTE = '/sync-studio/entity/5e0d22e77df51d38b296628e/field/1212512512123as/pipeline/DRAFT';

describe('NodePanel', () => {
  test('Show node panel', async () => {
    const node = getNode() as NodeUIModel;

    const { rerenderWithRouter } = renderWithRouter(<NodePanel node={node} path={PATH} />, {
      testState: getNodePanelTestState(),
      route: ROUTE,
    });
    rerenderWithRouter(<NodePanel node={node} path={PATH} />);

    expect(await screen.findByText('myvalue')).toBeInTheDocument();
  });

  test('Filter in node panel should update values', async () => {
    const { rerenderWithRouter } = renderWithRouter(
      // @ts-expect-error
      <NodePanel node={getNode()} path={PATH} />,
      {
        testState: getNodePanelTestState(),
        route: ROUTE,
      }
    );
    expect(await screen.findByText('myvalue')).toBeInTheDocument();

    rerenderWithRouter(
      <NodePanel
        node={
          getNode({
            metadata: {
              configuration: {
                predicate: {
                  predicates: [
                    {
                      right: { value: 'myupdatedvalue', type: 'literal' },
                    },
                  ],
                },
              },
            },
          }) as NodeUIModel
        }
        path={PATH}
      />
    );
    expect(await screen.findByText('myupdatedvalue')).toBeInTheDocument();
  });

  test('Filter input with field values on a dynamic configuration node', async () => {
    renderWithRouter(
      <NodePanel
        node={
          getNode({
            label: 'Filter with filter lookup condition',
            metadata: {
              configuration: {
                predicate: {
                  predicates: [
                    {
                      left: {
                        datatype: 'string',
                        picklistGroup: 'Previous Lookups',
                        label: 'Lookup Result from Mi Lookup',
                        type: 'variable',
                        value: 'output_60343256fa8fd8353d6471f4.x.lookupResult',
                      },
                      operator: 'not_empty',
                      predicateId: '603432abfa8fd8353d6473b8',
                      name: 'predicate',
                    },
                  ],
                },
                definition: '5f619d7d5dad1b58e7893ca8',
                configId: '5f619d7d5dad1b58e7893ca8',
              },
            },
          }) as NodeUIModel
        }
        path={PATH}
      />,
      {
        route: ROUTE,
        testState: getNodePanelTestState({
          entityPipeline: {
            dynamicConfigValues: {
              '5f6e44f908fd387e82f2285f': {
                id: '5f619d7d5dad1b58e7893ca8',
                name: 'filter',
                configuration: [
                  {
                    mapping: [
                      {
                        graphKey: 'configuration.attributeDefinition',
                        configKey: 'value',
                      },
                    ],
                    datatype: 'picklist',
                    values: [
                      {
                        datatype: 'string',
                        picklistGroup: 'Previous Lookups',
                        label: 'This value should be renderd instead of the persisted value',
                        type: 'variable',
                        value: 'output_60343256fa8fd8353d6471f4.x.lookupResult',
                      },
                    ],
                    name: 'field',
                    fieldSet: 'conditionFields',
                    label: 'Field',
                  },
                  {
                    mapping: [
                      {
                        graphKey: 'configuration.predicate',
                      },
                    ],
                    datatype: 'predicate',
                    name: 'predicate',
                    fieldSet: 'conditionFields',
                    label: 'Condition',
                  },
                  {
                    mapping: [
                      {
                        graphKey: 'configuration.operator',
                        configKey: 'value',
                      },
                    ],
                    dependsOn: {
                      dependantField: 'configuration.attributeDefinition',
                      dependantType: 'Operator',
                    },
                    datatype: 'picklist',
                    name: 'operator',
                    fieldSet: 'conditionFields',
                    label: 'Operator',
                  },
                  {
                    mapping: [
                      {
                        graphKey: 'configuration.value',
                      },
                    ],
                    datatype: 'string',
                    name: 'value',
                    fieldSet: 'conditionFields',
                    label: 'Value',
                    type: 'literal',
                  },
                ],
                dynamicConfig: true,
              },
            },
          },
        }),
      }
    );
    expect(await screen.findByText('This value should be renderd instead of the persisted value')).toBeInTheDocument();
  });
});
