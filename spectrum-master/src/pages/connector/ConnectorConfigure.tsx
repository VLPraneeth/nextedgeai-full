//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Alert, Form, Icon, Input, Tooltip } from 'antd';
import classNames from 'classnames';
import { each, isEmpty } from 'lodash';
import { Component, useEffect, useState } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import InputWithLabel, { InputWithLabelProps } from 'components/inputs/InputWithLabel';
import Switch from 'components/Switch';
import { useGetWebhookCustomSypapseEndpointUrlQuery } from 'store/custom-synapse/webhook/api';
import { RootState } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { getFormErrorKey, getFormValidateStatusKey } from 'utils/ConnectorMetadataUtil';
import { connectorIsCustomDraft } from 'utils/ConnectorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { getPreferredDatatype, isCheckedValueDatatype } from 'utils/InputUtil';
import './ConnectorConfigure.less';
import { shouldShowField } from 'utils/NodeConfigUtil';
import { isNotUndefined } from 'utils/TypeUtils';

const tn = tNamespaced('ConnectorConfigure');

// Special field input that will
// tell us how to render the authenticate page
const AUTH_TYPE = 'authType';

class ConnectorConfigure extends Component<any, any> {
  state: any = {};

  componentDidMount() {
    const { currentStep, ...connectorValues } = this.props.getConnectorValues();
    this.setState({
      ...this.getDefaultValues(),
      ...connectorValues.metaConfig,
      ...connectorValues,
      authType: this.props.authType,
      valueInitialized: true,
      webhook: this.props.metadata.webhook,
    });
  }

  getDefaultValues() {
    const defaultValues: Record<string, string> = {};
    each(this.props.metadata.configureFields, ({ name, defaultValue, defaultChecked }) => {
      if (isNotUndefined(defaultValue) || isNotUndefined(defaultChecked)) {
        // Add our defaultValues and we're also using the defaultChecked for the defaultValue per BE request.
        defaultValues[name] = defaultValue ?? defaultChecked;
      }
    });
    return defaultValues;
  }

  componentWillUnmount() {
    const state: any = {};
    each(this.state, (value, key) => {
      if (key.match(/ValidateStatus$/i) || key.match(/Message$/i)) {
        // Skip any errorMessage
        return;
      }
      state[key] = value;
    });
    this.props.updateConnectorValues(state);
  }

  componentDidUpdate(prevProps: any) {
    // Change on the validate value will trigger
    // a validation for this step
    if (this.props.validate !== prevProps.validate) {
      if (this.props.validate === true) {
        this.validate();
      }
    }
  }

  configureInputValidate = () => {
    const { metadata: meta } = this.props;
    let valid = true;
    each(meta.configureFields, (field) => {
      // Don't need to validate auth type since
      // it will always have value
      if (field.name !== AUTH_TYPE) {
        if (field.dataType === AppConstants.INPUT_TYPE.CHECKBOX) {
          return;
        }

        const fieldValid = this.state[field.name] || !field.required;
        if (!fieldValid) {
          valid = false;
        }
        this.setState({
          [getFormValidateStatusKey(field.name)]: fieldValid ? '' : 'error',
          [getFormErrorKey(field.name)]: fieldValid ? '' : tc('cannot_be_empty', { name: field.label }),
        });
      }
    });
    return valid;
  };

  validate = () => {
    // validate
    let error = false;
    if (!this.state['name']) {
      error = true;
    }
    this.setState({
      [getFormValidateStatusKey('name')]: !this.state['name'] ? 'error' : '',
      [getFormErrorKey('name')]: !this.state['name'] ? tc('cannot_be_empty', { name: 'Name' }) : '',
    });

    const hasSyncRate = 'syncRate' in this.state;

    // if we have a syncRate set, then we need to validate it exists
    if (hasSyncRate) {
      this.setState({
        [getFormValidateStatusKey('syncRate')]: hasSyncRate && this.state.syncRate === '' ? 'error' : '',
        [getFormErrorKey('syncRate')]:
          hasSyncRate && this.state.syncRate === '' ? tc('cannot_be_empty', { name: 'Sync Rate Limit' }) : '',
      });
    }

    if (!this.configureInputValidate()) {
      error = true;
    }

    this.props.validated();
    if (!error) {
      this.props.next();
    }
  };

  _onInputChange = (evt: any) => {
    this.setState({
      [evt.target.name]: evt.target.value,
    });
  };

  _onCheckboxChange = (evt: any) => {
    this.setState({
      [evt.target.name]: evt.target.checked,
    });
  };

  _onPicklistChange = (name: string, value: string) => {
    this.setState({
      [name]: value,
    });
  };

  _getInput = (
    name: string,
    label: string,
    dataType: string,
    defaultValue: any,
    tooltip?: string,
    required?: boolean,
    inputProps?: Partial<InputWithLabelProps>
  ) => {
    if (this.state.valueInitialized) {
      let onChange = this._onInputChange;
      inputProps = inputProps || {};

      dataType = getPreferredDatatype(dataType);

      if (isCheckedValueDatatype(dataType)) {
        onChange = this._onCheckboxChange;
        inputProps.defaultChecked = defaultValue;
        inputProps.checked = this.state[name] ?? false;
      }

      if (dataType === AppConstants.INPUT_TYPE.PICKLIST) {
        inputProps.onChange = (value: string) => this._onPicklistChange(name, value);
      }

      if (!shouldShowField(this.state, inputProps)) {
        return;
      }
      return (
        <InputWithLabel
          key={name}
          name={name}
          onChange={onChange}
          defaultValue={defaultValue}
          value={this.state[name]}
          tooltip={tooltip}
          datatype={dataType}
          validateStatus={this.state[getFormValidateStatusKey(name)]}
          help={this.state[getFormErrorKey(name)]}
          label={label}
          required={required}
          {...inputProps}
        />
      );
    }
  };

  _getAuthTypeInput = () => {
    const values: { label: string; value: any }[] = [];
    const { metadata: meta } = this.props;

    each(meta.supportedAuthTypes, ({ label, authType: value }) => {
      values.push({ label, value });
    });

    const authTypeKey = 'type';

    return (
      <InputWithLabel
        name={authTypeKey}
        key={authTypeKey}
        onChange={this.handleTypeChange}
        defaultValue={this.props.authType}
        datatype={AppConstants.INPUT_TYPE.PICKLIST}
        validateStatus={this.state.typeValidateStatus}
        help={this.state[getFormErrorKey(authTypeKey)]}
        optionData={values}
        label={tn('authentication_method')}
      />
    );
  };

  _getConfigureInputs = () => {
    const { metadata: meta } = this.props;
    const inputs: any[] = [];
    each(meta.configureFields, ({ name, label, dataType, helpSummary: tooltip, required, ...inputProps }) => {
      if (name === AUTH_TYPE) {
        inputs.push(this._getAuthTypeInput());
      } else {
        const input = this._getInput(name, label, dataType, this.state[name], tooltip, required, inputProps);
        if (input) {
          inputs.push(input);
        }
      }
    });
    return inputs;
  };

  handleTypeChange = (authType: string) => {
    this.props.updateConnectorValues({
      authType,
    });
    this.setState({
      authType,
    });
  };

  handleEndpointChange = (endpoint?: string) => {
    this.setState({
      endpoint,
    });
  };

  _getInputs = () => {
    let inputs = [];
    inputs.push(this._getInput('name', tn('name'), AppConstants.INPUT_TYPE.TEXT, this.state.name, undefined, true));

    if (this.state?.webhook) {
      inputs.push(
        <EndpointUrlInput handleEndpointChange={this.handleEndpointChange} endpoint={this.state?.endpoint} />
      );
    }

    const configureInputs = this._getConfigureInputs();
    if (!isEmpty(configureInputs)) {
      inputs = inputs.concat(configureInputs);
    }

    return inputs.filter(Boolean);
  };

  render() {
    const { className } = this.props;
    const { syncRate } = this.state;
    const inputs = this._getInputs();

    return (
      <div className={classNames('synri-connector-configure', className)}>
        {connectorIsCustomDraft(this.props.metadata) && (
          <Alert type="info" message={tn('custom_synapse_warning')} showIcon />
        )}
        {inputs}

        <SyncLimitInput
          name="syncRate"
          value={syncRate}
          onChange={this._onInputChange}
          validateStatus={this.state[getFormValidateStatusKey('syncRate')]}
          errorMessage={this.state[getFormErrorKey('syncRate')]}
        />
      </div>
    );
  }
}

function SyncLimitInput({ name, onChange, placeholder = ' ', validateStatus, errorMessage, value }: any) {
  const [fieldShowing, setFieldShowing] = useState(!!value);

  const toggleField = () => {
    // currently showing, toggling off and clear value
    if (fieldShowing) {
      onChange({
        target: {
          name,
          value: undefined,
        },
      });
    } else {
      // not showing, toggle it on, set value to "" by default
      onChange({
        target: {
          name,
          value: '',
        },
      });
    }

    setFieldShowing((prev) => !prev);
  };

  // if the value gets set, then need to toggle the switch on
  useEffect(() => {
    if (value) {
      setFieldShowing(true);
    }
  }, [value]);

  return (
    <div className="sync-rate-config">
      <Switch
        checkedLabel={tn('sync_rate_toggle_on')}
        uncheckedLabel={tn('sync_rate_toggle_off')}
        label={
          <>
            {tn('sync_rate_toggle_label')}
            <span className="sync-rate-tooltip-container">
              <Tooltip title={tn('sync_rate_toggle_tooltip')} placement="right">
                <Icon type="question-circle" theme="filled" />
              </Tooltip>
            </span>
          </>
        }
        checked={fieldShowing}
        onChange={toggleField}
      />
      {fieldShowing && (
        <div className="sync-rate-input-container">
          <label className="sync-rate-input-label">
            <span>{tn('sync_rate_input_label_prefix')}</span>
            <Form.Item required validateStatus={validateStatus} help={errorMessage}>
              <Input
                className="sync-rate-input-control"
                required
                name={name}
                value={value}
                onChange={onChange}
                placeholder={placeholder}
              />
            </Form.Item>
            <span>{tn('sync_rate_input_label_suffix')}</span>
          </label>
        </div>
      )}
    </div>
  );
}

function EndpointUrlInput({
  handleEndpointChange,
  endpoint,
}: {
  handleEndpointChange: (endpoint?: string) => void;
  endpoint: string | undefined;
}) {
  const { data } = useGetWebhookCustomSypapseEndpointUrlQuery(undefined, {
    skip: !!endpoint?.trim(),
  });

  useEffect(() => {
    if (!endpoint?.trim()) {
      handleEndpointChange(data?.endpoint);
    }
  }, [data, handleEndpointChange, endpoint]);
  return (
    <InputWithLabel
      name="endpoint"
      value={endpoint}
      datatype={AppConstants.INPUT_TYPE.TEXT}
      label={tc('endpoint_url')}
      required
      readOnly
    />
  );
}

const mapStateToProps = (state: RootState) => ({
  metadata: state.connector.modalConnectorMetadata,
});

const mapDispatchToProps = (dispatch: Dispatch) => {
  return bindActionCreators({}, dispatch);
};

export default connect(mapStateToProps, mapDispatchToProps)(ConnectorConfigure);
