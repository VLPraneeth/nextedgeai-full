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

import { webhookCustomSynapseItemPath } from '../CustomSynapseBreadcrumb';
import WebhookCustomSynapseContent from './WebhookCustomSynapseContent';

// TODO: These styles are used for all new wizards. Consider moving this less
// file to a higher level and making the naming more generic (not QS specific).
import '../../../sync-studio/entity/quick-start/QuickStartWizard.less';

export interface HTTPCustomSynapseWizardProps extends RouteComponentProps {
  close?: () => void;
}

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

const WebhookCustomSynapseWizard = ({ close }: HTTPCustomSynapseWizardProps) => {
  const webhookSynapseMatch = useMatch(webhookCustomSynapseItemPath);
  const isNewSynapse = webhookSynapseMatch?.id === 'new';

  const { data: customSynapse, isFetching, refetch } = useGetCustomSynapseItemQuery(
    { connectorMetaDefinitionId: webhookSynapseMatch?.id },
    {
      skip: !webhookSynapseMatch?.id || isNewSynapse,
    }
  );

  useEffect(() => {
    refetch();
  }, [refetch]);

  const panelTitle = customSynapse?.id ? tn('edit_webhook_synapse') : tn('create_webhook_synapse');

  const visible = !!customSynapse?.id || isNewSynapse;

  return (
    <DrawerPanel
      className="synri-config-full-content"
      wrapClassName="webhook_custom_synapse_modal"
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
        <WebhookCustomSynapseContent customSynapse={isNewSynapse ? undefined : customSynapse} close={close} />
      )}
    </DrawerPanel>
  );
};

export default WebhookCustomSynapseWizard;
