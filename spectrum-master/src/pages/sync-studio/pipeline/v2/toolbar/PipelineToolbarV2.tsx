//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Dropdown, Icon, Menu, Switch, Tooltip } from 'antd';
import { MenuProps } from 'antd/lib/menu';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { isUndefined, keys } from 'lodash';
import { Fragment, ReactElement, ReactNode, useEffect, useMemo } from 'react';

import { ReactComponent as PauseIcon } from 'assets/icons/pipeline-pause.svg';
import { ReactComponent as ResumeIcon } from 'assets/icons/pipeline-resume.svg';
import Can from 'components/Can';
import BaselinePublishIcon from 'components/icons/BaselinePublishIcon';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Spinner from 'components/Spinner';
import SyncStatusTag, { SyncStatuses } from 'components/SyncStatusTag';
import { Text } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { usePipelineError } from 'pages/sync-studio/pipeline-error/PipelineError.hooks';
import { PipelineErrorToolbar } from 'pages/sync-studio/pipeline-error/PipelineErrorToolbar';
import { ValidationToolbar } from 'pages/sync-studio/validation/ValidationToolbar';
import { useSyncStatusForEntity } from 'selectors/entitySelectors';
import { selectCurrentGraphDraftReady } from 'selectors/pipelineSelectors';
import { useCurrentInstanceState } from 'store/instances/useCurrentInstanceState';
import { showValidationResultsPanel, showValidationToolbar } from 'store/validation/slice';
import AppConstants from 'utils/AppConstants';
import { tCommon, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { createDraftGraph, navigateToGraphVersion } from 'utils/PipelineUtil';
import { UserflowTags } from 'utils/UserflowTags';

import './PipelineToolbar.less';

const { GRAPH_STATUS } = AppConstants;
const tn = tNamespaced('PipelineToolbar');
const tn_PlgTrial = tNamespaced('PlgTrial');

const redColorStyle = { color: 'red' };

export type GraphStatus = keyof typeof AppConstants.GRAPH_STATUS;
export type AvailableVersionsModel = Record<GraphStatus, Record<string, string>>;

export interface GraphToolbarPropsV2 {
  availableVersions?: AvailableVersionsModel;
  disableSave?: boolean;
  disableTest?: boolean;
  disableValidate?: boolean;
  isDraft?: boolean;
  errorMessage?: string;
  errorTitle?: boolean;
  entityId: string;
  fieldId?: string;
  goToName?: string;
  isLoading?: boolean;
  loadingMessage?: string;
  navigateUp?: () => void;
  onPublishPipeline?: () => void;
  onChangeGraph?: MenuProps['onClick'];
  onSaveChanges?: () => void;
  onCreateVersion?: () => void;
  onStart?: () => void;
  onStop?: () => void;
  onTest?: () => void;
  onValidate?: () => void;
  publishProps?: Record<string, any>;
  readOnlyMsg?: string;
  showPublishDraft?: boolean;
  showSave?: boolean;
  showSuccess?: boolean;
  showTest?: boolean;
  showValidate?: boolean;
  showCreateDraft?: boolean;
  pausedBy?: string;
  successMessage?: string;
  emptyToolbar?: boolean;
  allActionsDisabled?: boolean;
  readyToggleValue?: boolean;
  onReadyToggleChange?: (toggled: boolean) => void;
  rightGroup?: React.ReactElement[] | ReactNode;
  pipeline?: any;
  updatePipeline: (
    id: string,
    graphJson: any,
    options?: { refreshPipelineOnUpdate?: boolean; entityId?: string }
  ) => void;
}

const PipelineToolbarV2 = ({
  availableVersions,
  disableSave,
  disableTest,
  disableValidate,
  isDraft,
  errorMessage,
  errorTitle,
  entityId,
  fieldId,
  goToName,
  isLoading,
  loadingMessage,
  navigateUp,
  onPublishPipeline,
  onChangeGraph,
  onSaveChanges,
  onCreateVersion,
  onStart,
  onStop,
  onTest,
  onValidate,
  publishProps = {},
  readOnlyMsg,
  showPublishDraft,
  showSave,
  showSuccess,
  showTest,
  showValidate,
  showCreateDraft,
  pausedBy,
  successMessage,
  emptyToolbar: emptyToolbarProp,
  allActionsDisabled,
  readyToggleValue,
  onReadyToggleChange,
  rightGroup,
  updatePipeline,
  pipeline,
}: GraphToolbarPropsV2) => {
  const { trialExpired } = useCurrentInstanceState();

  // TEMP: Hide actions until we support them
  const emptyToolbar = true;

  const dispatch = useEnhancedDispatch();
  const displayedGraph = useEnhancedSelector((state) => state.pipeline.displayedGraph);
  const isPipelineDraftReady = useEnhancedSelector(selectCurrentGraphDraftReady);
  const validationToolbarVisible = useEnhancedSelector((state) => state.validation.validationToolbarVisible);
  const { errors } = usePipelineError({});

  const { pipelineStatus: syncStatus } = useSyncStatusForEntity(entityId);

  useEffect(() => {
    if (displayedGraph === AppConstants.GRAPH_STATUS.APPROVED) {
      dispatch(showValidationToolbar(false));
      dispatch(showValidationResultsPanel(false));
    }
  }, [displayedGraph, dispatch]);

  const onCreateDraftClick = async () => {
    // if (props.isFieldPipeline) {
    //   await props.createDraftFieldPipeline(props.fieldId as string);

    //   const newGraphVersion =
    //     props.graphVersion?.toUpperCase() === GRAPH_STATUS.DRAFT ? GRAPH_STATUS.NEW : GRAPH_STATUS.DRAFT;

    //   _navigateToGraphVersion({ graphVersion: newGraphVersion });
    // } else if (!props.pipelineExists) {
    //   await props.createDraftEntityPipeline(props.entityId);
    //   props.getEntities();

    //   _navigateToGraphVersion({ graphVersion: GRAPH_STATUS.DRAFT });

    //   // If the EP doesn't have a published version, the draft status would be
    //   // /new before AND AFTER the draft is created. In this case we need to
    //   // manually remount the component to get the new pipeline.
    //   props.remountComponent();
    // } else {
    const approvedDraft = {
      nodes: pipeline.nodes,
      edges: pipeline.edges,
      groups: pipeline.groups,
    };

    // Clone and create a valid draft
    const draftGraph = createDraftGraph(approvedDraft.nodes, approvedDraft.edges);
    const { draftStatus, draft, ...metadataSpread } = pipeline;

    // const pipelineSpecificGraph = props.isEntityPipeline
    //   ? {
    //       ...metadataSpread,
    //       id: ObjectID.generate(),
    //       parentId: pipeline.id,
    //     }
    //   : getNewDraftMetadata();

    const pipelineSpecificGraph = {
      ...metadataSpread,
      id: ObjectID.generate(),
      parentId: pipeline.id,
    };

    const updatedPipeline = {
      ...pipeline,
      draft: {
        ...pipelineSpecificGraph,
        nodes: draftGraph.nodes,
        edges: draftGraph.edges,
        groups: approvedDraft.groups,
      },
      nodes: approvedDraft.nodes,
      edges: approvedDraft.edges,
      groups: approvedDraft.groups,
    };

    // props.graphChanged({
    //   changed: null,
    //   changedScope: null,
    //   changedId: null,
    // });

    const pipelineId = fieldId || entityId;
    await updatePipeline(pipelineId, updatedPipeline);

    navigateToGraphVersion({
      graphVersion: GRAPH_STATUS.DRAFT,
      entityId,
      fieldId,
      replace: false,
    });
    // }
  };

  const disabledByTrialText = trialExpired ? tn_PlgTrial('trial_expired') : '';

  const _getGraphVersionSelection = () => {
    let readOnlyIcon: ReactElement | undefined;

    const verCount = keys(availableVersions)?.length;

    if (verCount <= 1) {
      return null;
    }

    if (!isUndefined(readOnlyMsg) && readOnlyMsg.length > 0) {
      readOnlyIcon = <Icon type="lock" theme="filled" />;
    }

    let selectionIcon = <Icon type="check-circle" />;
    let selectionText = tn('published');

    if (isDraft) {
      selectionIcon = <Icon type="edit" />;
      selectionText = tn('draft');
    }

    return (
      <Dropdown
        overlay={
          <Menu onClick={onChangeGraph}>
            <Menu.Item disabled={!isDraft} key={GRAPH_STATUS.APPROVED}>
              {tn('published')}
            </Menu.Item>
            <Menu.Item disabled={isDraft} key={GRAPH_STATUS.DRAFT}>
              {tn('draft')}
            </Menu.Item>
          </Menu>
        }
        trigger={['click']}
        disabled={verCount <= 1 || allActionsDisabled}
        className="dropdown-status"
        key="graph-version">
        <Button data-userflow-tag={UserflowTags.SyncStudio.PipelineVersionSelector}>
          {selectionIcon} {selectionText} {readOnlyIcon} <Icon type="down" />
        </Button>
      </Dropdown>
    );
  };

  const _getPublishedVersion = () => {
    if (showPublishDraft) {
      const { tooltip, ...morePublishProps } = publishProps;

      return (
        <Tooltip title={disabledByTrialText} placement="bottom">
          <Can permission={AllPermissions.WRITE_STUDIO}>
            <Button
              data-userflow-tag={UserflowTags.SyncStudio.PublishDraft}
              key="publish"
              onClick={onPublishPipeline}
              type="primary"
              {...morePublishProps}
              disabled={allActionsDisabled || trialExpired}>
              <BaselinePublishIcon /> {tn('publish')}
            </Button>
          </Can>
        </Tooltip>
      );
    }
  };

  const _getReady = () => {
    if (fieldId && isDraft) {
      return (
        <span data-userflow-tag={UserflowTags.SyncStudio.ReadyToPublish}>
          <InputWithLabel
            className="synri-ready-publish"
            label={tn('ready_to_publish')}
            name="ready"
            input={
              <Can permission={AllPermissions.WRITE_STUDIO}>
                <Switch
                  checkedChildren={<Icon type="check" />}
                  unCheckedChildren={<Icon type="close" />}
                  defaultChecked={readyToggleValue ?? isPipelineDraftReady}
                  onChange={onReadyToggleChange}
                />
              </Can>
            }
          />
        </span>
      );
    }
  };

  const graphVersion = _getGraphVersionSelection();
  const publish = _getPublishedVersion();
  const ready = _getReady();

  const showStop =
    !isDraft &&
    !fieldId &&
    syncStatus &&
    (['CLAIMED', 'RUNNING', 'READY', 'RESYNCING', 'RETRYING'] as SyncStatuses[]).includes(syncStatus);
  const showStart =
    !isDraft && !fieldId && syncStatus && (['STOPPED', 'PAUSED', 'ERROR'] as SyncStatuses[]).includes(syncStatus);

  const statusTooltipText = useMemo(() => {
    if (syncStatus === AppConstants.SYNC_STATUS.PAUSED) {
      return tn('paused_by', { user: pausedBy || 'Unknown' });
    } else if (syncStatus === AppConstants.SYNC_STATUS.RETRYING && errors?.[0]?.retryCount) {
      return tn('retries', { count: errors[0].retryCount });
    }
    return '';
  }, [errors, pausedBy, syncStatus]);

  return (
    <>
      <div
        className={cx('graph-toolbar-container', {
          'graph-version-draft': isDraft && !emptyToolbarProp,
        })}
        style={{ zIndex: 10 }}>
        <div className="left-group">
          <Tooltip title={tn('goto_name', { name: goToName })} placement="bottom">
            <div className="toolbar-button" onClick={navigateUp}>
              <Icon type="arrow-left" />
            </div>
          </Tooltip>
          {!emptyToolbarProp && graphVersion}
          {!emptyToolbarProp && publish}
          {!emptyToolbar && ready}
          {!emptyToolbarProp && (
            <div className="synri-left-toolbar-content" key="readonly">
              {!isDraft && !fieldId && (
                <SyncStatusTag
                  syncStatus={syncStatus}
                  large
                  color={syncStatus === AppConstants.SYNC_STATUS.PAUSED ? 'alert' : undefined}
                  tooltipText={statusTooltipText}
                />
              )}
              {readOnlyMsg && readOnlyMsg.length > 0 && <Text color="gray-800">{readOnlyMsg}</Text>}
              {!emptyToolbar && showStart && (
                <Tooltip title={disabledByTrialText}>
                  <Button
                    key="start-pipeline"
                    className="synri-faded-icon-button"
                    onClick={onStart}
                    disabled={allActionsDisabled || trialExpired}>
                    <ResumeIcon />
                    <Text>{tCommon('start')}</Text>
                  </Button>
                </Tooltip>
              )}
              {!emptyToolbar && showStop && (
                <Tooltip title={disabledByTrialText}>
                  <Button
                    key="stop-pipeline"
                    className="synri-faded-icon-button"
                    onClick={onStop}
                    disabled={syncStatus === 'PAUSING' || allActionsDisabled || trialExpired}>
                    <PauseIcon />
                    <Text>{tCommon('stop')}</Text>
                  </Button>
                </Tooltip>
              )}
            </div>
          )}
        </div>
        <div className="right-group">
          <div className="action-buttons">
            {errorMessage && (
              <div className="error-container" key="error">
                <Tooltip title={errorMessage}>
                  <Icon type="exclamation-circle" theme="filled" style={redColorStyle} />
                </Tooltip>
                <span className="error-title">{errorTitle}</span>
              </div>
            )}
            {!emptyToolbarProp && isLoading && (
              <Fragment key="loading-message">
                <Spinner key="loading-spin" size="small" />
                <span className="loading-message" key="loading-message">
                  {loadingMessage}
                </span>
              </Fragment>
            )}
            {!emptyToolbar && showSuccess && (
              <div
                className="synri-status-container"
                key="success-message"
                data-userflow-tag={UserflowTags.SyncStudio.PipelineValidMessage}>
                <Icon type="check-circle" />
                <span className="success-message">{successMessage}</span>
              </div>
            )}
            {!emptyToolbar && !fieldId && isDraft && (
              <Button key="create_version" onClick={onCreateVersion} disabled={allActionsDisabled}>
                {tn('create_version')}
              </Button>
            )}
            {/* Temp code to show Save only */}
            {(!emptyToolbar || true) && showSave && (
              <Button key="save" onClick={onSaveChanges} disabled={disableSave || allActionsDisabled}>
                {tn('save_draft')}
              </Button>
            )}
            {!emptyToolbarProp && showCreateDraft && (
              <Button key="create_draft" onClick={onCreateDraftClick}>
                {tn('create_draft')}
              </Button>
            )}
            {!emptyToolbarProp && showValidate && (
              <Button
                data-userflow-tag={UserflowTags.SyncStudio.ValidatePipeline}
                disabled={disableValidate || allActionsDisabled}
                key="validate"
                onClick={() => {
                  if (onValidate) {
                    dispatch(showValidationToolbar(true));
                    dispatch(showValidationResultsPanel(true));

                    // The ValidationToolbar calls validate when it becomes
                    // visible. Only call validate here if the ValidationToolbar
                    // is already open.
                    if (validationToolbarVisible) {
                      onValidate();
                    }
                  }
                }}>
                {tn('validate')}
              </Button>
            )}
            {!emptyToolbar && showTest && (
              <Button
                data-userflow-tag={UserflowTags.SyncStudio.TestPipeline}
                disabled={disableTest || allActionsDisabled}
                key="test"
                onClick={onTest}>
                {tn('test')}
              </Button>
            )}
            {!emptyToolbar && rightGroup}
          </div>
        </div>
      </div>
      {isDraft && <ValidationToolbar onValidate={onValidate} updateNodes={() => {}} />}
      {!isDraft && false && <PipelineErrorToolbar />}
    </>
  );
};

export default PipelineToolbarV2;
