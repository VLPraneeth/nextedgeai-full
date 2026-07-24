import { chartColors } from 'components/vizer/utils/VizerGraphColors';
import { DataCardWithData } from 'store/insights-studio/types';

export const dataCard1: DataCardWithData = {
  id: 'dataCard1',
  layout: {
    h: 3,
    minH: 1,
    w: 4,
    x: 0,
    y: 0,
  },
  name: 'quarterlyClosedPipelineRevenue',
  displayName: 'Quarterly Closed Pipeline Revenue',
  description: 'This is the description',
  hidden: false,
  contents: {
    id: 'revenueBar',
    configuration: {
      vizType: 'BAR',
      colorTheme: chartColors[0].color,
      columns: null,
      xaxis: {
        column: 'quarter',
        displayName: 'Quarter',
        name: 'quarter',
      },
      yaxis: [
        {
          column: 'total',
          name: 'total',
          displayName: 'Total',
          displayFormat: 'currency',
        },
      ],
      series: [{ column: '', name: '', displayName: '', displayFormat: '' }],
    },
    data: {
      series: [
        {
          color: '#0000FF',
          displayName: 'New Business',
        },
        {
          color: '#111111',
          displayName: 'Existing Business',
        },
      ],
      columns: [
        {
          name: 'quarter',
          displayName: 'Quarter',
          displayFormat: 'text',
        },
        {
          name: 'total',
          displayName: 'total',
        },
      ],
      rows: [
        {
          quarter: 'Q3 2021',
          // This is intentioally string to test
          // a series with a string value
          total: '1000.1',
          series: 'Ancient Business',
        },
        {
          quarter: 'Q3 2021',
          total: 1000,
          series: 'New Business',
        },
        {
          quarter: 'Q1 2022',
          total: 500,
          series: 'New Business',
        },
        {
          quarter: 'Q3 2021',
          total: 1000,
          series: 'Existing Business',
        },
        {
          quarter: 'Q1 2022',
          total: 500,
          series: 'Existing Business',
        },
        {
          quarter: 'Q3 2021',
          total: 1000,
          displayName: 'Another Business',
        },
        {
          quarter: 'Q1 2022',
          total: 500,
          displayName: 'Another Business',
        },
      ],
    },
  },
};
