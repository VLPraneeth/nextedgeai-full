// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Col, Icon, Input, Row, Select, Spin, message } from 'antd';
import cx from 'classnames';
import { each, filter, find, isEmpty, map } from 'lodash';
import { Component } from 'react';
import Highlighter from 'react-highlight-words';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { getEntityPipeline } from 'actions/entityPipelineActions';
import FieldTypeBadge from 'components/FieldTypeBadge';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import Modal from 'components/Modal';
import { getEntities, getFieldMapping, saveFieldMapping, showConnectorFieldModal } from 'store/entity/actions';
import AppConstants from 'utils/AppConstants';
import { getConnectorFieldsForMapping } from 'utils/EntityUtil';
import { tNamespaced, tc } from 'utils/i18nUtil';

import ConnectorEntityTable from './ConnectorEntityTable';

import './ConnectorEntityModal.scss';
import './ConnectorFieldModal.less';

const { Option } = Select;
const InputGroup = Input.Group;

const tn = tNamespaced('ConnectorFieldModal');

// Alias for input type
const DATA_TYPE = AppConstants.INPUT_TYPE;
const REFERENCE_TYPES = [DATA_TYPE.REFERENCE, DATA_TYPE.POLYMORPHIC_REFERENCE];

// Note: We should check out exporting the less variables to see if its worth doing
// or it will cause less/more maintenance problems...
const highlightStyle = { backgroundColor: '#ffc069', padding: 0 };

class ConnectorFieldModal extends Component {
  state = {
    selectedEntityId: this.props.manageConnectorField.synapseEntityId,
  };

  componentDidMount() {
    const { syncariEntityId, synapseEntityId } = this.props.manageConnectorField;
    this.props.getFieldMapping(syncariEntityId, synapseEntityId);
    this.props.getEntities();
  }

  close = () => {
    this.props.showConnectorFieldModal(false);
  };

  save = () => {
    if (!this.state.selectedFields || this.state.selectedFields?.length <= 0) {
      this.setState({
        errorMessage: tn('choose_field'),
      });
      return;
    }

    const { graphDraftId } = this.props.manageConnectorField;
    const selectedFieldId = map(this.state.selectedFields, (field) => field.synapseFieldId);
    const selectedFieldValues = filter(
      this.state.fieldMapping,
      (mapping) => selectedFieldId.indexOf(mapping.synapseFieldId) !== -1
    );

    const referenceToError = this.checkReferenceField(selectedFieldValues);
    if (!isEmpty(referenceToError)) {
      this.setState({
        errorMessage: tn('reference_field_check', { names: referenceToError.join(', ') }),
      });
      return;
    }

    const { synapseEntityId } = this.props.manageConnectorField;

    const fieldMapping = selectedFieldValues.map(
      ({ synapseFieldId, syncariEntityId, selectedConnectorIds, referenceEntityId }) => ({
        synapseFieldId,
        syncariEntityId,
        referenceEntityId,
        selectedConnectorIds,
        graphDraftId,
      })
    );
    this.setState({ creatingFields: true });
    this.props
      .saveFieldMapping(synapseEntityId, { fieldMapping }, { refreshFields: true })
      .then(() => {
        this.setState({ creatingFields: false });
        this.props.getEntityPipeline(this.props.manageConnectorField.syncariEntityId);
        message.success(tn('mapping_complete'));
      })
      .catch(() => {
        this.setState({ creatingFields: false });
        message.error(tn('mapping_failed'));
      });
  };

  checkReferenceField = (selectedFieldValues) => {
    const fieldNamesErrors = [];
    each(selectedFieldValues, (value) => {
      const { dataType, synapseFieldName, referenceEntityId } = value;
      const isReferenceDataType = REFERENCE_TYPES.includes(dataType);
      if ((referenceEntityId && !isReferenceDataType) || (!referenceEntityId && isReferenceDataType)) {
        fieldNamesErrors.push(synapseFieldName);
      }
    });
    return fieldNamesErrors;
  };

  _getFooter = () => {
    return (
      <>
        <Button key="cancel" onClick={this.close}>
          {tc('cancel')}
        </Button>
        <Button
          key="ok"
          type="primary"
          loading={this.state.creatingFields}
          disabled={this.props.connectorFieldsFetching}
          onClick={this.save}>
          {tn('create_selected')}
        </Button>
      </>
    );
  };

  getColumnSearchProps = (dataIndex, title) => ({
    filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }) => (
      <div className="synri-field-modal-filter-dropdown">
        <Input
          ref={(node) => {
            this.searchInput = node;
          }}
          placeholder={tn('search_field', { title })}
          value={selectedKeys[0]}
          onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
          onPressEnter={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
        />
        <Button
          type="primary"
          onClick={() => this.handleSearch(selectedKeys, confirm, dataIndex)}
          icon="search"
          size="small">
          {tc('search')}
        </Button>
        <Button onClick={() => this.handleReset(clearFilters)} size="small">
          {tc('reset')}
        </Button>
      </div>
    ),
    filterIcon: (filtered) => (
      <Icon type="search" className={cx(filtered ? 'synri-filter-dropdown-filtered' : undefined)} />
    ),
    onFilter: (value, record) => record[dataIndex].toString().toLowerCase().includes(value.toLowerCase()),
    onFilterDropdownVisibleChange: (visible) => {
      if (visible) {
        // Let the node get a chance to get rendered first before selecting the text
        setTimeout(() => this.searchInput.select());
      }
    },
    render: (text) =>
      this.state.searchedColumn === dataIndex ? (
        <Highlighter
          highlightStyle={highlightStyle}
          searchWords={[this.state.searchText]}
          autoEscape
          textToHighlight={text.toString()}
        />
      ) : (
        text
      ),
  });

  handleSearch = (selectedKeys, confirm, dataIndex) => {
    confirm();
    this.setState({
      searchText: selectedKeys[0],
      searchedColumn: dataIndex,
    });
  };

  handleReset = (clearFilters) => {
    clearFilters();
    this.setState({ searchText: '' });
  };

  _getFieldList = () => {
    const columns = [
      {
        title: tn('name'),
        dataIndex: 'synapseFieldName',
        key: 'synapseFieldName',
        className: 'synri-synapse-field-name',
        width: '40%',
        ...this.getColumnSearchProps('synapseFieldName', tn('name')),
        render: (title, record) => {
          return (
            <div className="synri-connector-field-name-container">
              <FieldTypeBadge
                className="synri-connector-field-badge"
                dataType={record.dataType}
                description={record.dataType}
              />
              <span title={title}>
                {this.state.searchedColumn === 'synapseFieldName' ? (
                  <Highlighter
                    highlightStyle={highlightStyle}
                    searchWords={[this.state.searchText]}
                    autoEscape
                    textToHighlight={title.toString()}
                  />
                ) : (
                  title
                )}
              </span>
            </div>
          );
        },
      },
      {
        title: tn('api_name'),
        dataIndex: 'synapseApiName',
        key: 'synapseApiName',
        className: 'synri-synapse-api-name',
        width: '20%',
        ...this.getColumnSearchProps('synapseApiName', tn('api_name')),
        render: (title, record) => {
          return (
            <div className="synri-connector-field-api-name-container">
              <span title={title}>
                {this.state.searchedColumn === 'synapseApiName' ? (
                  <Highlighter
                    highlightStyle={highlightStyle}
                    searchWords={[this.state.searchText]}
                    autoEscape
                    textToHighlight={title.toString()}
                  />
                ) : (
                  title
                )}
              </span>
            </div>
          );
        },
      },
      {
        title: tn('create_field_in'),
        dataIndex: 'selectedConnectorIds',
        key: 'selectedConnectorIds',
        editable: true,
        dataType: 'multiselect',
        width: '20%',
      },
      {
        title: tn('reference_to'),
        dataIndex: 'referenceEntityId',
        key: 'referenceEntityId',
        editable: true,
        editableRenderer: ({ record, dataIndex, editing, options }) => {
          if (editing) {
            const optionsData = map(record.entities, (entity) => {
              return (
                <Option value={entity.id} key={entity.id}>
                  {entity.displayName}
                </Option>
              );
            });
            if (record.isOffsetFieldReadOnly) {
              return <span>{record.name}</span>;
            } else {
              return (
                <Select
                  size="small"
                  style={{ width: '100%' }}
                  placeHolder={tn('choose_entity')}
                  ref={(node) => (options.input = node)}
                  showSearch
                  optionFilterProp="children"
                  filterOption={(input, option) =>
                    option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                  }
                  onPressEnter={options.saveAndToggle}
                  onChange={(value) => {
                    options.save({
                      target: {
                        value,
                        id: dataIndex,
                      },
                    });
                  }}
                  onBlur={options.saveAndToggle}>
                  {optionsData}
                </Select>
              );
            }
          } else {
            const val = record[dataIndex];
            const content = find(record.entities, (field) => {
              return field.id === val;
            });
            if (content) {
              return content.displayName;
            } else {
              return <span>{tn('choose_entity')}</span>;
            }
          }
        },
        width: '20%',
      },
    ];

    const { connectors } = this.props.manageConnectorField;
    const { connectorName } = this.props.manageConnectorField;

    const connectorList = connectors.filter((c) => c.name !== connectorName);

    const data = getConnectorFieldsForMapping(this.props.connectorFields, connectorList, this.props.entities);

    if (data?.length > 0) {
      return (
        <ConnectorEntityTable
          bordered
          columns={columns}
          dataSource={data}
          loading={this.props.fieldsFetching}
          onChange={this.onChange}
          scroll={{ y: 300 }}
        />
      );
    }
  };

  onChange = (fieldMapping, selectedFields) => {
    this.setState({
      errorMessage: '',
    });
    this.setState({
      fieldMapping,
      selectedFields,
    });
  };

  _getError = () => {
    const errorMessage = this.state.errorMessage || this.props.saveErrorMessage;
    if (errorMessage) {
      return (
        <InlineMessage title={errorMessage} type={InlineMessageTypes.ERROR}>
          {errorMessage}
        </InlineMessage>
      );
    }
  };

  _handleEntitySelection = (value) => {
    this.setState({ selectedEntityId: value });
    this.props.getFieldMapping(this.props.manageConnectorField.syncariEntityId, value);
  };

  _getContent = () => {
    let dropDown = '';

    if (this.props.manageConnectorField?.synapseNodes) {
      const synapseNodes = this.props.manageConnectorField.synapseNodes.map((entity) => (
        <Option key={entity.id} value={entity.configuration.entityDefinition}>
          {entity.name}
        </Option>
      ));
      dropDown = (
        <InputGroup className="sycr-input-group" size="medium">
          <Row gutter={8}>
            <Col span={5}>
              <span className="synri-label">Select an Entity</span>
            </Col>
            <Col span={8}>
              <Select
                name="fromEntityId"
                className="multi-select"
                onChange={this._handleEntitySelection}
                value={this.state.selectedEntityId}>
                {synapseNodes}
              </Select>
            </Col>
          </Row>
        </InputGroup>
      );
    }

    const fieldMappingTable = this._getFieldList();
    const error = this._getError();
    if (this.props.connectorFieldsFetching) {
      return (
        <Spin tip="Loading synapse entity fields…" spinning={this.props.fieldsFetching}>
          <div className="content-container" />
        </Spin>
      );
    }
    return (
      <div className="content-container">
        {error}
        {dropDown}
        {fieldMappingTable}
      </div>
    );
  };

  render() {
    const { manageConnectorField } = this.props;

    const content = this._getContent();
    const footer = this._getFooter();

    return (
      <Modal
        title={tn('unmapped_fields_in', { connectorName: manageConnectorField?.connectorName })}
        className="connector-entity-modal"
        centered
        visible
        footer={footer}
        width={900}
        onOk={() => this.close()}
        onCancel={() => this.close()}
        destroyOnClose>
        {content}
      </Modal>
    );
  }
}

const mapStateToProps = (state, props) => ({
  connectors: state.connector.connectors,
  connectorFields: state.entity.connectorFields,
  connectorFieldsFetching: state.entity.connectorFieldsFetching,
  manageConnectorField: state.entity.manageConnectorField,
  saveErrorMessage: state.entity.connectorFieldModalErrorMessage,
  entities: state.entity.entities,
});

const mapDispatchToProps = (dispatch) => {
  return bindActionCreators(
    {
      showConnectorFieldModal,
      getFieldMapping,
      saveFieldMapping,
      getEntities,
      getEntityPipeline,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(ConnectorFieldModal);
