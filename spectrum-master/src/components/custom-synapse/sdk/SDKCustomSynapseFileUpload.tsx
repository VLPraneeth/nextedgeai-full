//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Alert, Button, message, Spin } from 'antd';
import { RcFile } from 'antd/lib/upload';
import { isEqual, pick } from 'lodash';
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { SUPPORTED_CUSTOM_SYNAPSE_ICON_FORMATS } from 'components/imageUpload/ImageUpload';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack, Spacer, Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { useEnhancedDispatch } from 'hooks/redux';
import { useGetSyncariSdkInfoQuery } from 'store/connectors/api';
import { useGetAllCustomSynapseListQuery, useLazyGetCustomSynapseStatusQuery } from 'store/custom-synapse/sdk/api';
import { createSDKCustomSynapse, updateSDKCustomSynapse } from 'store/custom-synapse/sdk/thunks';
import { SDKCustomSynapseFunctionDeployStatuses } from 'store/custom-synapse/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadGetFile } from 'utils/DownloadUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { createApiName } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';
import useSetState from 'utils/useSetState';

import SingleFileUploadBox from '../../SingleFileUploadBox';
import { SDKCustomSynapseFileUploadFooter } from './SDKCustomSynapseFileUploadFooter';

import './SDKCustomSynapseFileUpload.less';

const REQUIREMENTS_FILE_NAME = 'requirements.txt';
export const DEFAULT_CUSTOM_SYNAPSE_ICON = '/assets/icons/custom-synapse-default.svg';

export interface SDKCustomSynapseFileState {
  id?: string;
  name: string;
  displayName: string;
  publishToGlobal: boolean;
  isGlobal: boolean;
  synapseFile?: RcFile;
  requirementsFile?: RcFile;
  iconFile?: RcFile;
}

const initialState: SDKCustomSynapseFileState = {
  id: '',
  name: '',
  displayName: '',
  publishToGlobal: false,
  isGlobal: false,
};

export interface SkullFileUploadProps extends SkullRenderTypeBaseProps {
  defaultValue: SDKCustomSynapseFileState;
}

const tnCustomSynapse = tNamespaced('CustomSynapse');

const SDKCustomSynapseFileUpload = ({ onChange, defaultValue }: SkullFileUploadProps) => {
  const [processing, setProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [showUpload, setShowUpload] = useState(!defaultValue.id);
  const [downloading, setDownloading] = useState(false);

  const { data: sdkInfo, isLoading: loadingSdkVersion } = useGetSyncariSdkInfoQuery();

  const [formData, setFormData] = useSetState(() => {
    return { ...initialState, ...defaultValue };
  });

  // TODO: this should not be necessary if we can get an updated defaultValue
  // when formData is submitted
  const [mostRecentFormData, setMostRecentFormData] = useState<Partial<SDKCustomSynapseFileState>>({});

  /**
   * returns true when the formData doesn't match the initial state for fields
   * in the form
   */
  const hasChanges = useMemo(() => {
    const editableFormFields = ['name', 'displayName', 'synapseFile', 'requirementsFile', 'iconFile'];
    const currentData = pick(formData, editableFormFields);
    const initialData = pick({ ...initialState, ...defaultValue, ...mostRecentFormData }, editableFormFields);
    return !isEqual(currentData, initialData);
  }, [defaultValue, formData, mostRecentFormData]);

  useEffect(() => {
    onChange(formData);
  }, [formData, onChange]);

  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();

  const [fetchCustomSynapseStatus, { data: cloudFunctionStatus }] = useLazyGetCustomSynapseStatusQuery({
    pollingInterval: 5000,
  });

  const { refetch: refreshcustomSynapseList } = useGetAllCustomSynapseListQuery();

  useEffect(() => {
    if (
      cloudFunctionStatus?.code === SDKCustomSynapseFunctionDeployStatuses.ACTIVE ||
      cloudFunctionStatus?.code === SDKCustomSynapseFunctionDeployStatuses.ERROR
    ) {
      setProcessing(false);
    } else if (cloudFunctionStatus?.code) {
      setProcessing(true);
    }
  }, [cloudFunctionStatus]);

  useEffect(() => {
    if (formData.id) {
      fetchCustomSynapseStatus({ connectorMetaDefinitionId: formData.id });
    }
  }, [fetchCustomSynapseStatus, formData.id]);

  const validate = useCallback(() => {
    if (!formData.name?.trim() || !formData.displayName?.trim()) {
      setErrorMessage(tn('input_synapse_details'));
      return false;
    }

    /**
     * Both the main and the requirements files should be selected when creating
     * a new custom synapse. When updating, both files need to be selected OR
     * neither should be selected.
     */
    const synapseFileSelected = !!formData.synapseFile;
    const requirementsFileSelected = !!formData.requirementsFile;

    const bothFilesSelected = synapseFileSelected && requirementsFileSelected;

    const filesMissingForCreate = !formData.id && !bothFilesSelected;
    const onlyOneFileSelected = synapseFileSelected !== requirementsFileSelected;
    const filesMissingForUpload = !!formData.id && onlyOneFileSelected;
    if (filesMissingForCreate || filesMissingForUpload) {
      setErrorMessage(tn('select_files_before_uploading'));
      return false;
    }
    setErrorMessage('');

    return true;
  }, [formData, tn]);

  const onSave = useCallback(async () => {
    if (validate()) {
      setProcessing(true);

      const actionMethod = formData.id ? updateSDKCustomSynapse : createSDKCustomSynapse;

      const response = await dispatch(
        actionMethod({
          connectorMetaDefinitionId: formData.id,
          connectorMetaName: formData.name,
          connectorMetaDisplayName: formData.displayName,
          synapseFile: formData.synapseFile as RcFile,
          requirementsFile: formData.requirementsFile as RcFile,
          iconFile: formData.iconFile as RcFile,
          publishToGlobal: formData.publishToGlobal,
        })
      );

      if (formData.id && (!formData.synapseFile || !formData.requirementsFile)) {
        setProcessing(false);
      }

      if (response?.payload) {
        if ('message' in response.payload) {
          setErrorMessage(response.payload.message || 'An unknown error occurred. Please try again.');
          setProcessing(false);
        } else if ('id' in response.payload) {
          refreshcustomSynapseList();

          message.success(
            formData.id
              ? `${formData.displayName} has successfully saved.`
              : `${formData.displayName} has been created.`
          );

          // TODO: Set the wizard title to be "Edit (Display name)"
          setFormData({ id: response.payload.id });

          // Update the values for all the other inputs in the wizard
          [
            'synapseFileUpload',
            'customSynapseAuthenticationTest',
            'customSynapseSharingOptions',
            'customSynapseReview',
          ].forEach((name) => {
            onChange({ name, value: response.payload });
          });

          // Update our internal state for determining hasChanges
          setMostRecentFormData(formData);
        }
      } else {
        setErrorMessage('An unknown error occurred. Please try again.');
        setProcessing(false);
      }
    }
  }, [dispatch, formData, onChange, refreshcustomSynapseList, setFormData, validate]);

  const showSpinner =
    !cloudFunctionStatus?.code ||
    cloudFunctionStatus?.code === SDKCustomSynapseFunctionDeployStatuses.DELETE_IN_PROGRESS ||
    cloudFunctionStatus?.code === SDKCustomSynapseFunctionDeployStatuses.DEPLOY_IN_PROGRESS;

  const alertStatusMap: Record<SDKCustomSynapseFunctionDeployStatuses, 'error' | 'warning' | 'info' | 'success'> = {
    DEPLOY_IN_PROGRESS: 'info',
    DELETE_IN_PROGRESS: 'info',
    ACTIVE: 'success',
    ERROR: 'error',
  };

  const defaultCustomIconUrl = formData.id
    ? makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_ICON, {
        connectorMetaDefinitionId: formData.id,
      })
    : DEFAULT_CUSTOM_SYNAPSE_ICON;

  let alertMessage = tn('loading_status');
  if (cloudFunctionStatus?.code) {
    alertMessage = cloudFunctionStatus?.errorStatusMessage
      ? tn(`server_error_message`, { errorMessage: cloudFunctionStatus?.errorStatusMessage })
      : tn(`status_label_${cloudFunctionStatus?.code}`);
  }

  return (
    <Stack className="custom-synapse-upload-container">
      {sdkInfo?.version && !formData.id && (
        <Alert
          banner
          type="info"
          message={
            <HStack>
              {tn('latest_stable_version', { version: sdkInfo.version })}
              <Spacer flex />
              {loadingSdkVersion && <Spin size="small" />}
            </HStack>
          }
        />
      )}

      {formData.id && (
        <Alert
          banner
          type={cloudFunctionStatus?.code ? alertStatusMap[cloudFunctionStatus.code] : 'info'}
          message={
            <HStack>
              {alertMessage}
              <Spacer flex />
              {showSpinner && <Spin size="small" />}
            </HStack>
          }
        />
      )}
      {errorMessage && <Alert message={errorMessage} type="error" showIcon />}

      <InputWithLabel
        label={tn('connector_meta_display_name')}
        required
        value={formData.displayName}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setFormData({ displayName: newName.target.value });
        }}
        onBlur={() => {
          if (formData.displayName && !formData.id && !formData.name) {
            setFormData({ name: createApiName(formData.displayName) });
          }
        }}
      />

      <InputWithLabel
        label={tn('connector_meta_name')}
        // The name is not editable except when creating a new custom synapse
        disabled={!!formData.id}
        required
        value={formData.name}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setFormData({ name: createApiName(newName.target.value) });
        }}
      />

      <InputWithLabel
        className="logo-upload"
        tooltip={tnCustomSynapse('custom_synapse_icon_tooltip')}
        defaultValue={defaultCustomIconUrl}
        datatype="image"
        id="iconFile"
        name="iconFile"
        label={tn('custom_icon')}
        accept={SUPPORTED_CUSTOM_SYNAPSE_ICON_FORMATS}
        value={formData.iconFile}
        onChange={(iconFile: RcFile) => {
          setFormData({ iconFile });
        }}
      />

      {showUpload && (
        <Stack>
          <SingleFileUploadBox
            required
            file={formData.synapseFile}
            selectButtonText={tn('upload_synapse_file')}
            helpText={tn('primary_synapse_file_description')}
            onRemove={() => {
              setFormData({ synapseFile: undefined });
            }}
            beforeUpload={(src) => {
              if (!src.name.includes('.py')) {
                setErrorMessage(tn('select_python_file'));
              } else if (src.size === 0) {
                setErrorMessage(tn('file_is_empty'));
              } else {
                setErrorMessage('');
                setFormData({ synapseFile: src });
              }
              return false;
            }}
          />

          <SingleFileUploadBox
            required
            file={formData.requirementsFile}
            selectButtonText={tn('upload_requirements_file')}
            helpText={tn('requirements_file_description')}
            onRemove={() => {
              setFormData({ requirementsFile: undefined });
            }}
            beforeUpload={(src) => {
              if (src.name !== REQUIREMENTS_FILE_NAME) {
                setErrorMessage(tn('file_name_match_exactly'));
              } else if (src.size === 0) {
                setErrorMessage(tn('file_is_empty'));
              } else {
                setErrorMessage('');
                setFormData({ requirementsFile: src });
              }
              return false;
            }}
          />
        </Stack>
      )}

      {!showUpload && Boolean(formData.id) && (
        <InputWithLabel
          label={tn('upload_files')}
          // The name is not editable except when creating a new custom synapse
          disabled={!!formData.id}
          value={formData.name}
          onChange={(newName: ChangeEvent<HTMLInputElement>) => {
            setFormData({ name: createApiName(newName.target.value) });
          }}
          input={
            <Button className="synri-reupload-link" type="link" onClick={() => setShowUpload(true)}>
              {tn('reupload_files')}
            </Button>
          }
        />
      )}
      {Boolean(formData.id) && (
        <Button
          size="small"
          loading={downloading}
          onClick={async () => {
            if (formData.id) {
              setDownloading(true);
              const url = makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_DOWNLOAD_FILES, {
                connectorMetaDefinitionId: formData.id,
              });
              await downloadGetFile(url, `${formData.name}_draft_download.zip`);

              setDownloading(false);
            }
          }}>
          {tn('download_all_files')}
        </Button>
      )}

      <SDKCustomSynapseFileUploadFooter
        processing={processing}
        hasChanges={hasChanges}
        disableNextButton={
          (!!formData.id && !cloudFunctionStatus?.code) ||
          (!hasChanges && cloudFunctionStatus?.code === SDKCustomSynapseFunctionDeployStatuses.ERROR)
        }
        editing={!!formData.id}
        onSave={onSave}
      />
    </Stack>
  );
};

export default withI18n(SDKCustomSynapseFileUpload, 'CustomSynapseFileUpload');
