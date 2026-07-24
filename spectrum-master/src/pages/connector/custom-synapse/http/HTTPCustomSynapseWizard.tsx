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

import { httpCustomSynapseItemPath } from '../CustomSynapseBreadcrumb';
import HTTPCustomSynapseContent from './HTTPCustomSynapseContent';
// TODO: These styles are used for all new wizards. Consider moving this less
// file to a higher level and making the naming more generic (not QS specific).
import '../../../sync-studio/entity/quick-start/QuickStartWizard.less';

export interface HTTPCustomSynapseWizardProps extends RouteComponentProps {
  close?: () => void;
}

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');

const HTTPCustomSynapseWizard = ({ close }: HTTPCustomSynapseWizardProps) => {
  const httpSynapseMatch = useMatch(httpCustomSynapseItemPath);
  const isNewSynapse = httpSynapseMatch?.id === 'new';

  const { data: customSynapse, isFetching, refetch } = useGetCustomSynapseItemQuery(
    { connectorMetaDefinitionId: httpSynapseMatch?.id },
    {
      skip: !httpSynapseMatch?.id || isNewSynapse,
    }
  );

  useEffect(() => {
    refetch();
  }, [refetch]);

  const panelTitle = customSynapse?.id ? tn('edit_http_synapse') : tn('create_http_synapse');

  const visible = !!customSynapse?.id || isNewSynapse;

  return (
    <DrawerPanel
      className="synri-config-full-content"
      keyboard={false}
      maskClosable={false}
      noPadding
      onClose={close}
      title={panelTitle}
      visible={visible}
      width="full">
      {isFetching || !visible ? (
        <HStack justify="center">
          <Spinner />
        </HStack>
      ) : (
        <HTTPCustomSynapseContent customSynapse={isNewSynapse ? undefined : customSynapse} close={close} />
      )}
    </DrawerPanel>
  );
};

export default HTTPCustomSynapseWizard;
