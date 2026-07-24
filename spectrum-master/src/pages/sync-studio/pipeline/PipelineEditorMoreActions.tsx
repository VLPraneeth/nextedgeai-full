import { Button, Dropdown, Icon, Menu, message, Modal, Tooltip } from 'antd';
import { useEffect } from 'react';

import {
  cancelResync,
  getResyncDetails,
  showClonePipelineModal,
  showResyncDraftWarningModal,
  showResyncModal,
} from 'actions/entityPipelineActions';
import Can from 'components/Can';
import { Text } from 'components/typography';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { PermissionsComparisonOperator, useUserHasPermission } from 'hooks/useUserHasPermission';
import { enableNodeCheck, showCreateFragmentModal } from 'store/fragment/actions';
import { useCurrentInstanceState } from 'store/instances/useCurrentInstanceState';
import { showSettingsPanel } from 'store/pipeline/slice';
import { TestPanelView } from 'store/test/types';
import { showValidationResultsPanel, showValidationToolbar } from 'store/validation/slice';
import AppConstants from 'utils/AppConstants';
import { t, tc, tNamespaced } from 'utils/i18nUtil';
import { colors } from 'utils/LessConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import { isGraphEditable } from 'utils/PipelineUtil';
import { UserflowTags } from 'utils/UserflowTags';

import { ResyncDraftWarningModal } from '../entity-pipeline/entity-resync/ResyncDraftWarningModal';
import ResyncRequestModal, { useResyncStates } from '../entity-pipeline/entity-resync/ResyncRequestModal';
import FastMapperModal, { useFastMapper } from '../fast-mapper/FastMapperModal';
import { PipelineEditorProps } from './PipelineEditor.types';
import { useRealtimePipelineContext } from './realtime-pipeline/RealtimePipeline.context';

const tn = tNamespaced('PipelineEditor');

const { GRAPH_STATUS, SYNC_STATUS } = AppConstants;
export const RESYNC_STATUS = {
  CANCEL_REQUESTED: 'CANCEL_REQUESTED',
};

export interface PipelineEditorMoreActionsProps extends PipelineEditorProps {
  graphIsReadOnly: boolean;
  isApproveWithDraftGraph: boolean;
  isDraftOnlyGraph: boolean;
}

const PipelineEditorMoreActions = (props: PipelineEditorMoreActionsProps) => {
  const { userHasPermission } = useUserHasPermission();
  const { showFastMapper } = useFastMapper();
  const dispatch = useEnhancedDispatch();

  const { showingResyncDraftWarningModal } = useEnhancedSelector((state) => state.entityPipeline);

  const {
    resyncDetail,
    entityPipeline,
    pipelineHasDraft,
    lastSynctime,
    prevLastSyncTime,
    requestingResyncStatus,
  } = useResyncStates();

  const { trialExpired, recordLimitExpired } = useCurrentInstanceState();
  const trialEnded = trialExpired || recordLimitExpired;

  const showConfirmDeleteModal = useUserInputConfirmationModal();

  const { entityId, fieldId, displayedGraph, isEntityPipeline, isApproveWithDraftGraph, isDraftOnlyGraph } = props;
  const allActionsDisabled = props.nodeCheckMode;

  const pipelineIsDraft = displayedGraph === GRAPH_STATUS.DRAFT || displayedGraph === GRAPH_STATUS.NEW;

  const onClonePipeline = () => {
    dispatch(showClonePipelineModal(true, entityId, pipelineIsDraft, entityPipeline?.name));
  };

  const onDiscardDraft = () => {
    if (pipelineIsDraft) {
      if (isEntityPipeline) {
        props.showDeleteDraftModal(true, entityId, isApproveWithDraftGraph);
      } else {
        Modal.confirm({
          title: tn('delete_draft_question'),
          content: tn(isEntityPipeline ? 'delete_draft_entity_pipeline' : 'delete_draft_field_pipeline'),
          okText: tc('delete'),
          icon: <Icon type="exclamation-circle" />,
          cancelText: tc('cancel'),
          onOk: () => {
            if (isEntityPipeline) {
              const options = {
                refreshPipelineOnDelete: isApproveWithDraftGraph,
              };
              props.discardPipeline(entityId, options);
            } else if (fieldId) {
              const draftOnlyGraph = isDraftOnlyGraph;
              props.discardPipeline(fieldId, { entityId, refreshOnDiscard: !draftOnlyGraph });
            }
          },
        });
      }
    } else {
      showConfirmDeleteModal({
        title: isEntityPipeline ? tn('delete_published_question') : tn('delete_published_field_question'),
        content: (
          <Text beDangerous>
            {tn(isEntityPipeline ? 'delete_published_entity_pipeline' : 'delete_published_field_pipeline')}
          </Text>
        ),
        okText: tc('delete'),
        okType: 'danger',
        okButtonProps: { type: 'danger' },
        onOk: () => {
          const { isEntityPipeline, entityId, fieldId } = props;
          if (isEntityPipeline) {
            props.deletePublishedPipeline(entityId);
          } else if (fieldId) {
            props.deleteFieldPipeline(fieldId);
          }
        },
      });
    }
  };

  useEffect(() => {
    if (!fieldId && entityPipeline.targetId) {
      if (
        (lastSynctime !== prevLastSyncTime && resyncDetail?.status === RESYNC_STATUS.CANCEL_REQUESTED) ||
        !resyncDetail
      ) {
        dispatch(getResyncDetails(entityPipeline.targetId));
      }
    }
  }, [prevLastSyncTime, lastSynctime, dispatch, resyncDetail, entityPipeline.targetId, fieldId]);

  // if our resync request was successful, show toast message
  useToastForFetchStatusChange(requestingResyncStatus, {
    success: t('EntitySyncStatus.resync_start_successful'),
  });

  // when requesting to open the resync modal, we'll first check for draft
  const openResyncModal = () => {
    if (pipelineHasDraft) {
      dispatch(showResyncDraftWarningModal(true));
    } else {
      dispatch(showResyncModal(true));
    }
  };

  const moreActionsOnClick = (evt: any) => {
    switch (evt.key) {
      case 'test':
        props.setTestPanelView(TestPanelView.SIMULATED_RUN);
        break;
      case 'test-result':
        props.setTestPanelView(TestPanelView.SIMULATED_RESULTS);
        break;
      case 'pipeline':
        props.setTestPanelView(TestPanelView.LIVE_RUN);
        break;
      case 'pipeline-result':
        props.setTestPanelView(TestPanelView.LIVE_RESULTS);
        break;
      case 'settings':
        dispatch(showSettingsPanel({ visible: true }));
        break;
      case 'manage-field-map':
        // TODO: it would be better to just close the ValidationToolbar instead and let
        // its cleanup function handle the rest, but that would require some refactoring
        // for other panel hiding logic.

        dispatch(showValidationToolbar(false));
        dispatch(showValidationResultsPanel(false));
        dispatch(showCreateFragmentModal(false));
        dispatch(enableNodeCheck(false));
        showFastMapper(entityId);
        break;
      case 'clone-pipeline':
        onClonePipeline();
        break;
      case 'discard-draft':
        onDiscardDraft();
        break;
      case 'resync':
        openResyncModal();
        break;
      case 'cancel-resync':
        message.success(t('EntitySyncStatus.cancelling_resync'));
        dispatch(cancelResync(entityPipeline.targetId));
        break;
      default:
        break;
    }
  };

  const { enabled: realtimePipelineEnabled } = useRealtimePipelineContext();

  const rightGroup = [];

  rightGroup.push(<FastMapperModal key="fast-mapper-modal" />);

  let testOptionComponents = null;

  if (isEntityPipeline) {
    const trialEnded = props.currentInstanceState.trialExpired || props.currentInstanceState.recordLimitExpired;
    const testInProgress = props.graphIsReadOnly;

    const disabledTooltipMessage = testInProgress ? tn('test_in_progress') : trialEnded ? tc('trial_ended') : '';

    testOptionComponents = [
      <Can
        key="pipeline"
        permissionOperator={PermissionsComparisonOperator.AND}
        permission={[AllPermissions.WRITE_STUDIO, AllPermissions.READ_CONNECTOR]}>
        <Menu.Item disabled={testInProgress || trialEnded}>
          <Tooltip title={disabledTooltipMessage}>{tn('run_live_test')}</Tooltip>
        </Menu.Item>
      </Can>,
      <Menu.Item key="pipeline-result">{tn('live_test_results')}</Menu.Item>,
    ];
  }

  if (isGraphEditable(displayedGraph)) {
    rightGroup.push(
      <Dropdown
        key="more-actions"
        trigger={['click']}
        overlay={
          <Menu onClick={moreActionsOnClick}>
            {testOptionComponents}
            <Can key="test" permission={AllPermissions.WRITE_STUDIO}>
              <Menu.Item>{tn('run_simulated_test')}</Menu.Item>
            </Can>
            <Menu.Item key="test-result">{tn('simulated_test_results')}</Menu.Item>
            <Menu.Divider key="test-divider" />
            {isEntityPipeline && (
              <Can
                key="manage-field-map"
                permission={[AllPermissions.READ_STUDIO, AllPermissions.READ_CONNECTOR]}
                permissionOperator={PermissionsComparisonOperator.AND}>
                <Menu.Item>{tn('manage_field_mappings')}</Menu.Item>
              </Can>
            )}
            {isEntityPipeline && (
              <Can key="clone-pipeline" permission={AllPermissions.WRITE_STUDIO}>
                <Menu.Item>{tn('clone_pipeline')}</Menu.Item>
              </Can>
            )}
            {isEntityPipeline && <Menu.Item key="settings">{tn('settings')}</Menu.Item>}
            {isEntityPipeline && <Menu.Divider key="entity-divider" />}

            {userHasPermission(AllPermissions.WRITE_STUDIO) && (
              <Menu.Item key="discard-draft" disabled={allActionsDisabled}>
                <Text style={{ color: colors.red500 }}>{tc('delete_draft')}</Text>
              </Menu.Item>
            )}
          </Menu>
        }>
        <Button data-userflow-tag={UserflowTags.SyncStudio.TestPipeline}>
          {tn('more_actions')} <Icon type="down" />
        </Button>
      </Dropdown>
    );
  } else {
    const dropdownMenuItems = [];

    if (!fieldId && !realtimePipelineEnabled) {
      if (resyncDetail?.syncStatus !== SYNC_STATUS.RESYNCING) {
        dropdownMenuItems.push(
          <Menu.Item data-userflow-tag={UserflowTags.SyncStudio.Resync} key="resync" disabled={trialEnded}>
            {t('EntitySyncStatus.resync_btn_label')}
          </Menu.Item>
        );
      } else if (resyncDetail?.status === RESYNC_STATUS.CANCEL_REQUESTED) {
        dropdownMenuItems.push(
          <Menu.Item key="cancelling-resync" disabled>
            {t('EntitySyncStatus.cancelling_resync')}
          </Menu.Item>
        );
      }

      if (
        resyncDetail?.syncStatus === SYNC_STATUS.RESYNCING &&
        resyncDetail?.status !== RESYNC_STATUS.CANCEL_REQUESTED
      ) {
        dropdownMenuItems.push(<Menu.Item key="cancel-resync">{tn('cancel_resync')}</Menu.Item>);
      }
    }

    rightGroup.push(
      <Dropdown
        key="more-actions"
        trigger={['click']}
        overlay={
          <Menu onClick={moreActionsOnClick}>
            {dropdownMenuItems}
            {dropdownMenuItems.length > 0 && <Menu.Divider key="test-divider" />}

            {isEntityPipeline && (
              <Can
                key="manage-field-map"
                permission={[AllPermissions.READ_STUDIO, AllPermissions.READ_CONNECTOR]}
                permissionOperator={PermissionsComparisonOperator.AND}>
                <Menu.Item>{tn('manage_field_mappings')}</Menu.Item>
              </Can>
            )}
            {isEntityPipeline && (
              <Can key="clone-pipeline" permission={AllPermissions.WRITE_STUDIO}>
                <Menu.Item>{tn('clone_pipeline')}</Menu.Item>
              </Can>
            )}
            {isEntityPipeline && <Menu.Item key="settings">{tn('settings')}</Menu.Item>}
            {isEntityPipeline && <Menu.Divider key="entity-divider" />}

            {userHasPermission(AllPermissions.WRITE_STUDIO) && (
              <Menu.Item key="discard-draft" disabled={allActionsDisabled}>
                <Text style={{ color: colors.red500 }}>{tn('delete_published_pipeline')}</Text>
              </Menu.Item>
            )}
          </Menu>
        }>
        <Button>
          {tn('more_actions')} <Icon type="down" />
        </Button>
      </Dropdown>
    );

    if (showingResyncDraftWarningModal) {
      rightGroup.push(<ResyncDraftWarningModal key="draft-warning-modal" />);
    }

    rightGroup.push(<ResyncRequestModal key="resync-request-modal" />);
  }

  return <>{rightGroup}</>;
};

export default PipelineEditorMoreActions;
