// @ts-nocheck
import { Select } from 'antd';
import { find, map } from 'lodash';
import { Component } from 'react';
import { connect } from 'react-redux';

export const fixedWidthStyle = { width: 200 };

const Option = Select.Option;

class DependentSelect extends Component {
  state = {
    selectedConnectorEntities: [],
  };
  render = () => {
    let changeHandler = this.props.onChange;
    let selectOptions = [];

    if (this.props.connectors && this.props.record['toConnectorId']) {
      let selectedConnector = find(this.props.connectors, (o) => o.connectorId === this.props.record['toConnectorId']);
      selectOptions =
        selectedConnector && selectedConnector.entities
          ? map(selectedConnector.entities, (option) => (
              <Option key={option.id} value={option.id}>
                {option.displayName}
              </Option>
            ))
          : [];
    }
    return (
      <Select
        className="multi-select"
        name="toEntityId"
        filterOption={(input: string, option: SelectOption) =>
          option.props.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
        }
        showSearch
        optionFilterProp="children"
        dropdownMatchSelectWidth={false}
        onChange={changeHandler}
        style={fixedWidthStyle}
        defaultValue={this.props.record.toEntityId}>
        {selectOptions}
      </Select>
    );
  };
}

function mapStateToProps(state, props) {
  return {
    connectors: state.connector.connectors,
  };
}

export default connect(mapStateToProps)(DependentSelect);
