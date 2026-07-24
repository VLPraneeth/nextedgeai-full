import { Button, Spin, Input, Icon } from 'antd';
import cx from 'classnames';
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { filter, find, map } from 'lodash';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Highlighter from 'react-highlight-words';

import Can from 'components/Can';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import Modal from 'components/Modal';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { Connector } from 'reducers/connectorReducer';
import { getEntityMapping, saveEntityMapping, showConnectorEntityModal } from 'store/entity/actions';
import { getEntityState, selectConnectorEntitiesForMapping } from 'store/entity/selectors';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { colors } from 'utils/LessConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import useSetState from 'utils/useSetState';

import ConnectorEntityTable from './ConnectorEntityTable';

import './ConnectorEntityModal.scss';

const tn = tNamespaced('ConnectorEntityModal');

const highlightStyle = { backgroundColor: colors.orange300, padding: 0 };

const render = (title: string, record: Record<string, string>) => <span title={title}>{title}</span>;

const scroll = { y: 340 };

export interface SynapseEntityValue {
  name: string;
  apiName: string;
  id: string;
  selectedOffsetFieldId?: string;
}

interface EntityMapping {
  connectorList: Connector[];
  createSyncariEntity: string;
  id: string;
  isCreateSyncariEntityReadOnly: boolean;
  key: string;
  name: string;
  offsetFieldList: { name: string; id: string; label: string };
  needsOffsetField: boolean;
  selectedConnectorIds: string[];
  selectedOffsetFieldId: string;
}

interface ConnectorEntityModalState {
  errorMessage: string | null;
  selectedEntities: EntityMapping[];
  entityMapping: EntityMapping[];
}

const ConnectorEntityModal = () => {
  const [state, setState] = useSetState<ConnectorEntityModalState>(() => ({
    errorMessage: null,
    selectedEntities: [],
    entityMapping: [],
  }));

  const [creatingEntities, setCreatingEntities] = useState(false);

  const dispatch = useEnhancedDispatch();

  const entity = useEnhancedSelector(getEntityState);
  const { connectorEntitiesFetching, manageConnectorEntity } = entity;
  const data = useEnhancedSelector(selectConnectorEntitiesForMapping);

  useEffect(() => {
    manageConnectorEntity?.connectorId && dispatch(getEntityMapping(manageConnectorEntity?.connectorId));
  }, [dispatch, manageConnectorEntity?.connectorId]);

  const close = () => {
    dispatch(showConnectorEntityModal(false));
  };

  const [sortedInfo, setSortedInfo] = useState<{ columnKey: string; order: string }>({
    columnKey: 'name',
    order: 'ascend',
  });

  const columnChange = useCallback((_: any, __: any, sorter: { columnKey: string; order: string }) => {
    setSortedInfo(sorter);
  }, []);

  const [searchState, setSearchState] = useState<{
    searchText: string;
    searchedColumn?: string;
  }>({ searchText: '' });

  const handleSearch = useCallback((selectedKeys: string[], confirm: () => void, dataIndex: string) => {
    confirm();
    setSearchState((current) => ({
      ...current,
      searchText: selectedKeys[0],
      searchedColumn: dataIndex,
    }));
  }, []);

  const handleReset = useCallback(
    (clearFilters: () => void) => {
      clearFilters();
      setSearchState({
        ...searchState,
        searchText: '',
      });
    },
    [searchState]
  );

  const searchInputRef = useRef<Input | null>(null);

  const getColumnSearchProps = useCallback(
    (dataIndex: string, title: string) => ({
      filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }: any) => (
        <div className="connector-entity-modal__filter-dropdown">
          <Input
            ref={searchInputRef}
            placeholder={tn('search_field', { title })}
            value={selectedKeys[0]}
            onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
            onPressEnter={() => handleSearch(selectedKeys, confirm, dataIndex)}
          />
          <Button
            type="primary"
            onClick={() => handleSearch(selectedKeys, confirm, dataIndex)}
            icon="search"
            size="small">
            {tc('search')}
          </Button>
          <Button onClick={() => handleReset(clearFilters)} size="small">
            {tc('reset')}
          </Button>
        </div>
      ),
      filterIcon: (filtered: boolean) => (
        <Icon type="search" className={cx(filtered ? 'connector-entity-modal--filtered' : undefined)} />
      ),
      onFilter: (value: string, record: Record<string, string>) =>
        record[dataIndex].toString().toLowerCase().includes(value.toLowerCase()),
      onFilterDropdownVisibleChange: (visible: boolean) => {
        if (visible) {
          // Let the node get a chance to get rendered first before selecting the text
          setTimeout(() => searchInputRef?.current?.select());
        }
      },
      render: (text: string) =>
        searchState.searchedColumn === dataIndex ? (
          <Highlighter
            highlightStyle={highlightStyle}
            searchWords={[searchState.searchText]}
            autoEscape
            textToHighlight={text.toString()}
          />
        ) : (
          text
        ),
    }),
    [handleReset, handleSearch, searchState]
  );

  const columns = useMemo(
    () => [
      {
        title: tc('name'),
        dataIndex: 'name',
        key: 'name',
        width: '30%',
        className: 'synri-entity-name',
        defaultSortOrder: 'ascend',
        sorter: (a: SynapseEntityValue, b: SynapseEntityValue) =>
          a?.name?.toLowerCase()?.localeCompare(b?.name?.toLowerCase()),
        sortOrder: sortedInfo.columnKey === 'name' && sortedInfo.order,
        sortDirections: ['descend', 'ascend'],
        ...getColumnSearchProps('name', tc('name')),
      },
      {
        title: tc('api_name'),
        dataIndex: 'apiName',
        key: 'apiName',
        editable: false,
        dataType: 'string',
        width: '30%',
        className: 'synri-entity-api-name',
        sorter: (a: SynapseEntityValue, b: SynapseEntityValue) =>
          a?.apiName?.toLowerCase()?.localeCompare(b?.apiName?.toLowerCase()),
        sortOrder: sortedInfo.columnKey === 'apiName' && sortedInfo.order,
        sortDirections: ['descend', 'ascend'],
        ...getColumnSearchProps('apiName', tc('api_name')),
      },
      {
        title: tc('id'),
        dataIndex: 'id',
        key: 'id',
        editable: false,
        dataType: 'string',
        width: '20%',
        render,
      },
      {
        title: tn('sync_offset'),
        dataIndex: 'selectedOffsetFieldId',
        key: 'selectedOffsetFieldId',
        editable: true,
        dataType: 'picklist',
        width: '20%',
      },
    ],
    [getColumnSearchProps, sortedInfo.columnKey, sortedInfo.order]
  );

  const save = async () => {
    if (!state.selectedEntities?.length) {
      setState({ errorMessage: tn('error_choose_entity') });
      return;
    }

    const selectedEntitiesId = map(state.selectedEntities, (ent) => ent.id);
    const selectedEntityValues = filter(
      state.entityMapping,
      (mapping) => selectedEntitiesId.indexOf(mapping.id) !== -1
    );

    // Validate offset fields only if needed by the backend
    const invalidEntity = find(
      selectedEntityValues,
      (entity) => entity.needsOffsetField && entity.selectedOffsetFieldId === null
    );

    if (invalidEntity) {
      setState({ errorMessage: tn('error_choose_offset') });
      return;
    }

    if (manageConnectorEntity?.connectorId) {
      setCreatingEntities(true);

      await dispatch(
        saveEntityMapping(
          manageConnectorEntity.connectorId,
          { entityMapping: selectedEntityValues },
          { refreshEntities: true }
        )
      );
      setCreatingEntities(false);
    }
    close();
  };

  const onChange = useCallback(
    (entityMapping: EntityMapping[], selectedEntities: EntityMapping[]) => {
      setState({
        errorMessage: '',
        entityMapping,
        selectedEntities,
      });
    },
    [setState]
  );

  const getContent = useCallback(() => {
    return (
      <div className="content-container">
        {state.errorMessage && <InlineMessage type={InlineMessageTypes.ERROR}>{state.errorMessage}</InlineMessage>}
        {data && data.length > 0 ? (
          <ConnectorEntityTable
            bordered
            columns={columns}
            onColumnChange={columnChange}
            dataSource={data}
            loading={connectorEntitiesFetching}
            onChange={onChange}
            scroll={scroll}
          />
        ) : (
          <div className="empty-content-wrapper">
            <span>{tn('all_fields_mapped')}</span>
          </div>
        )}
      </div>
    );
  }, [columnChange, columns, connectorEntitiesFetching, data, onChange, state?.errorMessage]);

  return (
    <Modal
      title={tn('title')}
      className="connector-entity-modal"
      centered
      visible
      footer={
        <>
          <Button key="cancel" onClick={close}>
            {tc('cancel')}
          </Button>
          <Can permission={AllPermissions.WRITE_STUDIO}>
            <Button
              className="create-entity-button"
              key="ok"
              type="primary"
              disabled={connectorEntitiesFetching}
              loading={creatingEntities}
              onClick={save}>
              {/*
                This is explicitly an empty string so the translation uses the singular
                version for zero instead of the plural version which would say "Create 0 Entities".
              */}
              {tn('create_entity', { count: state.selectedEntities?.length || '' })}
            </Button>
          </Can>
        </>
      }
      width={900}
      onOk={close}
      onCancel={close}
      destroyOnClose>
      {connectorEntitiesFetching ? (
        <Spin
          // Spin does not support sanitized text. Use plain text for now.
          tip={tn('loading', { name: manageConnectorEntity?.name })}
          spinning={connectorEntitiesFetching}>
          <div className="content-container" />
        </Spin>
      ) : (
        getContent()
      )}
    </Modal>
  );
};

export default ConnectorEntityModal;
