// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Form, Input, Modal, Popconfirm, Select, Table } from 'antd';
import { find, groupBy, map } from 'lodash';
import { Component, Fragment } from 'react';
import * as React from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { setConnectorSetting, showConnectorSettingModal } from 'actions/connectorActions';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { SelectOption } from 'components/inputs/types';
import { getConnectorEntities, getEntities } from 'store/entity/actions';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import DependentSelect, { fixedWidthStyle } from './DependentSelect';

const Option = Select.Option;

const tn = tNamespaced('ConnectorSettingModal');

const EditableContext = React.createContext();

const EditableRow = ({ form, index, ...props }) => (
  <EditableContext.Provider value={form}>
    <tr {...props} />
  </EditableContext.Provider>
);

const EditableFormRow = Form.create()(EditableRow);

class EditableCell extends React.Component {
  state = {
    editing: true,
  };

  saveRecord = (e) => {
    const { record, handleSave, dataIndex } = this.props;
    if (e.currentTarget && e.currentTarget.value) {
      record[dataIndex] = e.currentTarget.value;
    } else {
      record[dataIndex] = e;
    }

    this.form.validateFields((error, values) => {
      if (error && error[e.currentTarget.id]) {
        return;
      }
      handleSave(record, dataIndex);
    });
  };

  renderCell = (form) => {
    this.form = form;
    const { children, record, col } = this.props;
    const { editing } = this.state;
    let rendered = col.render ? col.render('', record, this.saveRecord) : <Input onBlur={this.saveRecord} />;
    return editing ? (
      <Form.Item style={{ margin: 0 }}>{rendered}</Form.Item>
    ) : (
      <div className="editable-cell-value-wrap" style={{ paddingRight: 24 }} onClick={this.toggleEdit}>
        {children}
      </div>
    );
  };

  render() {
    const { editable, dataIndex, title, record, index, handleSave, children, col, ...restProps } = this.props;
    return (
      <td {...restProps}>
        {editable ? <EditableContext.Consumer>{this.renderCell}</EditableContext.Consumer> : children}
      </td>
    );
  }
}

const flattenSchemaSyncSettings = (schemaSyncSettings) => {
  let flattenedRecords = [];
  if (schemaSyncSettings) {
    for (var i = 0; i < schemaSyncSettings.length; i++) {
      for (var j = 0; j < schemaSyncSettings[i].toEntityIds.length; j++) {
        flattenedRecords.push({
          fromConnectorId: schemaSyncSettings[i].fromConnectorId,
          toConnectorId: schemaSyncSettings[i].toConnectorId,
          fromEntityId: schemaSyncSettings[i].fromEntityId,
          syncariEntityId: schemaSyncSettings[i].syncariEntityId,
          toEntityId: schemaSyncSettings[i].toEntityIds[j],
        });
      }
    }
  }
  return flattenedRecords;
};

const normalizeSchemaSyncSettings = (connectorId, schemaSyncSettings) => {
  if (schemaSyncSettings) {
    let grouped = groupBy(schemaSyncSettings, (setting) => setting.fromEntityId + '_' + setting.syncariEntityId);
    return map(grouped, (value, key) => {
      return {
        fromConnectorId: connectorId,
        toConnectorId: value[0].toConnectorId,
        fromEntityId: value[0].fromEntityId,
        syncariEntityId: value[0].syncariEntityId,
        toEntityIds: value.map((v) => v.toEntityId),
      };
    });
  } else {
    return [];
  }
};

class ConnectorSettingModal extends Component {
  constructor(props) {
    super(props);

    let currentFromConnector = find(this.props.connectors, (c) => c.connectorId === this.props.connectorRecord.id);
    if (!(currentFromConnector && currentFromConnector.entities)) {
      this.props.getConnectorEntities(this.props.connectorRecord.id);
    }
    let connectorOptions = () =>
      this.props.connectors
        .filter((c) => c.id !== this.props.connectorRecord.id)
        .map((option) => (
          <Option key={option.id} value={option.id}>
            {option.name}
          </Option>
        ));
    let sEntitiesOptions = () =>
      this.props.syncariEntities.map((option) => (
        <Option key={option.id} value={option.id}>
          {option.displayName}
        </Option>
      ));
    let fromEntitiesOptions = () => {
      let currentConnector = find(this.props.connectors, (c) => c.connectorId === this.props.connectorRecord.id);
      return currentConnector && currentConnector.entities
        ? currentConnector.entities.map((option) => (
            <Option key={option.id} value={option.id}>
              {option.displayName}
            </Option>
          ))
        : '';
    };

    this.columns = [
      {
        title: 'From Entity',
        dataIndex: 'fromEntityId',
        width: 240,
        editable: true,
        render: (text, record, changeHandler) => (
          <Select
            className="multi-select"
            name="fromEntityId"
            filterOption={this.selectFilterOption}
            showSearch
            optionFilterProp="children"
            dropdownMatchSelectWidth={false}
            onChange={changeHandler}
            placeholder={tn('select_entity')}
            style={fixedWidthStyle}
            defaultValue={record.fromEntityId}
          >
            {fromEntitiesOptions()}
          </Select>
        ),
      },
      {
        title: 'Sync To',
        dataIndex: 'toConnectorId',
        width: 240,
        editable: true,
        render: (text, record, changeHandler) => (
          <Select
            className="multi-select"
            name="toConnectorId"
            filterOption={this.selectFilterOption}
            showSearch
            optionFilterProp="children"
            dropdownMatchSelectWidth={false}
            onChange={changeHandler}
            placeholder={tn('select_entity')}
            style={fixedWidthStyle}
            defaultValue={record.toConnectorId}
          >
            {connectorOptions()}
          </Select>
        ),
      },
      {
        title: 'NextEdge Entity',
        dataIndex: 'syncariEntityId',
        width: 240,
        editable: true,
        render: (text, record, changeHandler) => (
          <Select
            className="multi-select"
            name="syncariEntityId"
            filterOption={this.selectFilterOption}
            showSearch
            optionFilterProp="children"
            dropdownMatchSelectWidth={false}
            onChange={changeHandler}
            placeholder={tn('select_entity')}
            style={fixedWidthStyle}
            defaultValue={record.syncariEntityId}
          >
            {sEntitiesOptions()}
          </Select>
        ),
      },
      {
        title: 'To Entity',
        dataIndex: 'toEntityId',
        width: 250,
        editable: true,
        render: (text, record, changeHandler) => (
          <DependentSelect name="toEntityId" onChange={changeHandler} record={record} />
        ),
      },
      {
        title: '',
        dataIndex: 'operation',
        render: (text, record) =>
          this.state.dataSource.length >= 1 ? (
            <Popconfirm title="Sure to delete?" onConfirm={() => this.handleDelete(record.key)}>
              {/* eslint-disable-next-line jsx-a11y/anchor-is-valid */}
              <a>Delete</a>
            </Popconfirm>
          ) : null,
      },
    ];

    this.state = {
      dataSource: [],
      count: 0,
      connectorId: props.connectorRecord.id,
      autoSchemaSyncEntities: flattenSchemaSyncSettings(this.props.connectorRecord.autoSchemaSyncEntities),
    };
  }

  selectFilterOption = (input: string, option: SelectOption) =>
    option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0;

  componentDidMount() {
    if (!this.props.syncariEntities) {
      this.props.getEntities();
    }

    if (this.props.connectorRecord.autoSchemaSyncEntities.length > 0) {
      let autoSchemaSyncEntities = flattenSchemaSyncSettings(this.props.connectorRecord.autoSchemaSyncEntities);
      const { count, dataSource } = this.state;
      const newData = [];
      const toConnectorIdsProcessed = [];
      for (var i = 0; i < autoSchemaSyncEntities.length; i++) {
        let row = autoSchemaSyncEntities[i];

        if (row.toConnectorId) {
          let currentToConnector = find(this.props.connectors, (c) => c.connectorId === row.toConnectorId);
          //call remote only if its not already called before
          if (
            !(currentToConnector && currentToConnector.entities) &&
            !toConnectorIdsProcessed.includes(row.toConnectorId)
          ) {
            this.props.getConnectorEntities(row.toConnectorId);
            toConnectorIdsProcessed.push(row.toConnectorId);
          }
        }
        newData[i] = {
          key: i,
          fromConnectorId: row.fromConnectorId,
          fromEntityId: row.fromEntityId,
          toConnectorId: row.toConnectorId,
          syncariEntityId: row.syncariEntityId,
          toEntityId: row.toEntityId,
        };
      }

      this.setState({
        dataSource: [...dataSource, ...newData],
        count: count + 1,
      });
    }
  }

  _onAutSchemaSyncChange = (checked, eventObj) => {
    const state = {};
    if (!checked) {
      state.autoSchemaSyncEntities = [];
    }
    this.setState(state);
  };

  close = () => {
    this.props.showConnectorSettingModal(false);
  };

  save = () => {
    let data = normalizeSchemaSyncSettings(this.props.connectorRecord.id, this.state.autoSchemaSyncEntities);
    this.props.setConnectorSetting(data, this.props.connectorRecord.id);
  };

  _getFooter = () => {
    let saveText;
    if (this.props.modalMode === AppConstants.MODAL_MODE.ADD) {
      saveText = tc('create');
    } else {
      saveText = tc('save');
    }
    return (
      <Fragment>
        <Button key="cancel" onClick={this.close}>
          {tc('cancel')}
        </Button>
        <Button key="ok" type="primary" onClick={this.save}>
          {saveText}
        </Button>
      </Fragment>
    );
  };

  _getErrorMessage = () => {
    const { createConnectorErrorMessage } = this.props;
    if (createConnectorErrorMessage) {
      return <InlineMessage type={InlineMessageTypes.ERROR}>{createConnectorErrorMessage}</InlineMessage>;
    }
  };

  handleDelete = (key) => {
    const dataSource = [...this.state.dataSource];
    let remaining = dataSource.filter((item) => item.key !== key);
    this.setState({ dataSource: remaining, autoSchemaSyncEntities: remaining });
  };

  handleAdd = () => {
    const { count, dataSource } = this.state;
    const newData = {
      key: dataSource.length,
      fromEntityId: ``,
      toConnectorId: ``,
      syncariEntityId: ``,
      toEntityId: ``,
    };

    this.setState({
      dataSource: [...dataSource, newData],
      count: count + 1,
    });
  };

  handleSave = (row, changedColumn) => {
    const newData = [...this.state.dataSource];
    const index = newData.findIndex((item) => row.key === item.key);
    const item = newData[index];
    newData.splice(index, 1, {
      ...item,
      ...row,
    });
    this.setState({ dataSource: newData, autoSchemaSyncEntities: newData });
    if (item['toConnectorId'] && changedColumn === 'toConnectorId') {
      let currentConnector = find(this.props.connectors, (c) => c.connectorId === item['toConnectorId']);
      if (!(currentConnector && currentConnector.entities)) {
        this.props.getConnectorEntities(item['toConnectorId']);
      }
    }
  };

  render() {
    const footer = this._getFooter();
    const title = tn('connector_setting') + this.props.connectorRecord.name;
    const { dataSource } = this.state;
    const components = {
      body: {
        row: EditableFormRow,
        cell: EditableCell,
      },
    };
    const columns = this.columns.map((col) => {
      if (!col.editable) {
        return col;
      }
      return {
        ...col,
        onCell: (record) => ({
          record,
          editable: col.editable,
          dataIndex: col.dataIndex,
          title: col.title,
          col,
          handleSave: this.handleSave,
        }),
      };
    });

    return (
      <Modal
        title={title}
        centered
        visible
        footer={footer}
        onOk={this.close}
        className="connector-modal"
        width={1100}
        onCancel={this.close}
      >
        <div>
          <Button onClick={this.handleAdd} type="primary" style={{ marginBottom: 16 }}>
            Add Setting
          </Button>
          <Table
            scroll={{ y: 300 }}
            components={components}
            rowClassName={() => 'editable-row'}
            bordered
            dataSource={dataSource}
            columns={columns}
            pagination={false}
            loading={this.props.fetchingConnectorSettings}
          />
        </div>
      </Modal>
    );
  }
}

const mapStateToProps = (state, props) => ({
  id: state.connector.id,
  connectorRecord: state.connector.connectorRecord,
  fetchingConnectorSettings: state.connector.fetchingConnectorSettings,
  connector: state.connector,
  connectors: state.connector.connectors,
  createConnectorErrorMessage: state.connector.createConnectorErrorMessage,
  syncariEntities: state.entity.entities,
  connectorEntities: state.entity.connectorEntities,
});

const mapDispatchToProps = (dispatch) => {
  return bindActionCreators(
    {
      showConnectorSettingModal,
      setConnectorSetting,
      getEntities,
      getConnectorEntities,
    },
    dispatch
  );
};

const connectorSettingModal = Form.create({ name: 'connector_setting_modal' })(ConnectorSettingModal);
export default connect(mapStateToProps, mapDispatchToProps)(connectorSettingModal);
