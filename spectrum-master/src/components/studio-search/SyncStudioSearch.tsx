import { ChangeEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { ICON_MAP, ICON_TYPE } from 'components/icons/Icons';
import { AutoCompleteSearchBox } from 'components/SearchBox';
import { StatusTagColors } from 'components/StatusTag';
import { useEnhancedSelector as useSelector } from 'hooks/redux';
import useDebounce from 'hooks/useDebounce';
import { useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { EMPTY_ARRAY } from 'store/constants';
import { GraphModel, Node } from 'store/pipeline/types';
import { get } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { isCmdOrCtrlPressed } from 'utils/EventHandlerUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import SearchItem from './SearchItem';

import './SyncStudioSearch.less';

// this will eventually progress into becoming a global search.
// but it is currently isolated to sync studio for v1

export type SearchItemNode = Node & {
  status?: string;
  description?: string;
  path?: string;
  title?: string;
  route?: string;
  iconPath?: string;
  parentId?: string;
  configuration: {
    description?: string;
  };
};

type SearchItemNodes = SearchItemNode[];

type SearchGroup = {
  title: string;
  children: SearchItemNodes | [];
};

type SearchValues = SearchGroup[];

const {
  NEW: NEW_STATUS,
  APPROVED: APPROVED_STATUS,
  DRAFT: DRAFT_STATUS,
  APPROVED_WITH_DRAFT,
} = AppConstants.GRAPH_STATUS;

const { FUNCTION, ACTION, CUSTOM_GROUP } = AppConstants.NODE_TYPE;
const { ENTITY, ATTRIBUTE, CORE_ATTRIBUTE, CORE_ENTITY } = AppConstants.SCOPE;

const STATUS_COLOR_BLUE: StatusTagColors = 'blue';
const STATUS_COLOR_ORANGE: StatusTagColors = 'orange';

const tn = tNamespaced('SyncStudio');

const SyncStudioSearch = () => {
  const [searchValues, setSearchValues] = useState<SearchValues>([]);
  const [searchText, setSearchText] = useState<string>('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [loading, setLoading] = useState<boolean>(false);
  const entities = useSelector((state) => state.entity.entities);

  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  const sortGroupItems = (items: SearchItemNodes) => {
    items
      .sort((a, b) => (a.name.toLowerCase() > b.name.toLowerCase() ? 1 : -1))
      .sort((a) => (a.status === APPROVED_STATUS || a.status === APPROVED_WITH_DRAFT ? -1 : 1));
    return items;
  };

  // set to any for res - type safety still occurs in nested functions
  const handleResWithData = useCallback(
    (res: any) => {
      const nodes = res.map((graph: GraphModel) => {
        const results = [...graph.nodes, ...(graph.groups ?? EMPTY_ARRAY)];

        return results.map((node: SearchItemNode) => {
          let routePipeline = '';
          let graphPathDescription = graph.name;

          if (graph.scope === ENTITY) {
            routePipeline = RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION;
          }

          if (graph.scope === ATTRIBUTE) {
            routePipeline = RouteConstants.FIELD_PIPELINE_GRAPH_VERSION;

            const entity = entities?.find((entity) => entity.id === graph.parentId);
            if (entity) {
              graphPathDescription = `${entity.displayName}/${graph.name}`;
            }
          }

          const route = makeUrl(routePipeline, {
            entityId: graph.scope === ATTRIBUTE ? graph.parentId : graph.targetId,
            fieldId: graph.targetId,
            graphVersion: graph.draftStatus?.toLowerCase(),
          });

          return {
            apiName: node.apiName,
            description: node?.configuration?.description || '',
            name: node.name,
            title: node.label,
            id: node?.id,
            route,
            path: `${graphPathDescription}`,
            status: graph.draftStatus,
            iconPath:
              node.nodeType === CORE_ATTRIBUTE || node.nodeType === CORE_ENTITY
                ? ICON_MAP[ICON_TYPE.SYNCARI_NO_STATUS]
                : node.iconPath || '',
            nodeType: node.nodeType,
          };
        });
      });

      const categorizeNodes = (() => {
        let functionGroup: SearchItemNodes = [];
        let actionGroup: SearchItemNodes = [];
        let entityGroup: SearchItemNodes = [];
        let fieldGroup: SearchItemNodes = [];
        let groupGroup: SearchItemNodes = [];

        // organize results
        nodes.flat().forEach((node: SearchItemNode) => {
          const labelNameIncludesSearchText = node.name.toLowerCase().includes(searchText.toLowerCase());
          const apiNameIncludesSearchText = node.apiName.toLowerCase().includes(searchText.toLowerCase());

          if (labelNameIncludesSearchText || apiNameIncludesSearchText) {
            if (node.nodeType === FUNCTION) {
              functionGroup.push(node);
            }
            if (node.nodeType === ACTION) {
              actionGroup.push(node);
            }
            if (node.nodeType === CORE_ENTITY) {
              entityGroup.push(node);
            }
            if (node.nodeType === CORE_ATTRIBUTE) {
              fieldGroup.push(node);
            }
            if (node.nodeType === CUSTOM_GROUP) {
              groupGroup.push(node);
            }
          }
        });

        return { functionGroup, actionGroup, entityGroup, fieldGroup, groupGroup };
      })();

      if (
        categorizeNodes.actionGroup.length > 0 ||
        categorizeNodes.functionGroup.length > 0 ||
        categorizeNodes.entityGroup.length > 0 ||
        categorizeNodes.fieldGroup.length > 0 ||
        categorizeNodes.groupGroup.length > 0
      ) {
        setSearchValues([
          {
            title: tc('actions'),
            children: sortGroupItems(categorizeNodes.actionGroup),
          },
          {
            title: tc('functions'),
            children: sortGroupItems(categorizeNodes.functionGroup),
          },
          {
            title: tc('entities'),
            children: sortGroupItems(categorizeNodes.entityGroup),
          },
          {
            title: tc('fields'),
            children: sortGroupItems(categorizeNodes.fieldGroup),
          },
          {
            title: tc('groups'),
            children: sortGroupItems(categorizeNodes.groupGroup),
          },
        ]);
      } else {
        setSearchValues([]);
      }

      setLoading(false);
    },
    [entities, searchText]
  );

  const handleResWithoutData = () => {
    setLoading(false);
    setSearchValues([]);
  };

  // replace with use query hook
  const getSearchResults = useCallback(async () => {
    if (!searchText) {
      setSearchValues([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    const res = await get(`${DataUrlConstants.PIPELINE}/search/${encodeURIComponent(searchText)}`);

    if (res.data.length > 0) {
      handleResWithData(res.data);
    }
    if (res.data.length === 0) {
      handleResWithoutData();
    }
  }, [searchText, handleResWithData]);

  const onSelect = useCallback(
    (e: React.MouseEvent<HTMLElement>, selectedItem: SearchItemNode) => {
      const route = selectedItem.route || '';

      setDropdownOpen(false);
      if (isCmdOrCtrlPressed(e)) {
        window.open(route);
      } else {
        updateSelectedNodeIdsQueryParam(null, route, selectedItem.id);
      }
    },
    [updateSelectedNodeIdsQueryParam]
  );

  const onChange = (e: ChangeEvent<HTMLInputElement>) => {
    setSearchText(e.target.value);
  };

  const searchResultsDropdown = useMemo(
    () =>
      searchValues?.map(
        (group) =>
          group.children.length > 0 && (
            <div className="sync-studio-search__menu-item-group" key={group.title}>
              <div className="sync-studio-search__menu-item-group-title">
                <p>{`${group.title} (${group.children.length})`}</p>
                <div className="sync-studio-search__menu-item-group-divider" />
              </div>
              {group.children?.map((option) => {
                let statusColor: StatusTagColors = STATUS_COLOR_ORANGE;
                let statusDisplayText = '';

                if (option.status === NEW_STATUS || option.status === DRAFT_STATUS) {
                  statusColor = STATUS_COLOR_ORANGE;
                  statusDisplayText = tc('draft');
                }
                if (option.status === APPROVED_STATUS || option.status === APPROVED_WITH_DRAFT) {
                  statusDisplayText = tc('published');
                  statusColor = STATUS_COLOR_BLUE;
                }
                return (
                  <SearchItem
                    key={`${option.name}-${option.apiName}-${option?.id}`}
                    option={option}
                    statusDisplayText={statusDisplayText}
                    statusColor={statusColor}
                    onClick={(e, option) => onSelect(e, option)}
                  />
                );
              })}
            </div>
          )
      ),
    [searchValues, onSelect]
  );

  const debouncedValue = useDebounce<string>(searchText, 1000);

  useEffect(() => {
    getSearchResults();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedValue]);

  return (
    <AutoCompleteSearchBox
      aria-label="sync-studio-search-input"
      loading={loading}
      value={searchText}
      dataSource={searchResultsDropdown}
      noDataMessage={tn('search_no_data')}
      onChange={onChange}
      openDropdown={() => setDropdownOpen(true)}
      closeDropdown={() => setDropdownOpen(false)}
      onSearch={getSearchResults}
      dropdownOpen={dropdownOpen}
      placeholder={tn('search_for_placeholder')}
    />
  );
};

export default SyncStudioSearch;
