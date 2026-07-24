import { RouteComponentProps } from '@reach/router';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';

export const webhookCustomSynapseItemPath = '/synapses/custom-synapses/webhook/:id';
export const httpCustomSynapseItemPath = '/synapses/custom-synapses/http/:id';
export const sdkCustomSynapseItemPath = '/synapses/custom-synapses/sdk/:id';
export const entitiesBasePath = '/synapses/custom-synapses/http/:synapseId/entities/:version/*';
export const entitiesItemPath = '/synapses/custom-synapses/http/:synapseId/entities/:version/:entityId';

const tn = tNamespaced('ConnectorEditor');

export const CustomSynapseBreadcrumb = (props: RouteComponentProps) => {
  return (
    <>
      <BreadcrumbLink to={RouteConstants.SYNAPSES}>{tn('title')}</BreadcrumbLink>
      <BreadcrumbSeparator />
      <BreadcrumbLink to={RouteConstants.SYNAPSES_CUSTOM}>{tn('custom_synapses')}</BreadcrumbLink>
    </>
  );
};
