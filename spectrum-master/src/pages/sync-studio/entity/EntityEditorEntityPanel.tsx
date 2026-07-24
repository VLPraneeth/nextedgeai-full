//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon, Modal } from 'antd';
import { filter, isEmpty, keys, orderBy, pickBy, upperCase } from 'lodash';
import * as React from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import {
  createDraftFieldPipeline,
  discardFieldPipeline,
  markFieldPipelineNotReady,
  markFieldPipelineReady,
} from 'actions/fieldPipelineActions';
import { addTag, getTagsLike, removeTag } from 'actions/tagActions';
import { ReactComponent as DataAuthority } from 'assets/icons/data-authority.svg';
import { ReactComponent as Pipeline } from 'assets/icons/pipeline.svg';
import { ReactComponent as Tag } from 'assets/icons/tag.svg';
import Checkbox from 'components/Checkbox';
import FieldList, { FIELD_ACTIONS } from 'components/FieldList';
import { NodeModel } from 'components/GraphItemFilter';
import IconTooltip from 'components/icons/IconTooltip';
import InputFilter from 'components/InputFilter';
import { Stack } from 'components/layout';
import { PropertyPanelActionModel } from 'components/PropertyPanelAction';
import PropertyPanelTitle from 'components/PropertyPanelTitle';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import TabPanelSpin from 'components/TabPanelSpin';
import Tabs, { Tab, TabPane } from 'components/Tabs';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import useEffectForValue from 'hooks/useEffectForValue';
import NodePanel from 'pages/sync-studio/NodePanel';
import { RootState } from 'reducers/index';
import { setNavigatingTo } from 'store/app/actions';
import { selectDeleteMappingsResponse, selectSaveMappingsResponse } from 'store/fast-mapper/selectors';
import { showUnsavedConfirmModal as showConfirmModal } from 'store/pipeline/actions';
import { selectSchemaForEntity, selectSchemaStatusForEntity } from 'store/schema/selectors';
import { getSchemaForEntity as getSchemaForEntityAction } from 'store/schema/thunks';
import { FieldWithStatusModel, GraphStatus } from 'store/schema/types';
import { updateUserPreferencesEntityFilter } from 'store/user/actions';
import { useSelectSyncStudioFieldFilterForEntity } from 'store/user/selector.hooks';
import { updateSyncStudioFieldFiltersForEntityId, updateSyncStudioHiddenFieldsForEntityId } from 'store/user/thunks';
import {
  USER_PREF_SYNC_STUDIO_FIELD_FILTER,
  userPrefSyncStudioFieldFilters,
  UserPrefSyncStudioFieldFilters,
} from 'store/user/types';
import AppConstants from 'utils/AppConstants';
import { getNavigateParams, navigateTo } from 'utils/AppUtil';
import { t, tc, tNamespaced } from 'utils/i18nUtil';
import { getNodeConfigGroups } from 'utils/NodeConfigUtil';
import { getGraphVersionUrl } from 'utils/PipelineUtil';
import { filterItems } from 'utils/StringUtil';
import useSetState from 'utils/useSetState';

import { useDynamicConfig } from '../node-config/Config.hooks';
import EntityTagsInput from './EntityTagsInput';

import './EntityEditorEntityPanel.less';

const { FETCH_STATUS } = AppConstants;

type EnhancedFieldWithStatusModel = FieldWithStatusModel & {
  title: string;
  subtitle: string;
  description: string;
  hidden: boolean;
  link?: string;
};

const buildFilterSelectionsMap = (filterSelections: USER_PREF_SYNC_STUDIO_FIELD_FILTER[] = []) => {
  return userPrefSyncStudioFieldFilters.reduce((filterObject, filterKey) => {
    filterObject[filterKey] = filterSelections.includes(filterKey);
    return filterObject;
  }, {} as UserPrefSyncStudioFieldFilters);
};

const tn = tNamespaced('EntityEditorEntityPanel');

export interface EntityEditorEntityPanelProps {
  entityId: string;
  entityName: string;
  graphVersion: GraphStatus;
  editable?: boolean;
  actions?: PropertyPanelActionModel[];
  node: NodeModel;
  onClose: () => void;
}

const connector = connect(
  (state: RootState, props: EntityEditorEntityPanelProps) => ({
    tagsSuggest: state.tag.tags,
    tagsFetching: state.tag.tagsFetching,

    entitySchema: selectSchemaForEntity(state, props),
    entitySchemaStatus: selectSchemaStatusForEntity(state, props),

    // TODO: only include state that is necessary
    connectorEntities: state.entityPipeline.connectorEntities,
    fieldPipelineFunctions: state.pipelineFunction.fieldPipelineFunctions,
    entityPipelineFunctions: state.pipelineFunction.entityPipelineFunctions,
    fieldPipelineActions: state.pipelineAction.fieldPipelineActions,
    entityPipelineActions: state.pipelineAction.entityPipelineActions,
    pipelineContext: state.entityPipeline.pipelineContext,
    attributeNodes: state.fieldPipeline.attributeNodes,
    markFieldPipelineReadyStatus: state.fieldPipeline.markFieldPipelineReadyStatus,
    markFieldPipelineReadyErrorMessage: state.fieldPipeline.markFieldPipelineReadyErrorMessage,
    markFieldPipelineNotReadyStatus: state.fieldPipeline.markFieldPipelineNotReadyStatus,
    markFieldPipelineNotReadyErrorMessage: state.fieldPipeline.markFieldPipelineNotReadyErrorMessage,

    // Navigation related states
    changed: state.pipeline.changed,
    changedId: state.pipeline.changedId,
    changedScope: state.pipeline.changedScope,
  }),
  (dispatch) => {
    return bindActionCreators(
      {
        getTagsLike,
        addTag,
        removeTag,
        getSchemaForEntity: getSchemaForEntityAction,
        showConfirmModal,
        discardFieldPipeline,
        markFieldPipelineReady,
        markFieldPipelineNotReady,
        setNavigatingTo,
      },
      dispatch
    );
  }
);

type EntityEditorEntityPanelPropsFromRedux = ConnectedProps<typeof connector>;

const EntityEditorEntityPanel = (props: EntityEditorEntityPanelProps & EntityEditorEntityPanelPropsFromRedux) => {
  const {
    entityId,
    entityName,
    graphVersion,
    entitySchema,
    entitySchemaStatus,
    markFieldPipelineReadyStatus,
    markFieldPipelineReadyErrorMessage,
    markFieldPipelineNotReadyStatus,
    markFieldPipelineNotReadyErrorMessage,
    getSchemaForEntity,
    editable,
    actions,
    node,
    onClose,
    // Create draft actions related props
    markFieldPipelineReady,
    markFieldPipelineNotReady,
  } = props;

  const dispatch = useEnhancedDispatch();

  const { filterSelections, hiddenFields } = useSelectSyncStudioFieldFilterForEntity(entityId);

  const [filterText, setFilterText] = useState('');
  const initialMarkReadyStatus = useRef(markFieldPipelineReadyStatus);
  const initialMarkNotReadyStatus = useRef(markFieldPipelineNotReadyStatus);
  const saveMappingsResponse = useSelector(selectSaveMappingsResponse);
  const deleteMappingsResponse = useSelector(selectDeleteMappingsResponse);
  const [activeTab, setActiveTab] = useState('field-pipelines');

  const [filterState, setFilterStateLocal] = useSetState<UserPrefSyncStudioFieldFilters>(() => {
    // Set the default filterState based on user preferences stored in the
    // database. If it's a published pipeline and no filters exist, show only
    // mapped fields by default.
    const useDefaultPublishedFilter =
      isEmpty(filterSelections) && upperCase(graphVersion) === AppConstants.GRAPH_STATUS.APPROVED;
    const initialFilters = useDefaultPublishedFilter ? [USER_PREF_SYNC_STUDIO_FIELD_FILTER.MAPPED] : filterSelections;
    return buildFilterSelectionsMap(initialFilters);
  });

  const [hiddenFieldsState, setHiddenFieldsState] = useState<string[]>(hiddenFields);

  const setFilterState = (update: Partial<UserPrefSyncStudioFieldFilters>) => {
    const newState = { ...filterState, ...update };

    setFilterStateLocal(newState);

    const selectedKeys = keys(pickBy(newState)) as USER_PREF_SYNC_STUDIO_FIELD_FILTER[];
    dispatch(updateUserPreferencesEntityFilter(entityId, selectedKeys));
    updateSyncStudioFieldFiltersForEntityId(entityId, selectedKeys);
  };

  const resetFilterState = () => {
    setFilterState(buildFilterSelectionsMap());
    dispatch(updateUserPreferencesEntityFilter(entityId, []));
  };

  useEffect(() => {
    if (initialMarkReadyStatus.current !== markFieldPipelineReadyStatus) {
      initialMarkReadyStatus.current = markFieldPipelineReadyStatus;
      if (markFieldPipelineReadyStatus === FETCH_STATUS.ERROR) {
        Modal.error({
          title: tn('mark_ready_failed'),
          content: markFieldPipelineReadyErrorMessage,
        });
      }
    }
  }, [markFieldPipelineReadyStatus, markFieldPipelineReadyErrorMessage]);

  useEffect(() => {
    if (initialMarkNotReadyStatus.current !== markFieldPipelineNotReadyStatus) {
      initialMarkNotReadyStatus.current = markFieldPipelineNotReadyStatus;
      if (markFieldPipelineNotReadyStatus === FETCH_STATUS.ERROR) {
        Modal.error({
          title: tn('mark_not_ready_failed'),
          content: markFieldPipelineNotReadyErrorMessage,
        });
      }
    }
  }, [markFieldPipelineNotReadyStatus, markFieldPipelineNotReadyErrorMessage]);

  useEffect(() => {
    // fetch the draft summaries
    getSchemaForEntity({ entityId, graphVersion });
  }, [entityId, getSchemaForEntity, graphVersion]);

  useEffectForValue(
    saveMappingsResponse,
    (response) => response?.success,
    () => getSchemaForEntity({ entityId, graphVersion })
  );

  useEffectForValue(
    deleteMappingsResponse,
    (response) => response?.success,
    () => getSchemaForEntity({ entityId, graphVersion })
  );

  const { dynamicConfig, isCoreEntityNode, isLoading, getDynamicNodeConfig } = useDynamicConfig();

  useEffect(() => {
    if (activeTab === 'deduplicate' && isCoreEntityNode) {
      getDynamicNodeConfig();
    }
  }, [activeTab, getDynamicNodeConfig, isCoreEntityNode]);

  const _getNodeConfig = React.useCallback(() => {
    const { metadata = {} } = node;
    const groups = getNodeConfigGroups(metadata, props);

    if (groups.length) {
      return groups.map((group) => (
        <TabPane
          tab={
            <Tab>
              <IconTooltip iconPath={group.iconPath} tooltipTitle={group.label} />
            </Tab>
          }
          key={group.name}>
          <ScrollableArea>
            <TabPanelSpin spinning={isLoading || !dynamicConfig}>
              <NodePanel
                actions={actions}
                showTitlePanel={false}
                editable={editable}
                groupConfiguration={group}
                node={node}
              />
            </TabPanelSpin>
          </ScrollableArea>
        </TabPane>
      ));
    } else {
      return (
        <TabPane
          tab={
            <Tab>
              <IconTooltip>
                <DataAuthority />
              </IconTooltip>
            </Tab>
          }
          key="2">
          <NodePanel actions={actions} showTitlePanel={false} editable={editable} node={node} />
        </TabPane>
      );
    }
  }, [actions, dynamicConfig, editable, isLoading, node, props]);

  const filteredFields = useMemo(() => {
    if (entitySchema?.fields) {
      const textFilteredFields = filterItems(entitySchema?.fields, filterText).map((field) => {
        if (entitySchemaStatus !== FETCH_STATUS.SUCCESS) {
          return field;
        }
        return {
          ...field,
          title: field.displayName || field.apiName || '',
          subtitle: field.apiName,
          description: field.description || tc('none'),
          hidden: hiddenFieldsState.includes(field.id),
          link: getGraphVersionUrl(entityId, graphVersion, field.id),
          draftLink: getGraphVersionUrl(entityId, AppConstants.GRAPH_STATUS.DRAFT, field.id),
        } as EnhancedFieldWithStatusModel;
      });
      const filteredFieldList = textFilteredFields.filter((field) => {
        const hasFilters = filter(filterState).length > 0;
        if (!hasFilters) {
          return !hiddenFieldsState.includes(field.id);
        }

        if (filterState.HIDDEN && hiddenFieldsState.includes(field.id)) {
          return true;
        }
        // Drafts that are marked as ready are filtered out of this filter
        if (filterState.DRAFT && field.hasChanges && !field.ready) {
          return true;
        }
        if (filterState.READY && field.ready) {
          return true;
        }
        if (filterState.MAPPED && field.isMapped) {
          return true;
        }
        if (filterState.NOT_MAPPED && !field.isMapped) {
          return true;
        }
        return false;
      });

      return orderBy(filteredFieldList, (field) => field.displayName.toLowerCase());
    }

    return [];
  }, [filterText, entitySchemaStatus, entitySchema?.fields, filterState, hiddenFieldsState, entityId, graphVersion]);

  const _onFieldClick = (action: string, item: FieldWithStatusModel) => {
    switch (action) {
      case FIELD_ACTIONS.CREATE_DRAFT:
        if (!item.hasChanges && item?.link) {
          dispatch(createDraftFieldPipeline(item.id)).then(() => {
            // Navigate to the draft pipeline after its created
            item.draftLink && navigateTo(item.draftLink, getNavigateParams({ ...props }));
          });
        }
        break;
      case FIELD_ACTIONS.DISCARD_DRAFT:
        Modal.confirm({
          title: t('PipelineEditor.delete_draft_question'),
          content: t('PipelineEditor.delete_draft_entity_pipeline'),
          okText: tc('delete'),
          cancelText: tc('cancel'),
          icon: <Icon type="exclamation-circle" />,
          onOk: () => props.discardFieldPipeline(item.id, { refreshSchemaForEntity: true, entityId, graphVersion }),
        });
        break;
      case FIELD_ACTIONS.NAVIGATE:
      case FIELD_ACTIONS.EDIT_DRAFT:
        if (item?.link) {
          navigateTo(item.link, getNavigateParams({ ...props }));
        }
        break;
      case FIELD_ACTIONS.MARK_READY:
        markFieldPipelineReady(entityId, item.id, item.isMapped);
        break;
      case FIELD_ACTIONS.MARK_NOT_READY:
        markFieldPipelineNotReady(entityId, item.id, item.isMapped);
        break;
      case FIELD_ACTIONS.HIDE:
        const updatedHiddenFields = [...hiddenFieldsState, item.id];

        setHiddenFieldsState(updatedHiddenFields);
        updateSyncStudioHiddenFieldsForEntityId(entityId, updatedHiddenFields);
        break;
      case FIELD_ACTIONS.SHOW:
        const filteredFields = hiddenFieldsState.filter((fieldId) => fieldId !== item.id);

        setHiddenFieldsState(filteredFields);
        updateSyncStudioHiddenFieldsForEntityId(entityId, filteredFields);
        break;
      default:
        break;
    }
  };

  // TODO: Move all TabPanels to their own file
  const TagsTab = (
    <TabPane
      tab={
        <Tab>
          <IconTooltip tooltipTitle={tn('tags')}>
            <Tag height="24" />
          </IconTooltip>
        </Tab>
      }
      key="tags">
      <EntityTagsInput entityId={entityId} />
    </TabPane>
  );

  const FieldPipelinesTab = (
    <TabPane
      tab={
        <Tab>
          <IconTooltip tooltipTitle={tn('field_pipelines')}>
            <Pipeline height="24" />
          </IconTooltip>
        </Tab>
      }
      key="field-pipelines">
      <div className="synri-field-filter-list">
        <TabPanelSpin delay={100} spinning={entitySchemaStatus === FETCH_STATUS.LOADING} tip={tc('loading')}>
          <InputFilter
            containerClassName="entity-editor-entity-panel__input-filter"
            onChange={(evt) => setFilterText(evt.currentTarget.value)}
            value={filterText}
            placeholder={tn('filter_field_pipeline')}
            filterCount={filter(filterState).length}
            clearFilters={resetFilterState}
            filterChildren={
              <Stack spacing="sm">
                {userPrefSyncStudioFieldFilters.map((filter) => (
                  <Checkbox
                    key={filter}
                    checked={filterState[filter]}
                    onChange={(evt) => setFilterState({ [filter]: evt.target.checked })}>
                    {tn(`filter_${filter}`)}
                  </Checkbox>
                ))}
              </Stack>
            }
          />
          {filteredFields.length === 0 && (
            <p className="text-center">
              <TranslatedText namespace="EntityEditorEntityPanel" text="no_fields_match_filters" />
            </p>
          )}
          <ScrollableArea>
            <FieldList<FieldWithStatusModel> onFieldClick={_onFieldClick} items={filteredFields} />
          </ScrollableArea>
        </TabPanelSpin>
      </div>
    </TabPane>
  );

  const nodeConfig = useMemo(() => {
    const { metadata = {} } = node;
    const groups = getNodeConfigGroups(metadata, props);
    return groups?.length ? _getNodeConfig() : null;
  }, [_getNodeConfig, node, props]);

  return (
    <div className="entity-editor-entity-panel">
      <PropertyPanelTitle title={entityName} onClose={onClose} />
      <Tabs destroyInactiveTabPane onTabClick={(tabName: string) => setActiveTab(tabName)}>
        {FieldPipelinesTab}
        {nodeConfig}
        {TagsTab}
      </Tabs>
    </div>
  );
};

export default connector(EntityEditorEntityPanel);
