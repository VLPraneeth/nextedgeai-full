// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate } from '@reach/router';
import { Icon, Button } from 'antd';
import classNames from 'classnames';
import { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { activateConnector } from 'actions/connectorActions';
import { SYNC_STUDIO_VIEW_SELECTION } from 'components/graph/constants';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import { selectCurrentConnector } from 'selectors/connectorSelectors';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import { UserflowTags } from 'utils/UserflowTags';

import './ConnectorFinish.less';

const tn = tNamespaced('ConnectorFinish');

class ConnectorFinish extends Component {
  state = {
    activated: false,
    activating: false,
  };

  componentDidMount() {
    this.props.showFinishButtons();
  }

  activate = (createPipelines) => {
    this.props.activateConnector([{ connectorId: this.props.connectorId }], createPipelines);
    this.setState({ activating: true });
  };

  componentDidUpdate(prevProps) {
    if (
      this.props.activatedConnectorId !== prevProps.activatedConnectorId &&
      this.props.connectorId === this.props.activatedConnectorId
    ) {
      if (
        this.props.connectorActivateErrorMessage &&
        this.props.connectorActivateErrorMessage !== prevProps.connectorActivateErrorMessage
      ) {
        this.setState({ activated: false, activating: false });
      } else if (!this.props.connectorActivateErrorMessage) {
        this.setState({ activated: true, activating: false });
      }
    }
  }

  getActivateButtonProps = () => {
    let label = tn('make_active'),
      createPipelineLabel = tn('activate_create_pipeline'),
      icon,
      disabled,
      createPipelineIcon = '',
      createPipelineClassName = '',
      className;
    if (this.state.activated) {
      if (this.props.createPipelines) {
        createPipelineLabel = tn('synapse_activated_pipeline_created');
        createPipelineIcon = 'check-circle';
        createPipelineClassName = 'synri-button-checked';
      } else {
        label = tn('synapse_activated');
        icon = 'check-circle';
        className = 'synri-button-checked';
      }
      disabled = true;
    } else if (this.state.activating) {
      if (this.props.createPipelines) {
        createPipelineLabel = tn('synapse_activating_create_pipeline');
        createPipelineIcon = 'loading';
      } else {
        icon = 'loading';
        label = tn('synapse_activating');
      }
      disabled = true;
    } else {
      disabled = false;
    }
    return {
      label,
      createPipelineLabel,
      icon,
      createPipelineIcon,
      disabled,
      className,
      createPipelineClassName,
    };
  };

  storedTab = localStorage.getItem(SYNC_STUDIO_VIEW_SELECTION) ?? 'details';

  mapEntities = () => {
    navigate(makeUrl(RouteConstants.ENTITIES, { tabId: this.storedTab }));
    this.props.close();
  };

  _getMessage = () => {
    let message;
    if (this.props.connectorActivateErrorMessage) {
      message = this.props.connectorActivateErrorMessage;
      return (
        <InlineMessage type={InlineMessageTypes.ERROR} title={message}>
          {message}
        </InlineMessage>
      );
    }
  };

  render() {
    const { className } = this.props;
    const cls = classNames('synri-connector-finish', className);
    const {
      label,
      createPipelineIcon,
      createPipelineLabel,
      createPipelineClassName,
      ...activateButtonProps
    } = this.getActivateButtonProps();
    const message = this._getMessage();
    return (
      <>
        {message}
        <div className={cls}>
          <div className="synri-finish-content">
            <div>
              <Icon type="check-circle" />
            </div>
            <p className="synri-finish-title">{tn('synapse_created')}</p>
            <p>{tn('do_next')}</p>
            <div className="synri-finish-actions">
              <Button
                type="primary"
                {...activateButtonProps}
                onClick={() => this.activate()}
                data-userflow-tag={UserflowTags.SynapseStudio.activate}>
                {label}
              </Button>
              <Button type="primary" onClick={this.mapEntities}>
                {tn('map_entities')}
              </Button>
              <Button
                type="primary"
                icon={createPipelineIcon}
                label={createPipelineLabel}
                className={createPipelineClassName}
                disabled={activateButtonProps.disabled}
                onClick={() => this.activate(true)}>
                {createPipelineLabel}
              </Button>
            </div>
          </div>
        </div>
      </>
    );
  }
}

const mapStateToProps = (state, props) => ({
  metadata: state.connector.modalConnectorMetadata,
  createdConnectorId: state.connector.createdConnectorId,
  connectorId: state.connector.connectorId,
  currentConnector: selectCurrentConnector(state, props),
  connectorActivating: state.connector.connectorActivating,
  createPipelines: state.connector.createPipelines,
  connectorActivateErrorMessage: state.connector.connectorActivateErrorMessage,
  activatedConnectorId: state.connector.activatedConnectorId,
});

const mapDispatchToProps = (dispatch) => {
  return bindActionCreators(
    {
      activateConnector,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(ConnectorFinish);
