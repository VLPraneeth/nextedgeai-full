// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Component } from 'react';

import { ReactComponent as FragmentIcon } from 'assets/icons/fragment.svg';
import { ReactComponent as FxIcon } from 'assets/icons/pipeline-function.svg';
import GraphItemFilter from 'components/GraphItemFilter';
import { ACTION_ICON, SYNC_FROM_ICON, SYNC_TO_ICON } from 'components/icons/Icons';
import IconTooltip from 'components/icons/IconTooltip';
import Tabs from 'components/Tabs';
import ActionPanel from 'pages/sync-studio/action-studio/ActionPanel';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import FragmentPanel from '../fragment/FragmentPanel';

import './FieldPipelinePanel.less';

const { Tab, TabPane } = Tabs;

const tn = tNamespaced('FieldPipelinePanel');

class FieldPipelinePanel extends Component {
  state = { filterText: '' };

  _onSearch = (evt) => {
    this.setState({
      filterText: evt.currentTarget.value,
    });
  };

  _getList = (list, label, key, icon) => {
    if (list) {
      return (
        <TabPane
          tab={
            <Tab>
              <IconTooltip iconPath={icon} tooltipTitle={label} />
            </Tab>
          }
          key={key}>
          <GraphItemFilter filterPlaceHolder={tn('filter', { label })} items={list} />
        </TabPane>
      );
    }
  };

  render() {
    const { sourceFields, sinkFields, actions, functions } = this.props;

    const sourceFieldsList = this._getList(sourceFields, tn('sync_from_entities'), '3', SYNC_FROM_ICON);
    const sinkFieldsList = this._getList(sinkFields, tn('sync_to_entities'), '4', SYNC_TO_ICON);
    return (
      <>
        <div className="flow-field-pipeline">
          <Tabs>
            <TabPane
              tab={
                <Tab className="fns-tab">
                  <IconTooltip tooltipTitle={tc('functions')}>
                    <FxIcon height="24" />
                  </IconTooltip>
                </Tab>
              }
              key="1">
              <GraphItemFilter
                className="synri-functions"
                filterPlaceHolder={tn('function_filter_placeholder')}
                items={functions}
              />
            </TabPane>
            <TabPane
              tab={
                <Tab className="actions-tab">
                  <IconTooltip iconPath={ACTION_ICON} tooltipTitle={tc('actions')} />
                </Tab>
              }
              key="2">
              <ActionPanel actions={actions} />
            </TabPane>
            {sourceFieldsList}
            {sinkFieldsList}
            <TabPane
              className="fragment-tab"
              tab={
                <Tab className="fragment-tab">
                  <IconTooltip tooltipTitle="Fragments">
                    <FragmentIcon height="24" />
                  </IconTooltip>
                </Tab>
              }
              key="5">
              <FragmentPanel
                fragments={this.props.fragments}
                onCreateFragment={this.props.onCreateFragment}
                showShareFragmentModal={this.props.showShareFragmentModal}
                deleteFragment={this.props.deleteFragment}
                hideFragment={this.props.hideFragment}
                showFragment={this.props.showFragment}
                context={AppConstants.PIPELINE_CONTEXT.FIELD}
                deleteFragmentStatus={this.props.deleteFragmentStatus}
                deleteFragmentErrorMessage={this.props.deleteFragmentErrorMessage}
                hideFragmentStatus={this.props.hideFragmentStatus}
                hideFragmentErrorMessage={this.props.hideFragmentErrorMessage}
                showFragmentStatus={this.props.showFragmentStatus}
                showFragmentErrorMessage={this.props.showFragmentErrorMessage}
              />
            </TabPane>
          </Tabs>
        </div>
      </>
    );
  }
}

export default FieldPipelinePanel;
