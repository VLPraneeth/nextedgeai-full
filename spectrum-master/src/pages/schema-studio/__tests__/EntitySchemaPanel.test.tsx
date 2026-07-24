//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { getCurrentGraphFixture } from 'store/pipeline/fixtures';
import { render } from 'tests/helpers';

import EntitySchemaPanel from '../EntitySchemaPanel';

describe('Entity Schema Panel', () => {
  test('Entity schema panel renders an entity', async () => {
    const entityDetails = {
      id: '5ef7dc5f8c3f9729293ee467',
      apiName: 'myEntity1',
      displayName: 'My Entity Oneoo',
      description: 'Description',
      subLabel: null,
      iconPath: null,
      pipelineStatus: null,
      type: 'standard',
      connectedTo: [],
      tags: ['newTag'],
      fields: [
        {
          id: '5efa70ebda58b56af83de2d9',
          apiName: 'fieldone1',
          displayName: 'Field One 1',
          description: 'Description',
          dataType: 'string',
          status: null,
          type: null,
          tags: ['tagone'],
          values: [],
          isMapped: false,
          hasChanges: false,
          draftStatus: 'NEW',
          multiValueField: false,
          idField: false,
        },
      ],
      location: null,
      status: 'NEW',
      createdBy: null,
      updatedBy: '5ef7ca568c3f9728a66c65a8',
      createdAt: null,
      updatedAt: '2020-07-01T06:38:58.087+0000',
      activeFields: [],
    };
    const { findByText } = render(
      <EntitySchemaPanel entityId="5ef7dc5f8c3f9729293ee467" editEntity={(_entityId: string) => {}} />,
      {
        testState: {
          schema: {
            // @ts-expect-error: missing items, ok for test
            entityDetails,
            pipeline: {
              // @ts-ignore
              currentGraph: getCurrentGraphFixture(),
            },
          },
        },
      }
    );
    expect(await findByText('My Entity Oneoo')).toBeInTheDocument();
  });
});
