import { chartColors } from 'components/vizer/utils/VizerGraphColors';
import { DataCardWithData } from 'store/insights-studio/types';

export const getCurrentYearSales = (total = 1): DataCardWithData => {
  return {
    configuration: {
      displayName: {
        defaultValue: 'Current Year Sales',
        datatype: 'string',
        defaultValueType: 'LITERAL',
      },
      name: {
        defaultValue: 'currentYearSales',
        datatype: 'string',
        defaultValueType: 'LITERAL',
      },
    },
    configurationMeta: [
      {
        id: 'timeFrame',
        description: 'Time Frame description',
        helpSummary: 'Time frame for Current Year Sales',
        name: 'datetimeRange',
        displayName: 'Time Frame',
        component: 'datetimeRangePicker',
      },
    ],
    contents: {
      configuration: {
        colorTheme: chartColors[0].color,
        columns: [
          {
            displayFormat: 'currency',
            displayName: 'Total',
            name: 'Total',
          },
        ],
        vizType: 'METRIC',

        xaxis: {
          column: 'Total',
          displayName: 'Total',
          name: 'Total',
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
            Total: total,
          },
        ],
      },
      id: 'currentYearSales',
    },
    description: 'Amount in $ of closed won opportunities this year',
    displayName: 'Current Year Sales',
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
    name: 'currentYearSales',
  };
};

const currentYearSales = getCurrentYearSales();

export { currentYearSales };
