import { Button, Icon, message, Modal } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import InfoBox from 'components/InfoBox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import {
  useGetCustomSynapseShareStatusQuery,
  useGetCustomSynapseSharingScopeQuery,
  useShareCustomSynapseMutation,
} from 'store/custom-synapse/http/api';
import { showCustomSynapseSharePanel } from 'store/custom-synapse/sdk/slice';
import { CustomSynapseShareScopeType } from 'store/custom-synapse/types';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import AppConstants from 'utils/AppConstants';
import CapConstants from 'utils/CapConstants';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced, tc } from 'utils/i18nUtil';

import { SelectedInstanceScope } from './SelectedInstanceScope';

import './CustomSynapseSharePanel.scss';

const tn = tNamespaced('CustomSynapse');

export const CustomSynapseSharePanel = () => {
  const dispatch = useEnhancedDispatch();
  const { visible, customSynapse } = useEnhancedSelector((state) => state.customSynapse.customSynapseSharePanel);

  const publishedSynapseId = useMemo(() => {
    return customSynapse?.parentId || customSynapse?.id;
  }, [customSynapse]);

  const { data: scopes, isLoading: scopesLoading } = useGetCustomSynapseSharingScopeQuery();
  const [share, { isLoading: isSharing }] = useShareCustomSynapseMutation();
  const { data: shareStatus, isLoading } = useGetCustomSynapseShareStatusQuery(publishedSynapseId!, {
    skip: !publishedSynapseId,
  });
  const [selectedScope, setSelectedScope] = useState<CustomSynapseShareScopeType>('PRIVATE');
  const [selectedInstances, setSelectedInstances] = useState<string[]>([]);

  const { userCan } = useUserRolesForCurrentInstance();
  const isSuperAdmin = userCan([CapConstants.SUPER_ADMIN]);

  const sharingDisabled = (shareStatus?.scope === 'GLOBAL' || selectedScope === 'GLOBAL') && !isSuperAdmin;
  const dropdownDisabled = shareStatus?.scope === 'GLOBAL' && !isSuperAdmin;

  useEffect(() => {
    setSelectedScope(shareStatus?.scope || 'PRIVATE');
    if (shareStatus?.scope === 'SELECTED_INSTANCES') {
      setSelectedScope(shareStatus.scope);
      setSelectedInstances(shareStatus.instances || []);
    }
  }, [shareStatus, scopes, visible]);

  const handleClose = useCallback(() => {
    dispatch(showCustomSynapseSharePanel({ visible: false, customSynapse: null }));
    setSelectedScope('PRIVATE');
    setSelectedInstances([]);
  }, [dispatch]);

  const handleSave = useCallback(async () => {
    if (!publishedSynapseId || !selectedScope) {
      return;
    }
    share({
      shareOptions: {
        scope: selectedScope,
        instances: selectedScope === 'SELECTED_INSTANCES' ? selectedInstances : undefined,
      },
      id: publishedSynapseId,
    })
      .unwrap()
      .then((res) => {
        message.success(tn('sharing_success', { name: customSynapse?.displayName }));
      })
      .catch((err) => {
        message.error(getRtkQueryErrorMessage(err));
      })
      .finally(() => {
        handleClose();
      });
  }, [customSynapse, handleClose, publishedSynapseId, selectedInstances, selectedScope, share]);

  const confirmChangeSharingScope = useCallback(() => {
    Modal.confirm({
      title: tn('change_sharing_scope_title'),
      content: <div>{tn('change_sharing_scope_body')}</div>,
      onOk: () => handleSave().finally(handleClose),
      okText: tc('share'),
      icon: <Icon type="exclamation-circle" />,
      okType: 'primary',

      okButtonProps: { type: 'primary', loading: isSharing },
    });
  }, [handleClose, handleSave, isSharing]);

  const shareOptions: Record<CustomSynapseShareScopeType, any> = useMemo(() => {
    let helpText = '';
    if (shareStatus?.scope === 'GLOBAL' && !isSuperAdmin) {
      helpText = tn('global_scope_help_text_user');
    } else if ((shareStatus?.scope === 'GLOBAL' || selectedScope === 'GLOBAL') && isSuperAdmin) {
      helpText = tn('global_scope_help_text_admin');
    } else {
      helpText = scopes?.find((sp) => sp.id === selectedScope)?.helpText || '';
    }

    return {
      GLOBAL: <HelpText text={helpText} />,
      PRIVATE: <HelpText text={helpText} />,
      SELECTED_INSTANCES: (
        <SelectedInstanceScope selectedInstances={selectedInstances} setSelectedInstances={setSelectedInstances} />
      ),
      SUBSCRIPTION: <HelpText text={helpText} />,
    };
  }, [selectedInstances, scopes, selectedScope, shareStatus?.scope, isSuperAdmin]);

  const onSave = useCallback(() => {
    if (
      !shareStatus?.scope ||
      shareStatus?.scope === 'PRIVATE' ||
      shareStatus?.scope === selectedScope ||
      selectedScope === 'GLOBAL'
    ) {
      handleSave();
    } else {
      confirmChangeSharingScope();
    }
  }, [confirmChangeSharingScope, handleSave, selectedScope, shareStatus?.scope]);

  const footer = (
    <>
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button type="primary" onClick={onSave} disabled={sharingDisabled} loading={isSharing}>
        {tc('save')}
      </Button>
    </>
  );

  return (
    <DrawerPanel
      title={tn('sharing_title', { name: customSynapse?.displayName })}
      visible={visible}
      onClose={handleClose}
      footer={footer}
      width="xlarge"
      destroyOnClose
      className="custom-synapse-share-panel">
      <InputWithLabel
        label={tn('sharing_scope')}
        value={selectedScope}
        onChange={(scope: CustomSynapseShareScopeType) => {
          setSelectedScope(scope);
        }}
        disabled={dropdownDisabled}
        loading={isLoading || scopesLoading}
        datatype={AppConstants.INPUT_TYPE.PICKLIST}
        optionData={scopes?.map((scope) => ({
          label: scope.name,
          value: scope.id,
        }))}
      />

      {selectedScope && shareOptions[selectedScope]}
    </DrawerPanel>
  );
};

function HelpText({ text }: { text: string }) {
  return <InfoBox message={text} type="info" showIcon />;
}
