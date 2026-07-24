// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import classNames from 'classnames';
import { createRef, Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { addConnectorNode } from 'actions/connectorActions';
import GettingStarted from 'assets/images/synapse-get-started.png';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './ConnectorGraphEmptyPage.less';

const tn = tNamespaced('ConnectorGraphEmptyPage');

class ConnectorGraphEmptyPage extends Component {
  constructor(props) {
    super(props);
    this.maskRef = createRef();
    window.setTimeout(() => {
      if (!this.maskRef) {
        return;
      }
      if (!this.maskRef.current) {
        return;
      }
      this.maskRef.current.addEventListener('dragover', (evt) => {
        if (evt.dataTransfer.types.indexOf('graph-node') !== -1) {
          evt.preventDefault();
        }
        // evt.dataTransfer.dropEffect = "copy";
      });
      this.maskRef.current.addEventListener('dragexit', (evt) => {
        // evt.dataTransfer.dropEffect = "none";
      });
      this.maskRef.current.addEventListener('dragend', (evt) => {
        // evt.dataTransfer.dropEffect = "none";
      });
      this.maskRef.current.addEventListener('dragleave', (evt) => {
        // evt.dataTransfer.dropEffect = "none";
      });
      this.maskRef.current.addEventListener('drop', (evt) => {
        const draggedData = evt.dataTransfer.getData('graph-object');
        if (draggedData) {
          const data = JSON.parse(draggedData);
          if (data) {
            this.props.addConnectorNode({
              ...data,
              x: evt.offsetX,
              y: evt.offsetY,
            });
          }
        }
      });
    }, 1000);
  }

  render() {
    const { className } = this.props;
    const cls = classNames('synri-connector-graph-empty', className);
    return (
      <div className={cls} ref={this.maskRef}>
        <div className="synri-blank-center-container">
          <div>
            <img src={GettingStarted} alt={tc('getting_started')} />
          </div>
          <div>{tn('add_synapse')}</div>
          <div>{tn('instruction')}</div>
        </div>
      </div>
    );
  }
}

const mapDispatchToProps = (dispatch) => {
  return bindActionCreators(
    {
      addConnectorNode,
    },
    dispatch
  );
};

export default connect(null, mapDispatchToProps)(ConnectorGraphEmptyPage);
