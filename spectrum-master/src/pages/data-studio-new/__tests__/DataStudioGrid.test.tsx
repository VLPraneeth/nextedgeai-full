import I18nProvider from 'components/I18nProvider';
import { getDataStudioTestState } from 'store/data-studio';
// import * as dataStudioThunks from 'store/data-studio/thunks';
// import { getDataScoreTestState } from 'store/datascore';
import { renderWithRouter, screen, within, userEvent } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import DataStudioGrid from '../DataStudioGrid';

// const td = tNamespaced('DataStudio');
const tn = tNamespaced('DataStudio.DataScore');
const tdf = tNamespaced('DataFitness.ContributingFactors');

/*
test.each([[true], [false]])('test deleting record from kebabmenu (hardDelete: %s)', async hardDelete => {
  const entityId = 'ASDF12345';
  const entityName = 'Account';
  const dataScore = 15;
  const dataScoreLabel = 'Ugh';

  const datascoreState = getDataScoreTestState({ entityId, entityName, score: dataScore, label: dataScoreLabel });
  const dataStudioState = getDataStudioTestState({ entityId });

  const firstRecord = dataStudioState.entityRecordsListData[entityId].records[0];

  renderWithRouter(
    <I18nProvider namespace="DataStudio">
      <DataStudioGrid entityId={entityId} />
    </I18nProvider>,
    {
      route: `/data-studio/entity/${entityId}`,
      testState: {
        datascore: datascoreState,
        dataStudio: dataStudioState,
        entity: {
          entitiesFetching: false,
          entities: [
            {
              id: entityId,
              apiName: entityName,
              dataStoreName: `entityName: ${entityName}`,
              displayName: entityName,
              description: null,
              subLabel: '',
              iconPath: '',
              pipelineStatus: AppConstants.SYNCARI_NODE_STATUS.PUBLISHED,
              type: '',
              connectedTo: [],
              tags: [],
              location: null,
              status: '',
              draftStatus: AppConstants.GRAPH_STATUS.APPROVED,
              createdBy: '',
              updatedBy: '',
              createdAt: '',
              updatedAt: '',
              fields: [],
              activeFields: [],
            },
          ],
        },
        picklist: {
          picklistValues: {
            'rule_isNotEmpty_BillingState/ASDF12345Operator': [
              {
                label: 'Equals',
                unary: false,
                value: 'eq',
                datatype: 'multivaluetext',
              },
              {
                label: 'Equals Ignore Case',
                unary: false,
                value: 'ieq',
              },
              {
                label: 'Starts With',
                unary: false,
                value: 'starts_with',
              },
              {
                label: 'Is Empty',
                unary: true,
                value: 'empty',
              },
              {
                label: 'Is Not Empty',
                unary: true,
                value: 'not_empty',
              },
              {
                label: 'Not Equals',
                unary: false,
                value: 'ne',
              },
            ],
          },
        },
      },
    }
  );

  const thunksSpy = jest.spyOn(dataStudioThunks, 'deleteRecordData');

  await screen.findAllByRole('button');
  const kebabButton = await screen.findByRole('button', { name: `Action menu for ${firstRecord.syncariId}` });
  expect(kebabButton).toBeInTheDocument();

 await userEvent.click(kebabButton);
 await userEvent.click(await screen.findByText(tc('delete')));

  expect(await screen.findByRole('dialog')).toBeInTheDocument();
  expect(await screen.findByText(td('delete_entity_record_title'))).toBeInTheDocument();

  const inputField = await screen.findByPlaceholderText(`Type "DELETE" to confirm`);
 await userEvent.type(inputField, 'DELETE');

  if (hardDelete) {
   await userEvent.click(await screen.findByRole('switch'));
  }

 await userEvent.click(await screen.findByRole('button', { name: 'OK' }));
  expect(thunksSpy).toBeCalledWith(entityId, firstRecord.syncariId, hardDelete);
});
*/

// eslint-disable-next-line jest/no-disabled-tests
xtest('test filter panel opens when clicking DFI factor', async () => {
  const entityId = 'ASDF12345';
  const entityName = 'Account';
  const dataScore = 15;
  const dataScoreLabel = 'Ugh';

  const dataStudioState = getDataStudioTestState({ entityId });

  renderWithRouter(
    <I18nProvider namespace="DataStudio">
      <DataStudioGrid entityId={entityId} />
    </I18nProvider>,
    {
      route: `/data-studio/entity/${entityId}`,
      testState: {
        dataStudio: dataStudioState,
        entity: {
          entitiesFetching: false,
          entities: [
            {
              id: entityId,
              apiName: entityName,
              dataStoreName: `entityName: ${entityName}`,
              displayName: entityName,
              description: null,
              subLabel: '',
              iconPath: '',
              pipelineStatus: AppConstants.SYNCARI_NODE_STATUS.PUBLISHED,
              type: '',
              connectedTo: [],
              tags: [],
              location: null,
              status: '',
              draftStatus: AppConstants.GRAPH_STATUS.APPROVED,
              createdBy: '',
              updatedBy: '',
              createdAt: '',
              updatedAt: '',
              fields: [],
              activeFields: [],
            },
          ],
        },
        picklist: {
          picklistValues: {
            'rule_isNotEmpty_BillingState/ASDF12345Operator': [
              {
                label: 'Equals',
                unary: false,
                value: 'eq',
                datatype: 'multivaluetext',
              },
              {
                label: 'Equals Ignore Case',
                unary: false,
                value: 'ieq',
              },
              {
                label: 'Starts With',
                unary: false,
                value: 'starts_with',
              },
              {
                label: 'Is Empty',
                unary: true,
                value: 'empty',
              },
              {
                label: 'Is Not Empty',
                unary: true,
                value: 'not_empty',
              },
              {
                label: 'Not Equals',
                unary: false,
                value: 'ne',
              },
            ],
          },
        },
      },
    }
  );

  const DFIBadge = await screen.findByText(dataScore);
  expect(DFIBadge).toBeInTheDocument();
  expect(
    // using selector because we need to make sure we don't select the column header that also matches this string
    await screen.findByText(tn('data_fitness_index'), { selector: '.data-studio-meta-row .syncari-text' })
  ).toBeInTheDocument();

  // hovering DFI should open the popover
  await userEvent.hover(DFIBadge);

  const popover = await screen.findByRole('tooltip');
  const popoverQueries = within(popover);

  expect(popover).toBeInTheDocument();

  // see if the popover is present
  expect(await popoverQueries.findByText(tdf('title'))).toBeInTheDocument();
  expect(await popoverQueries.findByText(dataScoreLabel)).toBeInTheDocument();

  const fieldName = 'BillingState';

  const showRecordsButton = await popoverQueries.findByRole('button', {
    name: tdf('show_records_aria_label', { fieldName, entityId }),
  });
  expect(showRecordsButton).toBeInTheDocument();

  // click "show records" for our BillingState factor
  await userEvent.click(showRecordsButton);

  expect(await screen.findByRole('button', { name: tc('save') })).toBeInTheDocument();

  const ruleFieldMetadata = Object.entries(dataStudioState.entityRecordsListData[entityId].metadata.fields).find(
    ([key]) => key.startsWith('rule_') && key.endsWith(fieldName)
  );

  if (!ruleFieldMetadata) {
    throw new Error('Missing rule metadata');
  }

  // make sure we see the new condition
  expect(await screen.findByTitle(ruleFieldMetadata[1].label)).toBeInTheDocument();
});
