import { chartColors } from 'components/vizer/utils/VizerGraphColors';
import { DataCardWithData } from 'store/insights-studio/types';

export const getCustomerCount = (count = 0): DataCardWithData => {
  return {
    configuration: {
      displayName: {
        defaultValue: 'Customer Count',
        datatype: 'string',
        defaultValueType: 'LITERAL',
      },
      name: {
        defaultValue: 'existingCustomerCount',
        datatype: 'string',
        defaultValueType: 'LITERAL',
      },
      count: {
        defaultValue: '10000',
        datatype: 'integer',
        defaultValueType: 'LITERAL',
      },
    },
    configurationMeta: [
      {
        id: 'count',
        description: 'Customer count description',
        helpSummary: 'Customer count tooltip',
        name: 'count',
        displayName: 'Customer Count',
        component: 'integer',
      },
    ],
    contents: {
      configuration: {
        colorTheme: chartColors[0].color,
        columns: [
          {
            displayFormat: 'number',
            displayName: 'customercount',
            name: 'customercount',
          },
        ],
        vizType: 'METRIC',
        xaxis: {
          column: 'customerCount',
          displayName: 'Customer Count',
          name: 'customerCount',
        },
        yaxis: [],
        series: [{ column: '', name: '', displayName: '', displayFormat: '' }],
      },
      contents: null,
      data: {
        series: [],
        columns: [],
        rows: [
          {
            customercount: count,
          },
        ],
      },
      id: 'existingCustomerNumber',
    },
    description: 'Number of existing customers',
    displayName: 'Customer Count',
    hidden: false,
    id: '62bb7c8c3358d4aca16d8c05',
    layout: {
      h: 2,
      maxH: 0,
      minH: 2,
      w: 4,
      x: 0,
      y: 2,
    },
    name: 'existingCustomerCount',
  };
};

const customerCount = getCustomerCount();

export { customerCount };
