import ObjectID from 'bson-objectid';

import { ReferenceDataState } from './slice';
import { ReferenceDataRecord } from './types';

export type ReferenceDataTestState = Pick<ReferenceDataState, 'ids' | 'entities'>;

export const createRefDataTestState = (
  refDataCountOrItems: number | Partial<ReferenceDataRecord>[]
): ReferenceDataTestState => {
  const refDataItems =
    typeof refDataCountOrItems === 'number'
      ? Array.from({ length: refDataCountOrItems }).map(() => {
          const id = ObjectID.generate();

          return createRefDataFixture({
            id,
            name: id,
          });
        })
      : refDataCountOrItems.map(createRefDataFixture);

  return refDataItems.reduce(
    (acc, refData) => {
      const { id } = refData;

      return {
        ids: [...acc.ids, id],
        entities: { ...acc.entities, [id]: refData },
      };
    },
    {
      ids: [],
      entities: {},
    } as ReferenceDataTestState
  );
};

export const createRefDataFixture = (values: Partial<ReferenceDataRecord>): ReferenceDataRecord => ({
  accessKey: null,
  csvFile: null,
  id: ObjectID.generate(),
  importDetails: null,
  key: '',
  lastImported: new Date().toJSON(),
  location: '',
  name: 'Airport Codes',
  secretKey: null,
  status: 'ACTIVE',
  standard: true,
  totalRecords: '5',
  type: '',
  usedInPipelines: [],
  ...values,
});
