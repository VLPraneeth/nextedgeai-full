import { Alert, Button, message } from 'antd';
import { useEffect, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useGetAllCustomSynapseListQuery } from 'store/custom-synapse/sdk/api';
import { showCustomSdkSynapseSharePanel } from 'store/custom-synapse/sdk/slice';
import { updateSDKCustomSynapse } from 'store/custom-synapse/sdk/thunks';
import AppConstants from 'utils/AppConstants';

import './CustomSdkSynapseSharePanel.scss';

export const CustomSdkSynapseSharePanel = withI18n(() => {
  const { tc, tn } = useI18nContext();
  const { refetch: refreshcustomSynapseList } = useGetAllCustomSynapseListQuery();
  const dispatch = useEnhancedDispatch();
  const { visible, customSynapse } = useEnhancedSelector((state) => state.customSynapse.customSdkSynapseSharePanel);

  const [globalShareToggle, setGlobalShareToggle] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    setGlobalShareToggle(customSynapse?.publishToGlobal ?? false);
  }, [customSynapse?.publishToGlobal]);

  const handleGlobalShareToggle = () => {
    setGlobalShareToggle((previousState) => !previousState);
  };

  const handleClose = () => {
    setGlobalShareToggle(false);
    dispatch(showCustomSdkSynapseSharePanel({ visible: false, customSynapse: null }));
  };

  const handleSave = async () => {
    if (customSynapse) {
      const response = await dispatch(
        updateSDKCustomSynapse({
          connectorMetaDefinitionId: customSynapse?.id,
          connectorMetaName: customSynapse?.name,
          connectorMetaDisplayName: customSynapse?.displayName,
          publishToGlobal: globalShareToggle,
        })
      );

      if (response?.payload) {
        if (response.payload.message) {
          setErrorMessage(response.payload.message);
        } else {
          refreshcustomSynapseList();

          message.success(tn('save_successful', { displayName: customSynapse.displayName }));
          handleClose();
        }
      } else {
        setErrorMessage(tn('unknown_error'));
      }
    }
  };

  const footer = (
    <>
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button type="primary" onClick={handleSave}>
        {tc('save')}
      </Button>
    </>
  );

  return (
    <DrawerPanel
      title={tn('share')}
      visible={visible}
      onClose={handleClose}
      footer={footer}
      className="custom-sdk-synapse-share-panel">
      {errorMessage && <Alert message={errorMessage} type="error" showIcon />}
      <InputWithLabel
        name="shareGlobally"
        datatype={AppConstants.INPUT_TYPE.CHECKBOX}
        label={tn('request_to_share_globally')}
        checked={globalShareToggle}
        onChange={handleGlobalShareToggle}
        tooltip={tn('global_share_tooltip')}
      />
      <span className="custom-sdk-synapse-share-panel__help-text">{tn('global_share_warning')}</span>
    </DrawerPanel>
  );
}, 'CustomSynapse');
