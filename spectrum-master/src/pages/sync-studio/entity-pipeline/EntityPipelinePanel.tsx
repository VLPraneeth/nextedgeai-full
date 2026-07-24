// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Fragment, useState } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { ReactComponent as FragmentIcon } from 'assets/icons/fragment.svg';
import { ReactComponent as ActionIcon } from 'assets/icons/pipeline-action.svg';
import { ReactComponent as EntityIcon } from 'assets/icons/pipeline-entity.svg';
import { ReactComponent as FxIcon } from 'assets/icons/pipeline-function.svg';
import GraphItemFilter from 'components/GraphItemFilter';
import IconTooltip from 'components/icons/IconTooltip';
import Tabs from 'components/Tabs';
import { getIconFromPath } from 'components/icons/Icons';
import ActionPanel from 'pages/sync-studio/action-studio/ActionPanel';
import { showConnectorFieldModal } from 'store/entity/actions';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { UserflowTags } from 'utils/UserflowTags';

import FragmentPanel from '../fragment/FragmentPanel';

import './EntityPipelinePanel.less';

const { Tab, TabPane } = Tabs;

const tn = tNamespaced('EntityPipelinePanel');

const FnPanelKey = '1';
const ActionPanelKey = '2';
const EntitiesPanelKey = '3';

function EntityPipelinePanel({
  connectors,
  actions,
  functions,
  entityPipeline,
  showConnectorFieldModal,
  fragments,
  onCreateFragment,
  showShareFragmentModal,
  deleteFragment,
  hideFragment,
  showFragment,
  deleteFragmentStatus,
  deleteFragmentErrorMessage,
  hideFragmentStatus,
  hideFragmentErrorMessage,
  showFragmentStatus,
  showFragmentErrorMessage,
  getFragmentStatus,
}) {
  const [activeTab, setActiveTab] = useState(FnPanelKey);
  const updatedConnectors: any = connectors?.map((item: any) => {
    const icon = item?.iconUrl ? item.iconUrl : undefined;
    return {
      ...item,
      icon: icon ? getIconFromPath(icon) : item.icon,
    };
  });
  return (
    <Fragment>
      <div className="flow-entity-pipeline">
        <Tabs activeKey={activeTab} onChange={setActiveTab}>
          <TabPane
            tab={
              <Tab className="fns-tab">
                <IconTooltip tooltipTitle={tc('functions')}>
                  <FxIcon height="24" />
                </IconTooltip>
              </Tab>
            }
            key={FnPanelKey}>
            <GraphItemFilter filterPlaceHolder={tn('function_filter_placeholder')} items={functions} />
          </TabPane>
          <TabPane
            tab={
              <Tab className="actions-tab">
                <IconTooltip tooltipTitle={tc('actions')}>
                  <ActionIcon height="24" />
                </IconTooltip>
              </Tab>
            }
            key={ActionPanelKey}>
            <ActionPanel actions={actions} />
          </TabPane>
          <TabPane
            tab={
              <Tab className="entities-tab">
                <IconTooltip tooltipTitle={tn('connector_entities')}>
                  <EntityIcon height="24" />
                  <div className="entities-tab-bounding-box" data-userflow-tag={UserflowTags.SyncStudio.EntityTab} />
                </IconTooltip>
              </Tab>
            }
            key={EntitiesPanelKey}>
            <GraphItemFilter
              items={updatedConnectors}
              filterPlaceHolder={tn('entities_filter_placeholder')}
              entityPipeline={entityPipeline}
              showConnectorFieldModal={showConnectorFieldModal}
            />
          </TabPane>
          <TabPane
            className="tab-fragments"
            tab={
              <Tab className="fragment-tab">
                <IconTooltip tooltipTitle="Fragments">
                  <FragmentIcon height="24" />
                </IconTooltip>
              </Tab>
            }
            key="5">
            <FragmentPanel
              fragments={fragments}
              onCreateFragment={onCreateFragment}
              showShareFragmentModal={showShareFragmentModal}
              deleteFragment={deleteFragment}
              hideFragment={hideFragment}
              showFragment={showFragment}
              context={AppConstants.PIPELINE_CONTEXT.ENTITY}
              deleteFragmentStatus={deleteFragmentStatus}
              deleteFragmentErrorMessage={deleteFragmentErrorMessage}
              hideFragmentStatus={hideFragmentStatus}
              hideFragmentErrorMessage={hideFragmentErrorMessage}
              showFragmentStatus={showFragmentStatus}
              showFragmentErrorMessage={showFragmentErrorMessage}
              getFragmentStatus={getFragmentStatus}
            />
          </TabPane>
        </Tabs>
      </div>
    </Fragment>
  );
}

const mapStateToProps = (state, props) => ({
  entityPipeline: state.entityPipeline.entityPipeline,
});

const mapDispatchToProps = (dispatch) => {
  return bindActionCreators(
    {
      showConnectorFieldModal,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(EntityPipelinePanel);
