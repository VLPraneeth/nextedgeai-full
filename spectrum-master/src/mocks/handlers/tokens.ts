import { makeTestUrl } from 'mocks/utils';
import { rest } from 'msw';

import { testTokens as compositeTestTokens } from 'components/inputs/composite/CompositeGroupReadOnly.fixtures';
import { testTokens } from 'store/tokens/__testdata';
import { Token } from 'store/tokens/types';
import DataUrlConstants from 'utils/DataUrlConstants';

const TOKENS_MAP: Record<string, Record<string, Token[]>> = {
  default: {
    Synapse: [
      {
        value: '5edfda13fee0d800011e25d3',
        label: 'Syncari Test Repo / Account / Description',
        shortLabel: 'Description',
        token: '{{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.description}}',
        datatype: 'string',
        group: 'Synapse',
      },
      {
        value: '60f72e289cd74f0001f9fda4',
        label: 'Syncari Test Repo / Account / Last Modified',
        shortLabel: 'Last Modified',
        token: '{{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.syncariLastModified}}',
        datatype: 'datetime',
        group: 'Synapse',
      },
      {
        value: '5edfda13fee0d800011e25d2',
        label: 'Syncari Test Repo / Account / Name',
        shortLabel: 'Name',
        token: '{{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.name}}',
        datatype: 'string',
        group: 'Synapse',
      },
      {
        value: '60f72e289cd74f0001f9fda3',
        label: 'Syncari Test Repo / Account / Reversed Name',
        shortLabel: 'Reversed Name',
        token: '{{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.reversed_name}}',
        datatype: 'string',
        group: 'Synapse',
      },
      {
        value: '60f72e289cd74f0001f9fda5',
        label: 'Syncari Test Repo / Account / Syncari ID',
        shortLabel: 'Syncari ID',
        token: '{{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.syncariid}}',
        datatype: 'string',
        group: 'Synapse',
      },
      {
        value: '5edfdab33b501f0001a22f9c',
        label: 'Syncari Test Repo / Account / SyncariRecordId',
        shortLabel: 'SyncariRecordId',
        token: '{{Syncari Test Repo.1R-IxCymhQvEnQjNVwh4vSGjYWHOxlYPG.syncarirecordid}}',
        datatype: 'string',
        group: 'Synapse',
      },
      {
        value: '5ed01f0fdab33b5001a22f9c',
        label: 'Syncari Test Repo / Account / SyncariRecordId',
        shortLabel: 'SyncariRecordId',
        token: '{{record.values.syncarirecordid}}',
        datatype: 'string',
        group: 'Synapse',
      },
      {
        value: '5ed01f0fdab33b5001a22f9c',
        label: 'Syncari Test Repo / Account / SyncariRecordId',
        shortLabel: 'Last Modified',
        token: '{{record.values.lastModified}}',
        datatype: 'string',
        group: 'Synapse',
      },
    ],
  },
  ...testTokens, // state for tokentextarea tests
  ...compositeTestTokens, /// state for composite group read only
};

const handlers = [
  rest.post(makeTestUrl(DataUrlConstants.TOKENS_FOR_NODE), (req, res, ctx) => {
    const nodeId = req.params.nodeId as string;
    const tokensData = nodeId in TOKENS_MAP ? TOKENS_MAP[nodeId] : TOKENS_MAP['default'];
    return res(ctx.status(200), ctx.json(tokensData));
  }),
];

export default handlers;
