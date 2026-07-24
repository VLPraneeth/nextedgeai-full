/* eslint-disable jsx-a11y/anchor-is-valid */
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Component, Fragment, Suspense } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import { getConnectors } from 'actions/connectorActions';
import { showClonePipelineModal } from 'actions/entityPipelineActions';
import AlertInstanceMismatch from 'components/AlertInstanceMismatch';
import { ChangesInProgressModalVariants } from 'components/modals/ChangesInProgressModal';
import RouteSpin from 'components/RouteSpin';
import ServiceWorkerNotification from 'components/ServiceWorkerNotification';
import EntitySchemaModal from 'pages/schema-studio/EntitySchemaModal';
import { RootState } from 'store/types';
import { getVersion } from 'store/user/thunks';
import { EnhancedReactLazy } from 'utils/ModuleUtils';

const Home = EnhancedReactLazy(() => import('pages/Home'), { loadConcurrently: true });

// modals
const AboutModal = EnhancedReactLazy(() => import('pages/settings/AboutModal'));
const NodeKebabMenu = EnhancedReactLazy(() => import('components/graph/NodeKebabMenu'));
const ConnectorFieldModal = EnhancedReactLazy(() => import('pages/sync-studio/entity/ConnectorFieldModal'));
const ConnectorSettingModal = EnhancedReactLazy(() => import('pages/connector/ConnectorSettingModal'));
const ConnectorTooltip = EnhancedReactLazy(() => import('pages/connector/ConnectorTooltip'));
const CredentialModal = EnhancedReactLazy(() => import('pages/settings/credential/CredentialModal'));
const InstanceModal = EnhancedReactLazy(() => import('pages/settings/instance/InstanceModal'));
const InviteUserModal = EnhancedReactLazy(() => import('pages/settings/user/InviteUserModal'));
const PublishDraftModal = EnhancedReactLazy(() => import('pages/sync-studio/entity-pipeline/PublishDraftModal'));
const DeleteDraftModal = EnhancedReactLazy(() => import('pages/sync-studio/entity-pipeline/DeleteDraftModal'));
const ShareFragmentModal = EnhancedReactLazy(() => import('pages/sync-studio/fragment/ShareFragmentModal'));
const SubscriptionModal = EnhancedReactLazy(() => import('pages/settings/subscription/SubscriptionModal'));
const UnsavedPipelineConfirmModal = EnhancedReactLazy(() => import('pages/sync-studio/UnsavedConfirmModal'));
const ChangesInProgressModal = EnhancedReactLazy(() => import('components/modals/ChangesInProgressModal'));

export type HomeContainerProps = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps>;

class HomeContainer extends Component<HomeContainerProps> {
  componentDidMount() {
    // We are using the connector list if
    // we have a valid session
    this.props.getVersion();
  }

  _getAboutModal = () => {
    if (this.props.aboutModalVisible) {
      return <AboutModal key="about-modal" />;
    }
  };

  _getConnectorSettingModal = () => {
    if (this.props.connectorSettingModalVisible) {
      return <ConnectorSettingModal key="connector-settings-modal" />;
    }
  };

  _getSubscriptionModal = () => {
    if (this.props.subscriptionModalVisible) {
      return <SubscriptionModal key="subscription-modal" />;
    }
  };

  _getInstanceModal = () => {
    if (this.props.instanceModalVisible) {
      return <InstanceModal key="instance-modal" instance={this.props.instanceModalEditInstance} />;
    }
  };

  _getCredentialModal = () => {
    if (this.props.credentialModal) {
      return <CredentialModal key="credential-modal" />;
    }
  };

  _getInviteUserModal = () => {
    if (this.props.inviteUserModalVisible) {
      return <InviteUserModal key="invite-user-modal" />;
    }
  };

  _getPublishDraftModal = () => {
    if (this.props.publishDraftModalVisible) {
      return <PublishDraftModal key="publish-draft-modal" />;
    }
  };

  _getDeleteDraftModal = () => {
    if (this.props.deleteDraftModalVisible) {
      return <DeleteDraftModal key="delete-draft-modal" />;
    }
  };

  _getClonePipelineModal = () => {
    if (this.props.clonePipelineModalVisible) {
      return (
        <EntitySchemaModal
          key="entity-schema-modal"
          visible={this.props.clonePipelineModalVisible}
          entityId={this.props.clonePipelineEntityId}
          cloning
          onClose={() => {
            this.props.showClonePipelineModal(false, '', false);
          }}
        />
      );
    }
  };

  _getConnectorFieldModal = () => {
    if (this.props.connectorFieldModalVisible) {
      return <ConnectorFieldModal key="connector-field-modal" />;
    }
  };

  _getUnsavedPipelineConfirmModal = () => {
    if (this.props.unsavedConfirmModalVisible) {
      return <UnsavedPipelineConfirmModal key="unsaved-confirm-modal" />;
    }
  };

  _getChangesInProgressModal = () => {
    if (this.props.changesInProgressModal.visible) {
      if (this.props.changesInProgressModal.variant === ChangesInProgressModalVariants.upload) {
        return <ChangesInProgressModal variant="Upload" key="changes-in-progress-modal" />;
      }
      return <ChangesInProgressModal key="changes-in-progress-modal" />;
    }
  };

  _getNodeKebabMenu = () => {
    return (
      <Fragment key="node-kebab-floating-markup">
        <NodeKebabMenu key="node-kebab-dropdown" />
        <ConnectorTooltip key="connectorTooltip" />
      </Fragment>
    );
  };

  _getShareFragmentModal = () => {
    if (this.props.shareFragmentModalVisible) {
      return <ShareFragmentModal key="share-fragment-modal" />;
    }
  };

  _getAlertInstanceMismatch = () => {
    return <AlertInstanceMismatch key="get_alert_instance_mismatch" />;
  };

  _getModals = () => {
    return [
      this._getAboutModal(),
      this._getConnectorSettingModal(),
      this._getSubscriptionModal(),
      this._getInstanceModal(),
      this._getCredentialModal(),
      this._getInviteUserModal(),
      this._getPublishDraftModal(),
      this._getDeleteDraftModal(),
      this._getClonePipelineModal(),
      this._getConnectorFieldModal(),
      this._getChangesInProgressModal(),
      this._getUnsavedPipelineConfirmModal(),
      this._getShareFragmentModal(),
      this._getAlertInstanceMismatch(),
    ];
  };

  _getMenus = () => {
    return [this._getNodeKebabMenu()];
  };

  render() {
    return (
      <Suspense fallback={<RouteSpin />}>
        {process.env.NODE_ENV === 'production' && <ServiceWorkerNotification />}
        <Home {...this.props} />
        {this._getModals()}
        {this._getMenus()}
      </Suspense>
    );
  }
}

const mapStateToProps = (state: RootState) => ({
  ...state.connector,
  aboutModalVisible: state.user.aboutModalVisible,
  subscriptionModalVisible: state.subscription.subscriptionModalVisible,
  instanceModalVisible: state.instance.instanceModalVisible,
  instanceModalEditInstance: state.instance.instanceModalEditInstance,
  credentialModal: state.credential.credentialModal,
  inviteUserModalVisible: state.user.inviteUserModalVisible,
  publishDraftModalVisible: state.entityPipeline.publishDraftModalVisible,
  deleteDraftModalVisible: state.entityPipeline.deleteDraftModalVisible,
  clonePipelineModalVisible: state.entityPipeline.clonePipelineModalVisible,
  clonePipelineEntityId: state.entityPipeline.clonePipelineEntityId,
  connectorFieldModalVisible: state.entity.connectorFieldModalVisible,
  unsavedConfirmModalVisible: state.pipeline.unsavedConfirmModalVisible,
  shareFragmentModalVisible: state.fragment.shareFragmentModalVisible,
  changesInProgressModal: state.app.changesInProgressModal,
});

const mapDispatchToProps = (dispatch: Dispatch) => {
  return bindActionCreators(
    {
      getConnectors,
      getVersion,
      showClonePipelineModal,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(HomeContainer);
