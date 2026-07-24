import { DataCardWithData } from 'store/insights-studio/types';
import { DeepPartial } from 'utils/TypeUtils';

export const testDataCard: DeepPartial<DataCardWithData> = {
  configuration: {},
  configurationMeta: [
    {
      component: 'datetimeRangePicker',
      displayName: 'Time Frame',
      name: 'datetimeRange',
      description: '',
      id: 'datetimeRange',
      helpSummary: '',
    },
  ],
  contents: {
    configuration: {
      columns: [
        {
          displayFormat: 'text',
          displayName: 'Owner Name',
          name: 'Owner Name',
        },
        {
          displayFormat: 'number',
          displayName: 'Qualified Lead Count',
          name: 'Qualified Lead Count',
        },
      ],
    },
    contents: null,
    data: {
      rows: [
        {
          'Owner Name': 'Syncari Dev',
          'Qualified Lead Count': 6,
        },
      ],
    },
    id: 'sqlLeadCountByOwner',
  },
  description: 'Count of sales qualified leads for each owner',
  displayName: 'SQLs by SDR source',
  hidden: false,
  id: '62b3bcc63358d4168e4c1877',
  layout: {
    h: 2,
    maxH: 0,
    minH: 2,
    w: 4,
    x: 8,
    y: 2,
  },
  name: 'sqlLeadCountByOwner',
};
