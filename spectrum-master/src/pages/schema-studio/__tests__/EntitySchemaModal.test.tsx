//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import * as SchemaActions from 'store/schema/thunks';
import { fireEvent, render, screen } from 'tests/helpers';

import EntitySchemaModal from '../EntitySchemaModal';

describe('Entity Schema Modal', () => {
  test('Entity schema Modal renders an entity', async () => {
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

    render(<EntitySchemaModal visible />, {
      testState: {
        schema: {
          // @ts-expect-error: missing fields for test
          entityDetails,
        },
      },
    });
    expect(await screen.findByText('New Entity')).toBeInTheDocument();
  });

  test('Create an entity', async () => {
    const apSpy = jest.spyOn(SchemaActions, 'saveEntity');
    const entityDetails = {};
    render(<EntitySchemaModal visible connectorId="abc" />, {
      testState: {
        schema: {
          // @ts-expect-error: missing fields for test
          entityDetails,
        },
      },
    });

    expect(await screen.findByText('New Entity')).toBeInTheDocument();

    const displayName = document.querySelector('input[name="displayName"]');
    fireEvent.change(displayName!, { target: { value: 'New Entity' } });
    // Blur the displayName field to trigger automatically updating the api field
    fireEvent.blur(displayName!);

    const createButton = await screen.findByText('Create');
    fireEvent.click(createButton);

    expect(apSpy).toHaveBeenCalledWith(
      // new_entity is the auto generated name from createApiName function
      { displayName: 'New Entity', apiName: 'new_entity' },
      { refresh: true, connectorId: 'abc' }
    );
  });
});
