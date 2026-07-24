// @ts-nocheck
import { getDervEntitiesWithFieldDraftSummary } from '../entitySelectors';

describe('getDervEntitiesWithFieldDraftSummary', () => {
  // getDervEntitiesWithFieldDraftSummary grabs the entities out of state,
  // and updates any fields found in the fieldDraftSummary with the lastUpdatedAt info

  test('returns [] when no entities exist in store', () => {
    const state = {
      entity: {
        entities: null,
      },
      entityPipeline: { fieldDraftSummary: null },
    };

    expect(getDervEntitiesWithFieldDraftSummary(state)).toEqual([]);
  });

  test('returns entities when entities exist in store and no fieldDraftSummary exists', () => {
    const state = {
      entity: {
        entities: [
          {
            id: '5e7150495f3ca000011774a2',
            apiName: 'AcceptedEventRelation',
            displayName: 'Accepted Event Relation',
            subLabel: 'DRAFT',
            iconPath: '/assets/icons/draft.svg',
            pipelineStatus: 'DRAFT',
            type: 'standard',
          },
        ],
      },
      entityPipeline: { fieldDraftSummary: null },
    };

    expect(getDervEntitiesWithFieldDraftSummary(state)).toStrictEqual(state.entity.entities);
  });

  test('returns entities when entities exist in store and a fieldDraftSummary does exist', () => {
    const createState = () => ({
      entity: {
        entities: [
          {
            id: '5e7150495f3ca000011774a2',
            apiName: 'AcceptedEventRelation',
            displayName: 'Accepted Event Relation',
            subLabel: 'DRAFT',
            iconPath: '/assets/icons/draft.svg',
            pipelineStatus: 'DRAFT',
            type: 'standard',
            fields: [
              {
                id: '5e7150495f3ca000011774a3',
                apiName: 'AccountId',
                displayName: 'Account ID',
                dataType: 'reference',
                status: 'ACTIVE',
                tags: [],
                idField: false,
              },
              {
                id: '5e7150495f3ca000011774a4',
                apiName: 'CreatedById',
                displayName: 'Created By ID',
                dataType: 'reference',
                status: 'ACTIVE',
                tags: [],
                idField: false,
              },
            ],
          },
        ],
      },
      entityPipeline: {
        fieldDraftSummary: {
          '5e7150495f3ca000011774a2': [
            {
              name: 'AccountId',
              id: '5e7150495f3ca000011774a3',
              updatedAt: '2020-04-01T02:29:05.206+0000',
            },
            {
              name: 'Billing Country',
              id: '5e613d6598a68d0001ab85ca',
              updatedAt: '2020-04-01T02:29:05.329+0000',
            },
          ],
        },
      },
    });

    const state = createState();

    // we expect the AccountId field to now have `fieldPipelineUpdatedAt` set
    // using the date from `fieldDraftSummary`. Everything else should be the same
    let expectedResult = createState();
    expectedResult.entity.entities[0].fields[0].fieldPipelineUpdatedAt =
      expectedResult.entityPipeline.fieldDraftSummary['5e7150495f3ca000011774a2'][0].updatedAt;

    //  pluck off the slice of the state that we expect
    expectedResult = expectedResult.entity.entities;

    expect(getDervEntitiesWithFieldDraftSummary(state)).toStrictEqual(expectedResult);
  });
});
