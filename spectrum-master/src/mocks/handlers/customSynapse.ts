import { makeTestUrl } from 'mocks/utils';
import { rest } from 'msw';

import { SDKCustomSynapseFunctionDeployStatuses } from 'store/custom-synapse/types';
import DataUrlConstants from 'utils/DataUrlConstants';

const customSynapseFunctionStatus: Record<
  SDKCustomSynapseFunctionDeployStatuses,
  SDKCustomSynapseFunctionDeployStatuses
> = {
  ACTIVE: SDKCustomSynapseFunctionDeployStatuses.ACTIVE,
  DEPLOY_IN_PROGRESS: SDKCustomSynapseFunctionDeployStatuses.DEPLOY_IN_PROGRESS,
  DELETE_IN_PROGRESS: SDKCustomSynapseFunctionDeployStatuses.DELETE_IN_PROGRESS,
  ERROR: SDKCustomSynapseFunctionDeployStatuses.ERROR,
};

const handlers = [
  rest.get(makeTestUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_STATUS), (req, res, ctx) => {
    const { connectorMetaDefinitionId } = req.params;
    const status = (connectorMetaDefinitionId ||
      SDKCustomSynapseFunctionDeployStatuses.ACTIVE) as SDKCustomSynapseFunctionDeployStatuses;

    const tokensData = customSynapseFunctionStatus[status];
    return res(ctx.status(200), ctx.json({ code: tokensData }));
  }),

  // Empty set of custom synapses, this could be exanded to include custom
  // synapses in the future
  rest.get(makeTestUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE), (req, res, ctx) => {
    return res(ctx.status(200), ctx.json([]));
  }),
];

export default handlers;
