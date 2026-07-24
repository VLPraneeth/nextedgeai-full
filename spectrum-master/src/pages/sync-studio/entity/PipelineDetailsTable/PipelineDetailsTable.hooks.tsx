import { navigate } from '@reach/router';
import { Menu, message, Tooltip } from 'antd';
import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  cancelResync,
  createDraftEntityPipeline,
  deletePublishedPipeline,
  getEntityPipeline,
  getSyncStatuses,
  stop as pausePipeline,
  start as resumePipeline,
  showClonePipelineModal,
  showDeleteDraftModal,
  showPublishDraftModal,
  showResyncDraftWarningModal,
  showResyncModal,
} from 'actions/entityPipelineActions';
import Can from 'components/Can';
import Modal from 'components/Modal';
import { Text } from 'components/typography';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useClientPagination from 'hooks/useClientPagination';
import usePreviousValue from 'hooks/usePreviousValue';
import { useResyncStates } from 'pages/sync-studio/entity-pipeline/entity-resync/ResyncRequestModal';
import { EMPTY_ARRAY } from 'store/constants';
import { deleteEntity, getEntities } from 'store/entity/actions';
import { showFastMapper } from 'store/fast-mapper/slice';
import {
  useGetPipelinesDetailsQuery,
  useGetPipelinesSyncMetricDetailsQuery,
  useGetPipelinesTransactionDetailsQuery,
} from 'store/pipeline/api';
import { PipelineDetails, SinkOrSourceDetails } from 'store/pipeline/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { getEntityMetadataMap } from '../PipelineDetails';
import { usePipelineDetailsSearchParameters } from '../PipelineDetails/PipelineDetails.hooks';
import { useCurrentSyncStudioRootTab } from '../SyncStudioRootTabs';
import { usePipelineDetailsTableColumns } from './PipelineDetailsTable.config';
import { frameworkComponents, TagData } from './PipelineDetailsTable.renderers';
import {
  getLastSyncTime,
  getPipelineResyncStatus,
  getPipelineState,
  getPipelineStateObject,
  getPipelineStatusObject,
  getPipelineTagData,
} from './PipelineDetailsTable.utils';

const ta = tNamespaced('PipelineDetailsTableActions');
const tpe = tNamespaced('PipelineEditor');
const tee = tNamespaced('EntityEditor');
const tdt = tNamespaced('PipelineDetailsTable');

export const usePipelineDetailsTable = () => {
  const { rowData } = useRowData();
  const { filteredRowData } = useFilteredRowData(rowData);
  const { pipelineDetailsTableColumns } = usePipelineDetailsTableColumns();
  const { values: searchParams } = usePipelineDetailsSearchParameters();
  const { connectorEntityModalVisible, entitiesFetching } = useEnhancedSelector((state) => state.entity);
  const { entityPipelineDiscarding } = useEnhancedSelector((state) => state.entityPipeline);

  const {
    isFetching: areDetailsFetching,
    isLoading: areDetailsLoading,
    refetch: refetchDetails,
  } = useGetPipelinesDetailsQuery();

  const { refetch: refetchSyncMetrics } = useGetPipelinesSyncMetricDetailsQuery();

  const [pageSize, setPageSize] = useState(25);
  const [showLoadingState, setShowLoadingState] = useState(true);

  // Used to delay the rendering of pipeline table until BE data has been fetched since
  // the API is not set up to accomidate the delay between the request to delete a draft
  // versus actual draft deletion.
  const [extendLoadingDuration, setExtendLoadingDuration] = useState(false);
  const previousEntityPipelineDiscarding = usePreviousValue(entityPipelineDiscarding);

  const isActivityPanelOpen = useMemo(() => searchParams.panel === 'activity', [searchParams.panel]);
  const hasPiplineDraftDiscarded = useMemo(() => {
    return previousEntityPipelineDiscarding === true && entityPipelineDiscarding === false;
  }, [entityPipelineDiscarding, previousEntityPipelineDiscarding]);

  const [{ pageInfo, records: paginatedRowData }, { getNextPage, getPreviousPage, goToPage }] = useClientPagination(
    filteredRowData,
    pageSize
  );

  // When the activity panel is opened, refetch the sync metrics to keep
  // the currentActivity in sync.
  useEffect(() => {
    if (isActivityPanelOpen) {
      refetchSyncMetrics();
    }
  }, [isActivityPanelOpen, refetchSyncMetrics]);

  useEffect(() => {
    if (hasPiplineDraftDiscarded) {
      setExtendLoadingDuration(true);
    }
  }, [hasPiplineDraftDiscarded]);

  // Refetch details whenever there is a entity refetch occuring.
  // Disable this when the connector entity modal is visible. There seems
  // to be a entity refresh continually occuring when creating a new entity
  // was causing this to fire repeatedly.
  useEffect(() => {
    if (entitiesFetching && !connectorEntityModalVisible) {
      setShowLoadingState(true);
      refetchDetails();
    }
  }, [connectorEntityModalVisible, entitiesFetching, refetchDetails]);

  // Show loading state on inital data fetch.
  useEffect(() => {
    if (!areDetailsLoading) {
      setShowLoadingState(false);
    }
  }, [areDetailsLoading]);

  // Clear loading state when details are done fetching.
  useEffect(() => {
    if (!areDetailsFetching && showLoadingState) {
      if (extendLoadingDuration) {
        // Wait 1 second, then clear loading state.
        new Promise((resolve) => setTimeout(resolve, 1000)).then(() => {
          setExtendLoadingDuration(false);
          setShowLoadingState(false);
        });
      } else {
        setShowLoadingState(false);
      }
    }
  }, [areDetailsFetching, extendLoadingDuration, showLoadingState]);

  return {
    columnDefs: pipelineDetailsTableColumns,
    filteredRowData,
    frameworkComponents,
    getNextPage,
    getPreviousPage,
    goToPage,
    pageInfo,
    pageSize,
    paginatedRowData,
    rowData,
    setPageSize,
    showLoadingState,
  };
};

export const useRowData = () => {
  const { entities } = useEnhancedSelector((state) => state.entity);
  const entitySyncStatuses = useEnhancedSelector((state) => state.entityPipeline.entitySyncStatuses ?? EMPTY_ARRAY);

  const { data: pipelinesDetails } = useGetPipelinesDetailsQuery();
  const { data: pipelinesTransactionDetails } = useGetPipelinesTransactionDetailsQuery();
  const { data: pipelinesSyncMetricDetails } = useGetPipelinesSyncMetricDetailsQuery();
  const resyncStatus = useResyncStates();

  const rowData = useMemo(() => {
    const data: any[] = [];

    if (entities && entitySyncStatuses && pipelinesDetails) {
      const entityStatusMap = getEntityMetadataMap(entities, entitySyncStatuses, resyncStatus);

      entities?.forEach((entity) => {
        const pipelineState = getPipelineState(entity, entityStatusMap);
        const { pipelineStatusTagData, pipelineStateTagData } = getPipelineTagData(
          entity.id,
          pipelineState,
          entity.pipelineStatus,
          entityStatusMap[entity.id]?.warningCount
        );

        const pipelineDetails = pipelinesDetails.find((details) => details.syncariEntityId === entity.id);
        const transactionDetails = pipelinesTransactionDetails?.find(
          (details) => details.syncariEntityId === entity.id
        );
        const syncMetricDetails = pipelinesSyncMetricDetails?.find((details) => details.syncariEntityId === entity.id);

        data.push({
          id: entity.id,
          name: entity.displayName,
          apiName: entity.apiName,
          pipelineStatusTagData,
          pipelineStateTagData,
          warningCount: entityStatusMap[entity.id].warningCount,
          currentActivity: syncMetricDetails?.currentActivity,
          lastCompletedSync: getLastSyncTime(entity, entityStatusMap),
          lastCycleDuration: syncMetricDetails?.lastCycleDuration,
          transactionsInLastCycle: transactionDetails?.transactionsInLastCycle,
          sources: pipelineDetails?.sources,
          destinations: pipelineDetails?.sinks,
          fieldsMapped: pipelineDetails?.fieldsMapped,
          numberOfVersions: pipelineDetails?.numberOfVersions,
          mergeConfig: pipelineDetails?.mergeConfig,
          lastModifiedOn: pipelineDetails?.lastModifiedOn,
          settings: pipelineDetails?.settings,
          lastPublishedOn: pipelineDetails?.lastPublishedOn,
          lastModifiedBy: pipelineDetails?.lastModifiedBy,
          resyncDetails: pipelineDetails?.resyncDetail,
        });
      });
    }

    return data;
  }, [
    entities,
    entitySyncStatuses,
    pipelinesDetails,
    pipelinesSyncMetricDetails,
    pipelinesTransactionDetails,
    resyncStatus,
  ]);

  return { rowData };
};

export const useFilteredRowData = (rowData: any[]) => {
  const { values: searchParams } = usePipelineDetailsSearchParameters();

  const filterBySearch = useCallback(
    (row: any) => {
      const isSearchInName = row.name.toLowerCase().includes(searchParams.search.toLowerCase());
      const isSearchInApiName = row.apiName.toLowerCase().includes(searchParams.search.toLowerCase());

      return isSearchInName || isSearchInApiName;
    },
    [searchParams.search]
  );

  const filterByPipelineState = useCallback(
    (row: any) => {
      const states: string[] = [];

      for (const [key, value] of Object.entries(searchParams.pipelineState)) {
        if (value) {
          states.push(key.toLowerCase());
        }
      }

      return row.pipelineStateTagData
        .map((data: any) => states.includes(data.label.toLowerCase()))
        .some((value: boolean) => value);
    },
    [searchParams.pipelineState]
  );

  const filterByPipelineStatus = useCallback(
    (row: any) => {
      const statuses: string[] = [];

      for (const [key, value] of Object.entries(searchParams.pipelineStatus)) {
        if (value) {
          statuses.push(key.toLowerCase());
        }
      }

      return row.pipelineStatusTagData
        .map((data: any) => statuses.includes(data.label.toLowerCase()))
        .some((value: boolean) => value);
    },
    [searchParams.pipelineStatus]
  );

  const filterBySource = useCallback(
    (row: any) => {
      return !!row.sources?.find((source: SinkOrSourceDetails) => searchParams.sources.includes(source.entityId));
    },
    [searchParams.sources]
  );

  const filterByDestination = useCallback(
    (row: any) => {
      return !!row.destinations?.find((source: SinkOrSourceDetails) =>
        searchParams.destinations.includes(source.entityId)
      );
    },
    [searchParams.destinations]
  );

  const { searchFilters, panelFilters } = useMemo(() => {
    const searchFilters: ((row: any) => boolean)[] = [];
    const panelFilters: ((row: any) => boolean)[] = [];

    if (searchParams.search) {
      searchFilters.push(filterBySearch);
    }

    if (Object.values(searchParams.pipelineState).some((value) => value)) {
      panelFilters.push(filterByPipelineState);
    }

    if (Object.values(searchParams.pipelineStatus).some((value) => value)) {
      panelFilters.push(filterByPipelineStatus);
    }

    if (searchParams.sources.length > 0) {
      panelFilters.push(filterBySource);
    }

    if (searchParams.destinations.length > 0) {
      panelFilters.push(filterByDestination);
    }

    return { searchFilters, panelFilters };
  }, [
    filterByDestination,
    filterByPipelineState,
    filterByPipelineStatus,
    filterBySearch,
    filterBySource,
    searchParams.destinations,
    searchParams.pipelineState,
    searchParams.pipelineStatus,
    searchParams.search,
    searchParams.sources,
  ]);

  const filteredRowData = useMemo(() => {
    let filteredData = rowData;

    // First, filter on the input if the filter is active.
    if (searchFilters.length > 0) {
      filteredData = rowData.filter((row) => searchFilters.map((filter) => filter(row)).every((value) => value));
    }

    // Then, filter on the panel filters. This should work as an OR operation
    // between selected filters, hence the .some() call.
    if (panelFilters.length > 0) {
      filteredData = rowData.filter((row) => panelFilters.map((filter) => filter(row)).some((value) => value));
    }

    return filteredData;
  }, [searchFilters, panelFilters, rowData]);

  return {
    filteredRowData,
  };
};

export enum ActionKeys {
  PUBLISH = 'publish',
  PAUSE = 'pause',
  RESUME = 'resume',
  GOTO_PIPELINE = 'goto-pipeline',
  GOTO_DRAFT = 'goto-draft',
  CREATE_DRAFT = 'create-draft',
  MANAGE_FIELD_MAP = 'manage-field-map',
  RESYNC = 'resync',
  CANCEL_RESYNC = 'cancel-resync',
  DELETE_DRAFT = 'delete-draft',
  DELETE_PUBLISHED_PIPELINE = 'delete-published-pipeline',
  CLONE_DRAFT = 'clone-draft',
  CLONE_PUBLISHED_PIPELINE = 'clone-published-pipeline',
  DELETE_ENTITY = 'delete-entity',
}

export const usePipelineActionsCell = (data: Record<string, any>) => {
  const {
    handlePublish,
    handlePause,
    handleResume,
    handleGotoPipeline,
    handleGotoDraft,
    handleCreateDraft,
    handleManageFieldMap,
    handleResync,
    handleCancelResync,
    handleDeleteDraft,
    handleDeletePublishedPipeline,
    handleDeleteEntity,
    handleCloneDraft,
    handleClonePublished,
  } = usePipelineActionsHandlers(data);

  const { isRunning, isPausing } = getPipelineStateObject(data.pipelineStateTagData);
  const { realtimePipeline } = data.settings || {};
  const { isPublished, hasDraft } = getPipelineStatusObject(data.pipelineStatusTagData);
  const { isResyncing, isCancelling } = getPipelineResyncStatus(data.resyncDetails);

  const menuActionHandler = useCallback(
    (action: { key: string }) => {
      switch (action.key) {
        case ActionKeys.PUBLISH:
          handlePublish();
          break;
        case ActionKeys.PAUSE:
          handlePause();
          break;
        case ActionKeys.RESUME:
          handleResume();
          break;
        case ActionKeys.GOTO_PIPELINE:
          handleGotoPipeline();
          break;
        case ActionKeys.GOTO_DRAFT:
          handleGotoDraft();
          break;
        case ActionKeys.CREATE_DRAFT:
          handleCreateDraft();
          break;
        case ActionKeys.MANAGE_FIELD_MAP:
          handleManageFieldMap();
          break;
        case ActionKeys.RESYNC:
          handleResync();
          break;
        case ActionKeys.CANCEL_RESYNC:
          handleCancelResync();
          break;
        case ActionKeys.DELETE_DRAFT:
          handleDeleteDraft();
          break;
        case ActionKeys.DELETE_PUBLISHED_PIPELINE:
          handleDeletePublishedPipeline();
          break;
        case ActionKeys.DELETE_ENTITY:
          handleDeleteEntity();
          break;
        case ActionKeys.CLONE_DRAFT:
          handleCloneDraft();
          break;
        case ActionKeys.CLONE_PUBLISHED_PIPELINE:
          handleClonePublished();
          break;
      }
    },
    [
      handleCancelResync,
      handleCloneDraft,
      handleClonePublished,
      handleCreateDraft,
      handleDeleteDraft,
      handleDeleteEntity,
      handleDeletePublishedPipeline,
      handleGotoDraft,
      handleGotoPipeline,
      handleManageFieldMap,
      handlePause,
      handlePublish,
      handleResume,
      handleResync,
    ]
  );

  const Publish = useMemo(
    () => (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.PUBLISH}>
        <Menu.Item>{ta('publish')}</Menu.Item>
      </Can>
    ),
    []
  );

  const Pause = useMemo(
    () =>
      !realtimePipeline ? (
        <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.PAUSE}>
          <Menu.Item>{ta('pause')}</Menu.Item>
        </Can>
      ) : null,
    [realtimePipeline]
  );

  const Resume = useMemo(() => {
    if (realtimePipeline) {
      return null;
    }
    const tooltipTitle = isPausing ? ta('resume_while_pausing') : undefined;

    return (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.RESUME}>
        <Menu.Item disabled={isPausing}>
          <Tooltip title={tooltipTitle}>{ta('resume')}</Tooltip>
        </Menu.Item>
      </Can>
    );
  }, [isPausing, realtimePipeline]);

  const GoToPipeline = useMemo(() => <Menu.Item key={ActionKeys.GOTO_PIPELINE}>{ta('goto_pipeline')}</Menu.Item>, []);

  const GoToDraft = useMemo(() => <Menu.Item key={ActionKeys.GOTO_DRAFT}>{ta('goto_draft')}</Menu.Item>, []);

  const CreateDraft = useMemo(
    () => (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.CREATE_DRAFT}>
        <Menu.Item>{tc('create_draft')}</Menu.Item>
      </Can>
    ),
    []
  );

  const ManageFieldMap = useMemo(
    () => <Menu.Item key={ActionKeys.MANAGE_FIELD_MAP}>{ta('manage_field_map')}</Menu.Item>,
    []
  );

  const Resync = useMemo(
    () =>
      !realtimePipeline ? (
        <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.RESYNC}>
          <Menu.Item>{ta('resync')}</Menu.Item>
        </Can>
      ) : null,
    [realtimePipeline]
  );

  const CancelResync = useMemo(
    () =>
      !realtimePipeline ? (
        <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.CANCEL_RESYNC}>
          <Menu.Item>{ta('cancel_resync')}</Menu.Item>
        </Can>
      ) : null,
    [realtimePipeline]
  );

  const DeleteDraft = useMemo(
    () => (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.DELETE_DRAFT}>
        <Menu.Item className="pipeline-details-table__destructive-action">{tc('delete_draft')}</Menu.Item>
      </Can>
    ),
    []
  );

  const DeletePublishedPipeline = useMemo(
    () => (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.DELETE_PUBLISHED_PIPELINE}>
        <Menu.Item className="pipeline-details-table__destructive-action">{ta('delete_published_pipeline')}</Menu.Item>
      </Can>
    ),
    []
  );

  const CloneDraftPipeline = useMemo(
    () => (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.CLONE_DRAFT}>
        <Menu.Item>{ta('clone_draft_pipeline')}</Menu.Item>
      </Can>
    ),
    []
  );

  const ClonePublishedPipeline = useMemo(
    () => (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.CLONE_PUBLISHED_PIPELINE}>
        <Menu.Item>{ta('clone_published_pipeline')}</Menu.Item>
      </Can>
    ),
    []
  );

  const DeleteEntity = useMemo(() => {
    const disabled = isPublished || hasDraft;
    const tooltipTitle = disabled ? ta('delete_entity_disabled_tooltip') : undefined;

    return (
      <Can permission={AllPermissions.WRITE_STUDIO} key={ActionKeys.DELETE_ENTITY}>
        <Menu.Item className={cx(!disabled && 'pipeline-details-table__destructive-action')} disabled={disabled}>
          <Tooltip title={tooltipTitle}>{ta('delete_entity')}</Tooltip>
        </Menu.Item>
      </Can>
    );
  }, [hasDraft, isPublished]);

  const Cancelling = useMemo(() => <Menu.Item disabled>{ta('canceling_resync')}</Menu.Item>, []);

  const Divider = useMemo(() => <Menu.Divider />, []);

  const menuItems = useMemo(() => {
    let items: Array<JSX.Element | null> = [];

    if (isPublished && !hasDraft && isRunning) {
      // Published & Active
      items = [
        Pause,
        GoToPipeline,
        CreateDraft,
        ManageFieldMap,
        isCancelling ? Cancelling : isResyncing ? CancelResync : Resync,
        Divider,
        ClonePublishedPipeline,
        Divider,
        DeletePublishedPipeline,
        DeleteEntity,
      ];
    } else if (isPublished && !hasDraft && !isRunning) {
      // Published & Paused
      items = [
        Resume,
        GoToPipeline,
        CreateDraft,
        ManageFieldMap,
        isCancelling ? Cancelling : isResyncing ? CancelResync : Resync,
        Divider,
        ClonePublishedPipeline,
        Divider,
        DeletePublishedPipeline,
        DeleteEntity,
      ];
    } else if (isPublished && hasDraft && isRunning) {
      // Published & Draft & Active
      items = [
        Publish,
        Pause,
        GoToPipeline,
        GoToDraft,
        isCancelling ? Cancelling : isResyncing ? CancelResync : Resync,
        ManageFieldMap,
        Divider,
        CloneDraftPipeline,
        ClonePublishedPipeline,
        Divider,
        DeleteDraft,
        DeletePublishedPipeline,
        DeleteEntity,
      ];
    } else if (isPublished && hasDraft && !isRunning) {
      // Published & Draft & Paused
      items = [
        Publish,
        Resume,
        GoToPipeline,
        GoToDraft,
        isCancelling ? Cancelling : isResyncing ? CancelResync : Resync,
        ManageFieldMap,
        Divider,
        CloneDraftPipeline,
        ClonePublishedPipeline,
        Divider,
        DeleteDraft,
        DeletePublishedPipeline,
        DeleteEntity,
      ];
    } else if (!isPublished && hasDraft) {
      // Draft
      items = [Publish, GoToDraft, ManageFieldMap, Divider, CloneDraftPipeline, Divider, DeleteDraft, DeleteEntity];
    } else {
      // Unmapped
      items = [CreateDraft, ManageFieldMap, Divider, DeleteEntity];
    }

    return items.filter(Boolean);
  }, [
    CancelResync,
    Cancelling,
    CloneDraftPipeline,
    ClonePublishedPipeline,
    CreateDraft,
    DeleteDraft,
    DeleteEntity,
    DeletePublishedPipeline,
    Divider,
    GoToDraft,
    GoToPipeline,
    ManageFieldMap,
    Pause,
    Publish,
    Resume,
    Resync,
    hasDraft,
    isCancelling,
    isPublished,
    isResyncing,
    isRunning,
  ]);

  return { menuItems, menuActionHandler };
};

export const usePipelineActionsHandlers = (data: Record<string, any>) => {
  const dispatch = useEnhancedDispatch();
  const { updateSearchParams } = usePipelineDetailsSearchParameters();
  const { currentTab } = useCurrentSyncStudioRootTab();
  const showConfirmDeleteModal = useUserInputConfirmationModal();

  const { hasDraft } = getPipelineStatusObject(data.pipelineStatusTagData);

  const handlePublish = useCallback(() => {
    dispatch(showPublishDraftModal(true, data.id));
  }, [data.id, dispatch]);

  const handlePause = useCallback(() => {
    dispatch(pausePipeline(data.id));
    dispatch(getEntities());
    dispatch(getSyncStatuses());
  }, [data.id, dispatch]);

  const handleResume = useCallback(() => {
    dispatch(resumePipeline(data.id));
    dispatch(getEntities());
    dispatch(getSyncStatuses());
  }, [data.id, dispatch]);

  const handleGotoPipeline = useCallback(() => {
    const url = data.pipelineStatusTagData.find((data: any) => data.label.toLowerCase() === 'published')?.url;

    navigate(url);
  }, [data.pipelineStatusTagData]);

  const handleGotoDraft = useCallback(() => {
    const url = data.pipelineStatusTagData.find((data: any) => data.label.toLowerCase() === 'draft')?.url;

    navigate(url);
  }, [data.pipelineStatusTagData]);

  const handleCreateDraft = useCallback(() => {
    dispatch(createDraftEntityPipeline(data.id))
      .then(() => {
        dispatch(getEntities());
        navigate(makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { graphVersion: 'draft', entityId: data.id }));
      })
      .catch((error) => {
        message.error(error);
      });
  }, [data.id, dispatch]);

  const handleManageFieldMap = useCallback(() => {
    dispatch(showFastMapper({ visible: true, entityId: data.id }));
    updateSearchParams([{ name: 'panel', value: '' }]);
  }, [data.id, dispatch, updateSearchParams]);

  const handleResync = useCallback(() => {
    dispatch(getEntityPipeline(data.id, 'APPROVED'));

    if (hasDraft) {
      dispatch(showResyncDraftWarningModal(true));
    } else {
      dispatch(showResyncModal(true));
    }
  }, [data.id, dispatch, hasDraft]);

  const handleCancelResync = useCallback(() => {
    dispatch(cancelResync(data.id))
      .then(() => {
        message.success(ta('canceling_resync'));
        dispatch(getEntities());
      })
      .catch((error) => {
        message.error(error);
      });
  }, [data.id, dispatch]);

  const handleDeleteDraft = useCallback(() => {
    dispatch(showDeleteDraftModal(true, data.id));
  }, [data.id, dispatch]);

  const handleDeletePublishedPipeline = useCallback(() => {
    showConfirmDeleteModal({
      title: tpe('delete_published_question'),
      content: <Text beDangerous>{tpe('delete_published_entity_pipeline')}</Text>,
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
      onOk: () => {
        dispatch(deletePublishedPipeline(data.id));
      },
    });
  }, [data.id, dispatch, showConfirmDeleteModal]);

  const handleDeleteEntity = useCallback(() => {
    Modal.confirm({
      title: tee('confirm_entity_delete_title'),
      content: tee('confirm_entity_delete_description', { entityName: data.name }),
      okText: tc('remove'),
      cancelText: tc('cancel'),
      onOk: () => {
        (dispatch(deleteEntity(data.id, true)) as any).then((resp: any) => {
          if (resp.success) {
            navigate(makeUrl(RouteConstants.ENTITIES, { tabId: currentTab }));
          } else {
            message.error(
              <>
                {tee('delete_entity_failed', { entityName: data.name })}
                <br />
                {resp?.error?.errorMessage}
              </>
            );
          }
        });
      },
    });
  }, [currentTab, data.id, data.name, dispatch]);

  const handleCloneDraft = useCallback(() => {
    dispatch(showClonePipelineModal(true, data.id, true, data.name));
  }, [data.name, data.id, dispatch]);

  const handleClonePublished = useCallback(() => {
    dispatch(showClonePipelineModal(true, data.id, false, data.name));
  }, [data.name, data.id, dispatch]);

  return {
    handlePublish,
    handlePause,
    handleResume,
    handleGotoPipeline,
    handleGotoDraft,
    handleCreateDraft,
    handleManageFieldMap,
    handleResync,
    handleCancelResync,
    handleDeleteDraft,
    handleDeletePublishedPipeline,
    handleDeleteEntity,
    handleCloneDraft,
    handleClonePublished,
  };
};

export const useCurrentActivityText = (
  currentActivity: string,
  pipelineStatusTagData: TagData[],
  pipelineStateTagData: TagData[],
  settings?: PipelineDetails['settings']
) => {
  const { isPublished } = getPipelineStatusObject(pipelineStatusTagData);
  const { isPaused } = getPipelineStateObject(pipelineStateTagData);

  const text = useMemo(() => {
    if (!isPublished) {
      return tdt('activity_not_published');
    }

    if (isPaused) {
      return tdt('idle');
    }

    if (currentActivity) {
      return currentActivity;
    }

    // Waiting state
    if (settings?.realtimePipeline) {
      return tdt('active_accepting_request');
    } else {
      // Else for clarity since normal pipeline has different waiting text vs realtime pipeline
      return tdt('activity_waiting');
    }
  }, [isPublished, isPaused, currentActivity, settings?.realtimePipeline]);

  return text;
};
