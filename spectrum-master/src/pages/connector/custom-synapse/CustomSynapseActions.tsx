import { navigate } from '@reach/router';
import { Menu, Modal, message } from 'antd';
import { useCallback, useMemo, useState } from 'react';

import { getConnectorsMetadata } from 'actions/connectorActions';
import Can from 'components/Can';
import { CustomSynapse } from 'components/custom-synapse/types';
import KebabMenu, { MenuDivider } from 'components/KebabMenu';
import Spinner from 'components/Spinner';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import {
  useCreateDraftHttpCustomSynapseMutation,
  useDiscardDraftHttpCustomSynapseMutation,
  usePublishHttpCustomSynapseMutation,
} from 'store/custom-synapse/http/api';
import {
  useApproveCustomSynapseMutation,
  useCreateCustomSynapseDraftMutation,
  useDiscardCustomSynapseDraftMutation,
} from 'store/custom-synapse/sdk/api';
import {
  showCustomSynapseApprovalModal,
  showCustomSdkSynapseSharePanel,
  showCustomSynapseSharePanel,
} from 'store/custom-synapse/sdk/slice';
import { CustomSynapseDraftStatuses } from 'store/custom-synapse/types';
import {
  useCreateDraftWebhookCustomSynapseMutation,
  useDiscardDraftWebhookCustomSynapseMutation,
  usePublishWebhookCustomSynapseMutation,
} from 'store/custom-synapse/webhook/api';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadGetFile } from 'utils/DownloadUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useSDKCustomSynapseStudio } from './sdk/SDKCustomSynapses.hook';

interface CustomSynapseActionsProps {
  customSynapse: CustomSynapse | undefined;
  showCustomSynapse: (synapse?: CustomSynapse | null) => void;
}

const tn = tNamespaced('CustomSynapse');

export function CustomSynapseActions({ customSynapse, showCustomSynapse }: CustomSynapseActionsProps) {
  const dispatch = useEnhancedDispatch();

  const [isProcessing, setIsProcessing] = useState(false);
  const { roles } = useUserRolesForCurrentInstance();
  const [menuOpen, setMenuOpen] = useState(false);

  const [discardSDKCustomSynapseDraft] = useDiscardCustomSynapseDraftMutation();
  const { deleteSynapse, submitForApproval, withdrawFromApproval } = useSDKCustomSynapseStudio();
  const [createSDKCustomSynapseDraft] = useCreateCustomSynapseDraftMutation();
  const [approveSDKSynapse] = useApproveCustomSynapseMutation();

  const [discardHTTPCustomSynapseDraft] = useDiscardDraftHttpCustomSynapseMutation();
  const [createHTTPCustomSynapseDraft] = useCreateDraftHttpCustomSynapseMutation();
  const [publishHTTPSynapse] = usePublishHttpCustomSynapseMutation();

  const [discardWebhookCustomSynapseDraft] = useDiscardDraftWebhookCustomSynapseMutation();

  const [createWebhookCustomSynapseDraft] = useCreateDraftWebhookCustomSynapseMutation();
  const [publishWebhookSynapse] = usePublishWebhookCustomSynapseMutation();

  const isPublished = customSynapse?.draftStatus === CustomSynapseDraftStatuses.APPROVED || !!customSynapse?.parentId;
  const isDraft = customSynapse?.draftStatus === CustomSynapseDraftStatuses.NEW;
  const isPendingApproval = customSynapse?.draftStatus === CustomSynapseDraftStatuses.SUBMIT_FOR_APPROVAL;
  const approvalInProgress = customSynapse?.draftStatus === CustomSynapseDraftStatuses.APPROVAL_IN_PROGRESS;
  const isGlobal = customSynapse?.isGlobal;
  const isSDKSynapse = useMemo(() => customSynapse?.customSynapseType === 'SDK', [customSynapse?.customSynapseType]);
  const isHTTPSynapse = useMemo(() => customSynapse?.customSynapseType === 'HTTP', [customSynapse?.customSynapseType]);
  const isWebhookSynapse = useMemo(() => customSynapse?.customSynapseType === 'WEBHOOK', [
    customSynapse?.customSynapseType,
  ]);

  const confirmDeleteCustomSynapse = useCallback(
    (synapseId: string, customSynapseName: string) => {
      Modal.confirm({
        title: tn('delete_title'),
        content: (
          <TranslatedText
            namespace="CustomSynapse"
            text="delete_custom_synapse_content"
            beDangerous
            args={{ name: customSynapseName }}
          />
        ),
        onOk: async () => {
          setIsProcessing(true);
          await deleteSynapse(synapseId);
          dispatch(getConnectorsMetadata());
          setIsProcessing(false);
        },
        okText: tc('delete'),
        okType: 'danger',
        okButtonProps: { type: 'danger' },
      });
    },
    [deleteSynapse, dispatch]
  );

  const confirmDeleteDraftCustomSynapse = useCallback(
    (customSynapseId: string, customSynapseName: string) => {
      Modal.confirm({
        title: tn('delete_draft_title'),
        content: (
          <TranslatedText
            namespace="CustomSynapse"
            text="delete_custom_synapse_draft_content"
            beDangerous
            args={{ name: customSynapseName }}
          />
        ),
        onOk: async () => {
          setIsProcessing(true);
          if (isSDKSynapse) {
            await discardSDKCustomSynapseDraft({ customSynapseId }).then((res: any) => {
              if (res?.error?.data?.message) {
                message.error(res?.error?.data?.message);
              }
            });
            dispatch(getConnectorsMetadata());
          } else if (isHTTPSynapse) {
            await discardHTTPCustomSynapseDraft(customSynapseId).then((res: any) => {
              if (res?.error?.data?.message) {
                message.error(res?.error?.data?.message);
              }
            });
            dispatch(getConnectorsMetadata());
          } else if (isWebhookSynapse) {
            await discardWebhookCustomSynapseDraft(customSynapseId).then((res: any) => {
              if (res?.error?.data?.message) {
                message.error(res?.error?.data?.message);
              }
            });
            dispatch(getConnectorsMetadata());
          }
          setIsProcessing(false);
        },
        okText: tc('delete'),
        okType: 'danger',
        okButtonProps: { type: 'danger' },
      });
    },
    [
      discardHTTPCustomSynapseDraft,
      discardSDKCustomSynapseDraft,
      dispatch,
      isSDKSynapse,
      isHTTPSynapse,
      isWebhookSynapse,
      discardWebhookCustomSynapseDraft,
    ]
  );

  if (!customSynapse) {
    return null;
  }

  const deleteCustomSynapse = (
    <Can key="delete" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        key="delete"
        disabled={approvalInProgress}
        onClick={async () => {
          setMenuOpen(false);
          confirmDeleteCustomSynapse(customSynapse.id, customSynapse.displayName);
        }}>
        <Text color="red-300">{tc('delete')}</Text>
      </Menu.Item>
    </Can>
  );

  const editDraft = (
    <Can permission={AllPermissions.WRITE_CONNECTOR} key="edit">
      <Menu.Item
        key="edit"
        disabled={approvalInProgress}
        onClick={() => {
          setMenuOpen(false);
          showCustomSynapse(customSynapse);
        }}>
        {tn('edit_synapse')}
      </Menu.Item>
    </Can>
  );

  const deleteDraft = (
    <Can key="delete_draft" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        disabled={approvalInProgress}
        key="delete_draft"
        onClick={async () => {
          setMenuOpen(false);
          confirmDeleteDraftCustomSynapse(customSynapse.id, customSynapse.displayName);
        }}>
        <Text color="red-300">{tc('delete_draft')}</Text>
      </Menu.Item>
    </Can>
  );

  const createDraftAndEdit = (
    <Can key="edit_custom_synapse" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        disabled={approvalInProgress}
        key="edit_custom_synapse"
        onClick={() => {
          setMenuOpen(false);
          setIsProcessing(true);

          if (isSDKSynapse) {
            createSDKCustomSynapseDraft({ customSynapseId: customSynapse.id })
              .unwrap()
              .then((response) => {
                setIsProcessing(false);
                showCustomSynapse({ ...response, customSynapseType: 'SDK' });
                dispatch(getConnectorsMetadata());
              })
              .catch((error) => {
                setIsProcessing(false);
                message.error(getRtkQueryErrorMessage(error));
              });
          } else if (isHTTPSynapse) {
            createHTTPCustomSynapseDraft(customSynapse.id)
              .unwrap()
              .then((response) => {
                setIsProcessing(false);
                showCustomSynapse({ ...response, customSynapseType: 'HTTP' });
                dispatch(getConnectorsMetadata());
              })
              .catch((error) => {
                setIsProcessing(false);
                message.error(getRtkQueryErrorMessage(error));
              });
          } else if (isWebhookSynapse) {
            createWebhookCustomSynapseDraft(customSynapse.id)
              .unwrap()
              .then((response) => {
                setIsProcessing(false);
                showCustomSynapse({ ...response, customSynapseType: 'WEBHOOK' });
                dispatch(getConnectorsMetadata());
              })
              .catch((error) => {
                setIsProcessing(false);
                message.error(getRtkQueryErrorMessage(error));
              });
          }
        }}>
        {tc('create_draft')}
      </Menu.Item>
    </Can>
  );

  const submitForApprovalMenu = (
    <Can key="submit_for_review" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        key="submit_for_review"
        disabled={approvalInProgress}
        onClick={async () => {
          setMenuOpen(false);
          setIsProcessing(true);
          await submitForApproval(customSynapse.id, customSynapse.displayName);
          setIsProcessing(false);
        }}>
        {tn('submit_for_review')}
      </Menu.Item>
    </Can>
  );

  const withdrawApproval = (
    <Can key="withdraw_review" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        key="withdraw_review"
        disabled={approvalInProgress}
        onClick={async () => {
          setMenuOpen(false);
          setIsProcessing(true);
          await withdrawFromApproval(customSynapse.id, customSynapse.displayName);
          setIsProcessing(false);
        }}>
        {tn('withdraw_review')}
      </Menu.Item>
    </Can>
  );

  const approveCustomSynapse = (
    <Can key="approve_custom_synapse" permission={AllPermissions.APPROVE_CUSTOM_SYNAPSE}>
      <Menu.Item
        key="approve_custom_synapse"
        disabled={approvalInProgress}
        onClick={async () => {
          setMenuOpen(false);
          if (customSynapse.publishToGlobal) {
            dispatch(showCustomSynapseApprovalModal({ visible: true, customSynapse }));
          } else {
            setIsProcessing(true);
            await approveSDKSynapse({ connectorMetaDefinitionId: customSynapse.id });
            await dispatch(getConnectorsMetadata());
            setIsProcessing(false);
          }
        }}>
        {tn('approve_custom_synapse')}
      </Menu.Item>
    </Can>
  );

  const publishDraftSynapseMenu = (
    <Can key="publish_custom_synapse" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        key="publish_custom_synapse"
        onClick={async () => {
          setIsProcessing(true);
          if (isHTTPSynapse) {
            await publishHTTPSynapse(customSynapse.id);
          } else if (isWebhookSynapse) {
            await publishWebhookSynapse(customSynapse.id);
          }

          setIsProcessing(false);
          dispatch(getConnectorsMetadata());
        }}>
        <Text>{tc('publish_draft')}</Text>
      </Menu.Item>
    </Can>
  );

  const downloadDraftFiles = (
    <Menu.Item
      key="download_draft_files"
      onClick={async () => {
        const url = makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_DOWNLOAD_FILES, {
          connectorMetaDefinitionId: customSynapse.id,
        });

        setIsProcessing(true);
        await downloadGetFile(url, `${customSynapse.name}_draft_download.zip`);
        setIsProcessing(false);
      }}>
      {tn('download_draft_files')}
    </Menu.Item>
  );

  // Use the id if the document is published, otherwise use the parentId if there is one
  const publishedFilesDownloadId =
    customSynapse.draftStatus === CustomSynapseDraftStatuses.APPROVED ? customSynapse.id : customSynapse.parentId;

  const downloadPublishedFiles = (
    <Menu.Item
      key="download_published_files"
      disabled={approvalInProgress}
      onClick={async () => {
        const url = makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_DOWNLOAD_FILES, {
          connectorMetaDefinitionId: publishedFilesDownloadId,
        });

        setIsProcessing(true);
        await downloadGetFile(url, `${customSynapse.name}_published_download.zip`);
        setIsProcessing(false);
      }}>
      {tn('download_published_files')}
    </Menu.Item>
  );

  const downloadErrorLog = (
    <Menu.Item
      key="download_error_logs"
      disabled={approvalInProgress}
      onClick={async () => {
        const url = makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_DOWNLOAD_ERROR_LOGS, {
          connectorMetaDefinitionId: publishedFilesDownloadId,
        });

        setIsProcessing(true);
        await downloadGetFile(url, `${customSynapse.name}_error_log.txt`);
        setIsProcessing(false);
      }}>
      {tn('download_error_logs')}
    </Menu.Item>
  );

  const openSdkSharePanel = (
    <Menu.Item
      key="share"
      onClick={() => {
        dispatch(showCustomSdkSynapseSharePanel({ visible: true, customSynapse }));
      }}>
      {tn('share')}
    </Menu.Item>
  );

  const openSharePanel = (
    <Can key="share_non_sdk_custom_synapse" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        key="share"
        onClick={() => {
          dispatch(showCustomSynapseSharePanel({ visible: true, customSynapse }));
          setMenuOpen(false);
        }}>
        {isPublished && isDraft ? tn('share_published') : tn('share')}
      </Menu.Item>
    </Can>
  );

  const viewPublishedHttpSynapse = (
    <Can key="view_http_synapse" permission={AllPermissions.READ_CONNECTOR}>
      <Menu.Item
        key="view_http_synapse"
        onClick={() => {
          const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ENTITIES, {
            synapseId: customSynapse.id,
            version: 'published',
          });

          navigate(url);
        }}>
        <Text>{tn('view_entity_schema')}</Text>
      </Menu.Item>
    </Can>
  );

  const openDraftHttpSynapse = (
    <Can key="open_http_synapse" permission={AllPermissions.WRITE_CONNECTOR}>
      <Menu.Item
        key="open_http_synapse"
        onClick={() => {
          const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ENTITIES, {
            synapseId: customSynapse.id,
            version: 'draft',
          });

          navigate(url);
        }}>
        <Text>{tn('edit_entity_schema')}</Text>
      </Menu.Item>
    </Can>
  );

  const getMenuItems = () => {
    let menuItems: JSX.Element[] = [];
    if (isHTTPSynapse) {
      if (isDraft) {
        menuItems.push(editDraft, openDraftHttpSynapse, publishDraftSynapseMenu);
        if (isPublished) {
          menuItems.push(openSharePanel);
        }
        menuItems.push(<MenuDivider key="divider-two" />, deleteDraft);
      } else if (isPublished) {
        menuItems.push(
          viewPublishedHttpSynapse,
          createDraftAndEdit,
          openSharePanel,
          <MenuDivider key="divider-two" />,
          deleteCustomSynapse
        );
      }
    } else if (isWebhookSynapse) {
      if (isDraft) {
        menuItems.push(editDraft, publishDraftSynapseMenu);
        if (isPublished) {
          menuItems.push(openSharePanel);
        }
        menuItems.push(<MenuDivider key="divider-two" />, deleteDraft);
      } else if (isPublished) {
        menuItems.push(createDraftAndEdit, openSharePanel, <MenuDivider key="divider-two" />, deleteCustomSynapse);
      }
    } else if (isSDKSynapse) {
      if (!isDraft && !isPendingApproval) {
        menuItems.push(createDraftAndEdit);
      }
      if (isDraft) {
        menuItems.push(editDraft, submitForApprovalMenu);
      }
      if (isDraft && !isGlobal && roles.superAdmin) {
        menuItems.push(openSdkSharePanel);
      }
      if (isPendingApproval) {
        menuItems.push(withdrawApproval);
      }
      // Download actions
      if (customSynapse.draftStatus !== CustomSynapseDraftStatuses.APPROVED || publishedFilesDownloadId) {
        menuItems.push(<MenuDivider key="divider-one" />);
      }

      if (customSynapse.draftStatus !== CustomSynapseDraftStatuses.APPROVED) {
        menuItems.push(downloadDraftFiles);
      }
      if (publishedFilesDownloadId) {
        menuItems.push(downloadPublishedFiles);
        // The error logs come from the deployed cloud functions that are published
        // and installed as a connector. We only show this option when there is a
        // published version.
        menuItems.push(downloadErrorLog);
      }
      if (isPendingApproval || !isGlobal) {
        menuItems.push(<MenuDivider key="divider-two" />);
      }
      if (isPendingApproval) {
        menuItems.push(approveCustomSynapse);
      }
      if (!isGlobal) {
        if (isDraft) {
          menuItems.push(deleteDraft);
        } else {
          menuItems.push(deleteCustomSynapse);
        }
      }
    }
    return menuItems;
  };

  return (
    <div className="custom-synapse__table-actions">
      {isProcessing ? (
        <Spinner />
      ) : (
        <KebabMenu
          menuItems={getMenuItems()}
          visible={menuOpen}
          onVisibleChange={setMenuOpen}
          onClick={() => setMenuOpen(false)}
          size="large"
        />
      )}
    </div>
  );
}
