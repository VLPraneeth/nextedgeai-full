// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Form, Icon, Input, Tooltip } from 'antd';
import classNames from 'classnames';
import { camelCase, cloneDeep, each, isEmpty } from 'lodash';
import { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import {
  createConnectorWizard,
  getConnector,
  getConnectors,
  initializeConnectorModal,
  oauthenticate,
  oauthGetRedirectUrl,
  testConnector,
} from 'actions/connectorActions';
import ButtonImage from 'components/button-image';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { selectCurrentConnector, selectCurrentOauthRedirectUrl } from 'selectors/connectorSelectors';
import AppConstants from 'utils/AppConstants';
import { getFormErrorKey, getFormValidateStatusKey } from 'utils/ConnectorMetadataUtil';
import { findConnectorById, findConnectorMetadataByConnectorId } from 'utils/ConnectorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { getPreferredDatatype, isCheckedValueDatatype } from 'utils/InputUtil';
import { copyStringToClipboard } from 'utils/StringUtil';

import './ConnectorAuthenticate.less';

const tn = tNamespaced('ConnectorAuthenticate');
const AUTH_TYPES = AppConstants.AUTH_TYPES;
const REDIRECT_URL_NAME = 'redirectUrl';

class ConnectorAuthenticate extends Component {
  state = {};

  componentDidMount() {
    const connector = this.props.getConnectorValues();
    const { currentStep, ...connectorValues } = connector;
    this.setState({
      ...connectorValues,
      valueInitialized: true,
    });
  }

  componentWillUnmount() {
    const state = {};
    each(this.state, (value, key) => {
      if (key.match(/ValidateStatus$/i) || key.match(/Message$/i)) {
        // Skip any errorMessage
        return;
      }
      state[key] = value;
    });
    this.props.updateConnectorValues(state);
  }

  componentDidUpdate(prevProps, prevState) {
    if (this.props.connectorTesting === false && prevProps.connectorTesting === true) {
      if (!this.props.connectorTestErrorMessage) {
        this.setState({
          tested: true,
        });
      } else {
        this.setState({
          tested: false,
        });
      }
    }

    if (this.props.connectorCreating === false && prevProps.connectorCreating === true) {
      if (!this.props.createConnectorErrorMessage) {
        this.setState({
          created: true,
        });
      } else {
        this.setState({
          created: false,
        });
      }
      if (this.state.oAuthenticating === true) {
        this.props.oauthenticate(this.getCreateParams());
      }
    }

    // Change on the validate value will trigger
    // a validation for this step
    if (this.props.validate !== prevProps.validate) {
      if (this.props.validate === true) {
        this.validate();
      }
    }

    if (this.props.connectors !== prevProps.connectors && this.state.oAuthenticating === true) {
      const connector = findConnectorById(this.props.connectorId, this.props.connectors);
      if (connector) {
        if (connector.status === AppConstants.CONNECTOR_STATUS.AUTHENTICATED) {
          this.setState({
            oAuthenticating: false,
            created: true,
          });
          this.props.testConnector([this.getCreateParams()]);
        } else {
          this.setState({
            oAuthenticating: false,
            errorMessage: tn('validation_failed'),
          });
        }
      }
    }

    // Pass the authenticated status up to the wizard
    if (this.state.created !== prevState.created || this.state.tested !== prevState.tested) {
      this.props.setWizardModalState({ isAuthenticated: this.state.created && this.state.tested });
    }
  }

  validateAuthenticateFields = () => {
    const { metadata: meta } = this.props;
    let valid = true;
    each(meta.supportedAuthTypes, (auth) => {
      if (this.props.authType === auth.authType) {
        each(auth.fields, (field) => {
          const { name } = field;
          const fieldValid = this.state[name] || !field.required;
          if (!fieldValid) {
            valid = false;
          }
          this.setState({
            [getFormValidateStatusKey(field.name)]: fieldValid ? '' : 'error',
            [getFormErrorKey(field.name)]: fieldValid ? '' : tc('cannot_be_empty', { name: field.label }),
          });
        });
      }
    });
    return valid;
  };

  getAuthenticateFieldValues = () => {
    const { metadata: meta } = this.props;
    const values = {};
    each(meta.supportedAuthTypes, (auth) => {
      if (this.props.authType === auth.authType) {
        each(auth.fields, (field) => {
          const { name } = field;
          values[name] = this.state[name];
        });
      }
    });
    return values;
  };

  _getErrorMessage = (currentConnector) => {
    let errorMessage = tn('authenticate_to_proceed');
    if (currentConnector?.errorMessage) {
      errorMessage = currentConnector.errorMessage;
    }
    return errorMessage;
  };

  validate = () => {
    let valid = this.validateAuthenticateFields();

    if (valid) {
      let errorMessage;
      if (this.props.currentConnector) {
        const { currentConnector } = this.props;
        if (currentConnector.status !== AppConstants.CONNECTOR_STATUS.AUTHENTICATED) {
          errorMessage = this._getErrorMessage(currentConnector);
        }
      } else if (!this.props.connectorId) {
        errorMessage = this._getErrorMessage();
      } else {
        this.props.getConnectors();
        // Valid
        // this.props.getConnector(this.props.connectorId);
        // errorMessage = authenticatedErrorMessage;
      }
      if (errorMessage) {
        this.setState({ errorMessage });
        valid = false;
      }

      if (valid) {
        this.setState({
          errorMessage: '',
        });
      }
    }

    this.props.validated();

    if (valid) {
      this.props.next();
    }
  };

  _onInputChange = (name, dataType, evt) => {
    this.setState({
      [name]: isCheckedValueDatatype(dataType) ? evt.target.checked : evt?.target?.value,
    });
  };

  _getInput(name, label, dataType, defaultValue, tooltip, required, options) {
    if (!this.state.valueInitialized) {
      return;
    }

    // Disable visibility toggle unless the user has entered or edited the value
    const valueIsHidden = defaultValue === '*****' && dataType === AppConstants.INPUT_TYPE.PASSWORD;

    const extraInputProps = valueIsHidden
      ? {
          allowClear: true,
          visibilityToggle: false,
        }
      : {};

    return (
      <InputWithLabel
        key={name}
        name={name}
        onChange={this._onInputChange.bind(this, name, dataType)}
        defaultValue={defaultValue}
        value={this.state[name]}
        datatype={getPreferredDatatype(dataType)}
        tooltip={tooltip}
        required={required}
        validateStatus={this.state[getFormValidateStatusKey(name)]}
        help={this.state[getFormErrorKey(name)]}
        label={label}
        optionData={options}
        {...extraInputProps}
      />
    );
  }

  _getOauthOptions = () =>
    this.props.metadata.supportedAuthTypes.find((auth) => auth.authType === this.props.authType)?.options;

  _getOauthComps = () => {
    const cls = classNames('synri-redirect-url-container', {
      'synri-redirect-url-error': this.state[getFormErrorKey(REDIRECT_URL_NAME)],
    });
    const button = isEmpty(this.props.oauthRedirectUrl) ? (
      <Button onClick={this.getRedirectUrl} disabled={this.props.oauthRedirectUrlGetting} icon="file-add">
        {tn('generate')}
      </Button>
    ) : (
      <Button onClick={this.copyRedirectUrl} icon="copy">
        {tn('copy')}
      </Button>
    );
    let suffix = this.props.oauthRedirectUrlGetting ? <Icon type="loading" /> : '';

    const input = (
      <div className={cls}>
        <Form.Item
          validateStatus={this.state[getFormValidateStatusKey(REDIRECT_URL_NAME)]}
          help={this.state[getFormErrorKey(REDIRECT_URL_NAME)]}>
          <Input className="synri-redirect-url" disabled value={this.props.oauthRedirectUrl} suffix={suffix} />
        </Form.Item>
        {button}
      </div>
    );
    const { label, tooltip, ...buttonProps } = this._getAuthenticateButtonProps();
    const validateInput = (
      <Tooltip title={tooltip} placement="right">
        <Button {...buttonProps} onClick={this.oauthenticate}>
          {label}
        </Button>
      </Tooltip>
    );

    const options = this._getOauthOptions();
    const auth = this._getAuth();
    return [
      options?.oneClickOauth ? null : (
        <InputWithLabel
          key={REDIRECT_URL_NAME}
          name={REDIRECT_URL_NAME}
          input={input}
          label={tn('generate_register')}
        />
      ),
      auth.brandImagePath ? (
        <ButtonImage
          imageSrc={auth.brandImagePath}
          imageAlt={auth.brandImageText}
          key={this.props.authType}
          onClick={this.oauthenticate}
        />
      ) : (
        <InputWithLabel
          key="validateInput"
          name="validateInput"
          input={validateInput}
          label={options?.oneClickOauth ? tn('one_click_oauth') : tn('verify_authenticate')}
        />
      ),
    ].filter(Boolean);
  };

  _getAuth = () => {
    return this.props.metadata.supportedAuthTypes.find((auth) => auth.authType === this.props.authType);
  };

  getRedirectUrl = () => {
    if (this.validateAuthenticateFields()) {
      this.setState({
        [getFormValidateStatusKey(REDIRECT_URL_NAME)]: null,
        [getFormErrorKey(REDIRECT_URL_NAME)]: null,
      });
      this.props.oauthGetRedirectUrl(this.getCreateParams());
    }
  };

  copyRedirectUrl = () => {
    copyStringToClipboard(this.props.oauthRedirectUrl);
    this.setState({
      copiedToClipboard: true,
    });
  };

  validateOAuthExtraFields = () => {
    if (this._getOauthOptions()?.oneClickOauth) {
      return true;
    }

    const isRedirectUrlEmpty = isEmpty(this.props.oauthRedirectUrl);
    this.setState({
      [getFormValidateStatusKey(REDIRECT_URL_NAME)]: isRedirectUrlEmpty ? 'error' : '',
      [getFormErrorKey(REDIRECT_URL_NAME)]: isRedirectUrlEmpty ? tn('generate_redirect_url') : '',
    });
    return !isRedirectUrlEmpty;
  };

  oauthenticate = () => {
    if (this.validateAuthenticateFields() && this.validateOAuthExtraFields()) {
      this.setState({
        oAuthenticating: true,
        errorMessage: null,
      });
      this.props.createConnectorWizard(this.getCreateParams());
    }
  };

  getCreateParams = () => {
    const connectorValues = cloneDeep(this.props.getConnectorValues());
    if (this.props.connectorId) {
      if (!connectorValues.metadataId) {
        const metadata = findConnectorMetadataByConnectorId(
          this.props.connectorId,
          this.props.connectors,
          this.props.connectorsMetadata
        );
        if (metadata) {
          connectorValues.metadataId = metadata.id;
        }
      }
      connectorValues.connectorId = this.props.connectorId;
    }

    const { name, metadataId, connectorId } = connectorValues;
    const metaConfig = this.getConfigureFieldValues({
      ...this.state,
      ...connectorValues,
    });

    // extract syncRate from connectorValues, put on `setting` object
    const setting = 'syncRate' in connectorValues ? { syncRate: +connectorValues.syncRate } : {};

    // TODO: Remove the metaConfig spread when authType is no longer expected in the root and just in metaConfig
    return {
      name,
      metadataId,
      connectorId,
      ...metaConfig,
      metaConfig,
      authConfig: this.getAuthenticateFieldValues(),
      setting,
    };
  };

  getConfigureFieldValues = (params) => {
    const { metadata: meta } = this.props;
    const values = {};
    each(meta.configureFields, ({ name }) => {
      values[name] = params[name];
    });
    if (meta.webhook) {
      values.endpoint = params.endpoint;
    }

    // TODO: Remove this when the metadata for bootstrapWithSyncari is fixed.
    values.bootstrapWithSyncari = values.bootstrappable;
    return values;
  };

  validateUserPassword = () => {
    let valid = this.validateAuthenticateFields();
    if (valid) {
      const connectorValues = cloneDeep(this.props.getConnectorValues());
      if (this.props.connectorId) {
        connectorValues.connectorId = this.props.connectorId;
      }
      this.props.createConnectorWizard(this.getCreateParams(), { test: true, refresh: true }).then(() => {
        this.setState({ errorMessage: '' });
      });
    }
  };

  _getAuthenticateButtonProps = () => {
    let icon,
      label,
      className = '',
      disabled,
      tooltip;
    if (this.props.connectorCreating) {
      icon = 'loading';
      label = tn('creating');
      disabled = true;
    } else if (this.props.connectorTesting || this.state.oAuthenticating) {
      icon = 'loading';
      label = tn('authenticating');
      disabled = true;
    } else if (this.state.created && this.state.tested) {
      if (this.props.modalMode === AppConstants.MODAL_MODE.EDIT) {
        label = tn('saved_authenticated');
      } else {
        label = tn('authenticated');
      }
      icon = 'check-circle';
      className = 'synri-button-checked';
      disabled = false;
    } else if (this.props.modalMode === AppConstants.MODAL_MODE.EDIT) {
      label = tn('save_authenticate');
    } else {
      label = tn('authenticate');
    }

    if (this.props.currentConnector?.status === AppConstants.CONNECTOR_STATUS.ACTIVE) {
      disabled = true;
      tooltip = tn('deactivate_to_update');
    }

    return { icon, label, className, disabled, tooltip };
  };

  _getValidateComps = (authType) => {
    const { label, tooltip, ...buttonProps } = this._getAuthenticateButtonProps();
    const userPassword = (
      <Tooltip title={tooltip} placement="right">
        <Button {...buttonProps} onClick={this.validateUserPassword}>
          {label}
        </Button>
      </Tooltip>
    );

    return (
      <InputWithLabel
        key="default-auth-type"
        name={`btn${camelCase(authType)}`}
        input={userPassword}
        label={tn('authenticate_credentials')}
      />
    );
  };

  _getAuthExtraComps = (authType) => {
    switch (authType) {
      case AUTH_TYPES.OAUTH:
      case AUTH_TYPES.ONE_CLICK_OAUTH:
        return this._getOauthComps();
      default:
        return this._getValidateComps();
    }
  };

  _getInputs = () => {
    const meta = this.props.metadata;
    let inputs = [];
    each(meta.supportedAuthTypes, (auth) => {
      if (this.props.authType === auth.authType) {
        each(auth.fields, (field) => {
          const { name, dataType, label, helpSummary: tooltip, required, hidden = false, options = [] } = field;
          let defaultValue = '';
          if (this.state[name]) {
            defaultValue = this.state[name];
          }
          if (!hidden) {
            inputs.push(this._getInput(name, label, dataType, defaultValue, tooltip, required, options));
          }
        });
        const extraComps = this._getAuthExtraComps(auth.authType);
        if (!isEmpty(extraComps)) {
          inputs = inputs.concat(extraComps);
        }
      }
    });
    return inputs;
  };

  _getMessage = () => {
    let message;
    if (this.props.createConnectorErrorMessage) {
      message = this.props.createConnectorErrorMessage;
    } else if (this.props.oauthRedirectUrlErrorMessage) {
      message = this.props.oauthRedirectUrlErrorMessage;
    } else if (this.props.connectorTestErrorMessage) {
      message = this.props.connectorTestErrorMessage;
    } else if (this.state.errorMessage) {
      message = this.state.errorMessage;
    }
    if (message) {
      return (
        <InlineMessage type={InlineMessageTypes.ERROR} title={message}>
          {message}
        </InlineMessage>
      );
    }
  };

  render() {
    const { className } = this.props;
    const cls = classNames('synri-connector-authenticate', className);
    const inputs = this._getInputs();
    const message = this._getMessage();
    return (
      <>
        {message}
        <div className={cls}>{inputs}</div>
      </>
    );
  }
}

const mapStateToProps = (state, props) => ({
  metadata: state.connector.modalConnectorMetadata,
  oauthRedirectUrl: selectCurrentOauthRedirectUrl(state, props),
  createdConnectorId: state.connector.createdConnectorId,
  connectorId: state.connector.connectorId,
  connectorTesting: state.connector.connectorTesting,
  currentConnector: selectCurrentConnector(state, props),
  oauthRedirectUrlErrorMessage: state.connector.oauthRedirectUrlErrorMessage,
  connectorTestErrorMessage: state.connector.connectorTestErrorMessage,
  createConnectorErrorMessage: state.connector.createConnectorErrorMessage,
  connectorCreating: state.connector.connectorCreating,
  oauthRedirectUrlGetting: state.connector.oauthRedirectUrlGetting,
  modalMode: state.connector.modalMode,
  connectors: state.connector.connectors,
  connectorsMetadata: state.connector.connectorsMetadata,
});

const mapDispatchToProps = (dispatch) => {
  return bindActionCreators(
    {
      createConnectorWizard,
      oauthenticate,
      testConnector,
      oauthGetRedirectUrl,
      initializeConnectorModal,
      getConnector,
      getConnectors,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(ConnectorAuthenticate);
