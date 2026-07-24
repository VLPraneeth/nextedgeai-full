//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Status } from 'components/renderers/types';
import * as SchemaActions from 'store/schema/thunks';
import { fireEvent, render } from 'tests/helpers';

import FieldSchemaModal from '../FieldSchemaModal';

describe('Field Schema Modal', () => {
  test('Edit the field with modal', async () => {
    const field = {
      picklistValues: [],
      isRequired: false,
      apiName: 'fieldone1',
      isReadonly: false,
      updatedBy: '5ef7ca568c3f9728a66c65a8',
      displayName: 'Field One 1',
      isCalculated: false,
      precision: 0,
      totalRecords: 100,
      totalFields: 100,
      references: 0,
      isUnique: false,
      usedIn: [],
      description: 'Description',
      type: 'string',
      tags: ['tagone'],
      isSystem: false,
      lastUpdated: '2020-07-01T06:38:58.100+0000',
      id: '5efa70ebda58b56af83de2d9',
      isIdField: true,
      sources: [],
      destinations: [],
      hasDraft: true,
      status: Status.APPROVED,
    };

    const { findByText } = render(<FieldSchemaModal entityId="5ef7dc5f8c3f9729293ee467" field={field} />, {
      testState: {
        schema: {
          connectorEntitySchemas: {
            '5ef7dc5f8c3f9729293ee467': {},
          },
        },
        entityPipeline: {
          fieldDraftSummary: {},
        },
      },
    });
    expect(await findByText('Field One 1')).toBeInTheDocument();
  });

  test('Create a field', async () => {
    const apSpy = jest.spyOn(SchemaActions, 'saveField');

    const { findByText, queryByText } = render(
      <FieldSchemaModal entityId="5ef7dc5f8c3f9729293ee467" isSyncariConnector />,
      {
        testState: {
          schema: {
            connectorEntitySchemas: {
              '5ef7dc5f8c3f9729293ee467': {},
            },
          },
          entityPipeline: {
            fieldDraftSummary: {},
          },
        },
      }
    );
    expect(await findByText('New Field')).toBeInTheDocument();
    expect(await findByText('Data Store Name')).toBeInTheDocument();
    expect(queryByText('Changing this value could break your reports built on Data Store.')).not.toBeInTheDocument();

    const displayName = document.querySelector('input[name="displayName"]');
    fireEvent.change(displayName!, { target: { value: 'New Field' } });
    // Blur the displayName field to trigger automatically updating the api field
    fireEvent.blur(displayName!);

    // Data store warning should display when a value is present in the data
    // store field. Value should be present after bluring the displayName field.
    expect(queryByText('Changing this value could break your reports built on Data Store.')).toBeInTheDocument();

    const dataType = document.querySelector('.synri-field-schema-modal .data-type.synri-picklist .ant-select-arrow');
    fireEvent.click(dataType!);

    const dateTimeSelectOption = await findByText('DateTime');
    fireEvent.click(dateTimeSelectOption!);

    const createButton = await findByText('Create');

    fireEvent.click(createButton);
    expect(apSpy).toHaveBeenCalledWith(
      {
        // new_field is the auto generated name from createApiName function
        displayName: 'New Field',
        dataType: 'datetime',
        apiName: 'new_field',
        dataStoreName: 'new_field',
      },
      { refresh: true, entityId: '5ef7dc5f8c3f9729293ee467' }
    );

    // Verify that apiName and dataStoreName can be manually set by user
    const apiName = document.querySelector('input[name="apiName"]');
    fireEvent.change(apiName!, { target: { value: 'newField' } });

    const dataStoreName = document.querySelector('input[name="dataStoreName"]');
    fireEvent.change(dataStoreName!, { target: { value: 'dataStore' } });

    fireEvent.click(createButton);
    expect(apSpy).toHaveBeenLastCalledWith(
      {
        displayName: 'New Field',
        dataType: 'datetime',
        apiName: 'newField',
        dataStoreName: 'dataStore',
      },
      { refresh: true, entityId: '5ef7dc5f8c3f9729293ee467' }
    );
  });

  test('Edit the connector entity attribute that is syncari defined should be allowed', async () => {
    const field = {
      picklistValues: [],
      isRequired: false,
      apiName: 'fieldone1',
      isReadonly: false,
      updatedBy: '5ef7ca568c3f9728a66c65a8',
      displayName: 'Field One 1',
      isCalculated: false,
      precision: 0,
      totalRecords: 100,
      totalFields: 100,
      references: 0,
      isUnique: false,
      usedIn: [],
      description: 'Description',
      type: 'string',
      tags: ['tagone'],
      isSystem: false,
      lastUpdated: '2020-07-01T06:38:58.100+0000',
      id: '5efa70ebda58b56af83de2d9',
      isIdField: true,
      sources: [],
      destinations: [],
      hasDraft: true,
      status: Status.APPROVED,
    };

    const { findByTestId, findByText } = render(
      <FieldSchemaModal entityId="5ef7dc5f8c3f9729293ee467" isSyncariConnector field={field} />,
      {
        testState: {
          schema: {
            connectorEntitySchemas: {
              '5ef7dc5f8c3f9729293ee467': {},
            },
          },
          entityPipeline: {
            fieldDraftSummary: {},
          },
        },
      }
    );
    expect(await findByText('Display Name')).toBeInTheDocument();
    expect(await findByTestId('field-display-name')).not.toHaveAttribute('disabled');
    expect(await findByTestId('field-api-name')).toHaveAttribute('disabled');
  });

  test('Show unique for marketo and dynamodb synapse field', async () => {
    const field = {
      picklistValues: [],
      isRequired: false,
      apiName: 'fieldone1',
      isReadonly: false,
      updatedBy: '5ef7ca568c3f9728a66c65a8',
      displayName: 'Field One 1',
      isCalculated: false,
      precision: 0,
      totalRecords: 100,
      totalFields: 100,
      references: 0,
      isUnique: false,
      isSyncariDefined: true,
      usedIn: [],
      description: 'Description',
      type: 'string',
      tags: ['tagone'],
      isSystem: false,
      lastUpdated: '2020-07-01T06:38:58.100+0000',
      id: '5efa70ebda58b56af83de2d9',
      isIdField: true,
      sources: [],
      destinations: [],
      hasDraft: true,
      status: Status.APPROVED,
    };

    const { queryByText, findByText, rerender } = render(
      <FieldSchemaModal entityId="5ef7dc5f8c3f9729293ee467" field={field} synapse={{ typeName: 'Marketo' }} visible />,
      {
        testState: {
          schema: {
            connectorEntitySchemas: {
              '5ef7dc5f8c3f9729293ee467': {},
            },
          },
          entityPipeline: {
            fieldDraftSummary: {},
          },
        },
      }
    );
    expect(await findByText('Unique')).toBeInTheDocument();

    rerender(
      <FieldSchemaModal
        entityId="5ef7dc5f8c3f9729293ee467"
        field={field}
        synapse={{ typeName: 'Amazon DynamoDB' }}
        visible
      />
    );
    expect(await findByText('Unique')).toBeInTheDocument();

    rerender(
      <FieldSchemaModal entityId="5ef7dc5f8c3f9729293ee467" field={field} synapse={{ typeName: 'AirTable' }} visible />
    );

    expect(await findByText('Display Name')).toBeInTheDocument();
    expect(queryByText('Unique')).toBeNull();
  });
});
