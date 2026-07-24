import { Alert, message } from 'antd';
import { useEffect, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import InfoBox from 'components/InfoBox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { useEnhancedDispatch } from 'hooks/redux';
import { useGetAllCustomSynapseListQuery } from 'store/custom-synapse/sdk/api';
import { updateSDKCustomSynapse } from 'store/custom-synapse/sdk/thunks';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import AppConstants from 'utils/AppConstants';

import { SDKCustomSynapseFileState } from './SDKCustomSynapseFileUpload';
import { SDKCustomSynapseSharingFooter } from './SDKCustomSynapseSharingFooter';

export interface SDKCustomSynapseSharingOptionsProps extends SkullRenderTypeBaseProps {
  defaultValue: SDKCustomSynapseFileState;
}

export const SDKCustomSynapseSharingOptions = withI18n(
  ({ defaultValue, onChange }: SDKCustomSynapseSharingOptionsProps) => {
    const { tn } = useI18nContext();
    const dispatch = useEnhancedDispatch();
    const { refetch: refreshcustomSynapseList } = useGetAllCustomSynapseListQuery();
    const { roles } = useUserRolesForCurrentInstance();

    const [formData, setFormData] = useState({ ...defaultValue });
    const [gloablShareToggle, setGlobalShareToggle] = useState(defaultValue?.publishToGlobal);
    const [errorMessage, setErrorMessage] = useState('');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
      setFormData((previousState) => ({
        ...previousState,
        publishToGlobal: gloablShareToggle,
      }));
    }, [gloablShareToggle]);

    const updateDefaultValue = () => {
      [
        'synapseFileUpload',
        'customSynapseAuthenticationTest',
        'customSynapseSharingOptions',
        'customSynapseReview',
      ].forEach((name) => {
        onChange({ name, value: formData });
      });
    };

    const handleGlobalShareToggle = () => {
      setGlobalShareToggle((previousState) => !previousState);
    };

    const handlePrevious = (gotoPrevious: () => void) => {
      updateDefaultValue();
      gotoPrevious();
    };

    const handleNext = async (gotoNext: () => void) => {
      setLoading(true);
      if (!formData.isGlobal) {
        const response = await dispatch(
          updateSDKCustomSynapse({
            connectorMetaDefinitionId: formData.id,
            connectorMetaName: formData.name,
            connectorMetaDisplayName: formData.displayName,
            publishToGlobal: formData.publishToGlobal,
          })
        );

        if (response?.payload) {
          if (response.payload.message) {
            setErrorMessage(response.payload.message);
          } else {
            refreshcustomSynapseList();

            message.success(tn('save_successful', { displayName: formData.displayName }));

            updateDefaultValue();
            gotoNext();
          }
        } else {
          setErrorMessage(tn('unknown_error'));
        }
      } else {
        gotoNext();
      }
      setLoading(false);
    };

    const shareGloballyFallback = !roles.superAdmin ? (
      <span>{tn('global_share_insufficient_permissions')}</span>
    ) : undefined;

    return (
      <Stack>
        <InfoBox
          message={tn('sharing_config_title')}
          description={
            <>
              <span>{tn('sharing_config_description')}</span>
              <br />
              <span>{tn('sharing_config_default_behavior')}</span>
            </>
          }
        />
        {errorMessage && <Alert message={errorMessage} type="error" showIcon />}
        <InputWithLabel
          name="shareGlobally"
          label={tn('request_to_share_globally')}
          datatype={AppConstants.INPUT_TYPE.BOOLEAN}
          value={gloablShareToggle}
          onChange={handleGlobalShareToggle}
          input={shareGloballyFallback}
          disabled={formData.isGlobal}
          validateStatus={formData.isGlobal ? undefined : 'error'}
          help={
            formData.isGlobal
              ? tn('global_share_unmutable', { displayName: formData.displayName })
              : tn('global_share_warning')
          }
          tooltip={tn('global_share_tooltip')}
        />
        <SDKCustomSynapseSharingFooter onPrevious={handlePrevious} onNext={handleNext} loadingNext={loading} />
      </Stack>
    );
  },
  'CustomSynapse'
);
