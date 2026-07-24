// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
// TODO: Need to refactor this table to be more useful
// This is just hacked for the connector entity
//
import { Select, Form, Checkbox, Input } from 'antd';
import { find, map, isString, split, join, filter } from 'lodash';
import { createContext, Component } from 'react';
import * as React from 'react';

import Table from 'components/Table';
import { tNamespaced } from 'utils/i18nUtil';

import './ConnectorEntityTable.less';

const tn = tNamespaced('ConnectorEntityModal');

const { Option } = Select;

const EditableContext = createContext();

const EditableRow = ({ form, index, ...props }) => (
  <EditableContext.Provider value={form}>
    <tr {...props} />
  </EditableContext.Provider>
);

const EditableFormRow = Form.create()(EditableRow);

class EditableCell extends Component {
  state = {
    editing: false,
  };

  toggleEdit = () => {
    const editing = !this.state.editing;
    this.setState({ editing }, () => {
      if (editing && this.input) {
        this.input.focus();
      }
    });
  };

  save = (e, param) => {
    const { record, handleSave } = this.props;
    this.form.validateFields((error, values) => {
      // if (error && error[e.currentTarget.id]) {
      //   return;
      // }
      // this.toggleEdit();
      // e is string for picklist, weird ant select behavior
      if (isString(e)) {
        const item = find(record.offsetFieldList, (field) => {
          return field.id === e;
        });
        values.selectedOffsetFieldId = item?.id;
      } else if (e?.target?.type === 'checkbox') {
        values[e.target.id] = e.target.checked;
      } else if (e?.target?.id && e?.target.value) {
        // Handle normal html input values
        values[e.target.id] = e.target.value;
      }
      handleSave({ ...record, ...values });
    });
  };

  saveAndToggle = (e, param) => {
    this.save.call(this, e, param);
    this.toggleEdit();
  };

  renderCell = (form) => {
    this.form = form;
    const { children, dataIndex, record, dataType, editableRenderer } = this.props;
    const { editing } = this.state;
    let component,
      readonlyContent = children;

    if (editableRenderer) {
      const options = {
        saveAndToggle: this.saveAndToggle,
        save: this.save,
      };
      readonlyContent = component = editableRenderer({ record, dataIndex, editing, options });
      this.input = options.input;
    } else if (editing) {
      switch (dataType) {
        case 'multiselect':
          const opts = map(record.connectorList, (connector) => {
            return (
              <Option key={connector.id} value={connector.id}>
                {connector.name}
              </Option>
            );
          });

          component = (
            <Select
              size="small"
              mode="multiple"
              style={{ width: '100%' }}
              ref={(node) => (this.input = node)}
              optionFilterProp="children"
              onPressEnter={this.saveAndToggle}
              onChange={this.save}
              onBlur={this.saveAndToggle}>
              {opts}
            </Select>
          );
          break;
        case 'picklist':
          const options = map(record.offsetFieldList, (field) => {
            return (
              <Option key={field.id} value={field.id}>
                {field.label} ({field.name})
              </Option>
            );
          });
          if (record.isOffsetFieldReadOnly) {
            component = <span>{record.name}</span>;
          } else {
            component = (
              <Select
                size="small"
                style={{ width: '100%' }}
                placeHolder="Choose offset"
                ref={(node) => (this.input = node)}
                showSearch
                optionFilterProp="children"
                onPressEnter={this.saveAndToggle}
                onChange={this.save}
                onBlur={this.saveAndToggle}>
                {options}
              </Select>
            );
          }
          break;
        case 'boolean':
          const checked = record[dataIndex];
          component = (
            <Checkbox
              ref={(node) => (this.input = node)}
              onPressEnter={this.saveAndToggle}
              checked={checked}
              onChange={this.save}
              onBlur={this.saveAndToggle}>
              Yes
            </Checkbox>
          );
          break;
        default:
          component = (
            <Input ref={(node) => (this.input = node)} onPressEnter={this.saveAndToggle} onBlur={this.saveAndToggle} />
          );
          break;
      }
    } else {
      switch (dataType) {
        case 'multiselect':
          const idString = record[dataIndex];
          const ids = split(idString, ',');
          const connectors = filter(record.connectorList, (field) => ids.includes(field.id));
          if (connectors.length > 0) {
            readonlyContent = join(
              map(connectors, (c) => c.name),
              ','
            );
          } else {
            readonlyContent = <span>Choose synapse&nbsp;</span>;
          }
          break;
        case 'picklist':
          const val = record[dataIndex];
          const content = find(record.offsetFieldList, (field) => {
            return field.id === val;
          });
          if (content) {
            readonlyContent = content.name;
          } else {
            readonlyContent = (
              <>
                <span>{tn('choose_offset')} </span>
                {record?.needsOffsetField && <span className="required">*</span>}
              </>
            );
          }
          break;
        case 'boolean':
          if (record[dataIndex]) {
            readonlyContent = <span>Yes</span>;
          } else {
            readonlyContent = <span>No</span>;
          }
          break;
        default:
          break;
      }
    }

    return editing ? (
      <Form.Item style={{ margin: 0 }}>
        {form.getFieldDecorator(dataIndex, {
          rules: [
            // {
            //   required: true,
            //   message: `${title} is required.`
            // }
          ],
          initialValue: record[dataIndex],
        })(component)}
      </Form.Item>
    ) : (
      <div className="editable-cell-value-wrap" style={{ paddingRight: 24 }} onClick={this.toggleEdit}>
        {readonlyContent}
      </div>
    );
  };

  render() {
    const {
      editable,
      dataIndex,
      title,
      record,
      index,
      handleSave,
      children,
      editableRenderer,
      dataType,
      ...restProps
    } = this.props;

    return (
      <td {...restProps}>
        {editable ? <EditableContext.Consumer>{this.renderCell}</EditableContext.Consumer> : children}
      </td>
    );
  }
}

class ConnectorEntityTable extends React.Component<any> {
  constructor(props) {
    super(props);
    this.state = {
      dataSource: this.props.dataSource,
      count: this.props.dataSource.length,
    };
  }

  handleSave = (row) => {
    const newDataSource = [...this.state.dataSource];
    const index = newDataSource.findIndex((item) => row.key === item.key);
    const item = newDataSource[index];
    newDataSource.splice(index, 1, {
      ...item,
      ...row,
    });
    this.setState({ dataSource: newDataSource });
    this.props.onChange(newDataSource, this.state.selectedRows);
  };

  render() {
    const { dataSource } = this.state;
    const components = {
      body: {
        row: EditableFormRow,
        cell: EditableCell,
      },
    };
    const columns = this.props.columns.map((col) => {
      if (!col.editable) {
        return col;
      }
      const { editableRenderer, ...rest } = col;
      return {
        ...rest,
        onCell: (record) => ({
          record,
          editable: col.editable,
          dataIndex: col.dataIndex,
          editableRenderer,
          title: col.title,
          dataType: col.dataType,
          handleSave: this.handleSave,
        }),
      };
    });

    const rowSelection = {
      onChange: (selectedRowKeys, selectedRows) => {
        this.setState({
          selectedRows,
        });
        this.props.onChange(this.state.dataSource, selectedRows);
      },
      getCheckboxProps: (record) => ({
        name: record.name,
      }),
    };
    return (
      <div>
        <Table
          className="connector-entity-table"
          rowSelection={rowSelection}
          components={components}
          onChange={this.props.onColumnChange}
          loading={this.props.loading}
          rowClassName={() => 'editable-row'}
          bordered={this.props.bordered}
          pagination={false}
          dataSource={dataSource}
          columns={columns}
          size="small"
          scroll={this.props.scroll}
        />
      </div>
    );
  }
}

export default ConnectorEntityTable;
