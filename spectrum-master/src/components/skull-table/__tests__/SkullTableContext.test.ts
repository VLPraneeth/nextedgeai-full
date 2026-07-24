import { tranformSourceDataToFormData } from '../SkullTableContext';

describe('tranformSourceDataToFormData', () => {
  test('should return the form data based on the columnDefs', () => {
    const formData = tranformSourceDataToFormData([
      {
        id: 'rowId',
        fields: {
          selected: {
            value: true,
          },
          synapse: {
            label: 'Salesforce',
            value: '606de0f5e31bbd8f90422d45',
          },
          entity: {
            label: 'Contact',
            value: '606de149e31bbd8f904233d2',
          },
          unificationField: {
            value: ['606de149e31bbd8f904233f7', '606de149e31bbd8f904233f8'],
            values: [
              {
                label: 'Email Bounced Reason',
                value: '606de149e31bbd8f904233f7',
              },
              {
                label: 'Email Bounced Date',
                value: '606de149e31bbd8f904233f8',
              },
              {
                label: 'Deleted',
                value: '606de149e31bbd8f904233d4',
              },
            ],
          },
        },
      },
    ]);
    expect(formData).toHaveLength(1);

    expect(formData[0]).toMatchObject({
      id: 'rowId',
      selected: true,
      synapse: '606de0f5e31bbd8f90422d45',
      entity: '606de149e31bbd8f904233d2',
      unificationField: ['606de149e31bbd8f904233f7', '606de149e31bbd8f904233f8'],
    });
  });
});
