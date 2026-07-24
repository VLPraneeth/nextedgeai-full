import { DataCardWithData } from 'store/insights-studio/types';
import { DeepPartial } from 'utils/TypeUtils';

export const dataCard2: DeepPartial<DataCardWithData> = {
  id: 'dataCard1',
  layout: { h: 3 },
  name: 'dataCard2',
  displayName: 'Data Card 2',
  description: 'A test data card with a custom data error',
  hidden: false,
  contents: {
    data: {
      error: { title: 'Custom Error', body: 'Body of a customized error' },
    },
  },
};
