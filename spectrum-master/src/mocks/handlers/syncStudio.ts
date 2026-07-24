import { rest } from 'msw';

import DataUrlConstants from 'utils/DataUrlConstants';

const data = [
  {
    nodes: [
      {
        apiName: 'lead',
        configuration: { realtime: false, entityDefinition: '5f5a97ba0a3b3c000160641b', enableDeduplicate: false },
        iconPath: null,
        id: '61e99ab092800e0001601941',
        inputPorts: [{ portType: 'INPUT', datatype: 'object', maxConnections: 2147483647 }],
        label: 'Lead',
        location: { x: '692', y: '427' },
        name: 'Lead',
        nodeType: 'CORE_ENTITY',
        outputPorts: [{ portType: 'OUTPUT', datatype: 'object', maxConnections: 2147483647 }],
        subLabel: 'Syncari',
      },
    ],
    edges: [],
    scope: 'ENTITY',
    name: 'User',
    draftStatus: 'NEW',
    draft: null,
  },
];

const handlers = [
  rest.get(`${process.env.REACT_APP}/${DataUrlConstants.PIPELINE}/search/lead`, (req, res, ctx) => {
    return res(ctx.status(200), ctx.json(data));
  }),
];

export default handlers;
