//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps, useMatch } from '@reach/router';
import { useEffect } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { HStack } from 'components/layout';
import Spinner from 'components/Spinner';
import { useGetCustomSynapseItemQuery } from 'store/custom-synapse/sdk/api';
import { tNamespaced } from 'utils/i18nUtil';

import { sdkCustomSynapseItemPath } from '../CustomSynapseBreadcrumb';
import SDKCustomSynapseContent from './SDKCustomSynapseContent';
// TODO: These styles are used for all new wizards. Consider moving this less
// file to a higher level and making the naming more generic (not QS specific).
import '../../../sync-studio/entity/quick-start/QuickStartWizard.less';

export interface SDKCustomSynapseWizardProps extends RouteComponentProps {
  close?: () => void;
}

const tn = tNamespaced('CustomSynapse');

const SDKCustomSynapseWizard = ({ close }: SDKCustomSynapseWizardProps) => {
  const sdkSynapseMatch = useMatch(sdkCustomSynapseItemPath);

  const { data: customSynapse, isFetching, refetch } = useGetCustomSynapseItemQuery(
    { connectorMetaDefinitionId: sdkSynapseMatch?.id },
    {
      skip: !sdkSynapseMatch?.id || sdkSynapseMatch?.id === 'new',
    }
  );

  useEffect(() => {
    refetch();
  }, [refetch]);

  const panelTitle = customSynapse?.id
    ? tn('edit_synapse_title', { name: customSynapse.displayName, interpolation: { escapeValue: false } })
    : tn('title');

  return (
    <DrawerPanel
      className="synri-config-full-content"
      keyboard={false}
      maskClosable={false}
      noPadding
      onClose={close}
      title={panelTitle}
      visible={!!customSynapse?.id || sdkSynapseMatch?.id === 'new'}
      width="full">
      {isFetching ? (
        <HStack justify="center">
          <Spinner />
        </HStack>
      ) : (
        <SDKCustomSynapseContent customSynapse={customSynapse} close={close} />
      )}
    </DrawerPanel>
  );
};

export default SDKCustomSynapseWizard;
