//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Modal, Button } from 'antd';
import { useState, useCallback, useEffect, useRef } from 'react';
import * as React from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import CenterLayout from 'components/layout/CenterLayout';
import { RootState } from 'reducers/index';
import {
  showShareFragmentModal,
  shareFragment,
  getFragmentShares,
  resetShareFragmentModal,
} from 'store/fragment/actions';
import { Instance } from 'store/instances/slice';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './ShareFragmentModal.less';

const tn = tNamespaced('ShareFragmentModal');

const mapStateToProps = (state: RootState) => ({
  instances: state.user.instances,
  fragmentId: state.fragment.shareFragmentId,
  fragmentShares: state.fragment.fragmentShares,
  currentInstanceId: state.user.currentInstanceNextEdgeId,
  context: state.fragment.fragmentContext,
  fragmentSharing: state.fragment.fragmentSharing,
  fragmentSharingErrorMessage: state.fragment.fragmentSharingErrorMessage,
});

const mapDispatchToProps = (dispatch: Dispatch) => {
  return bindActionCreators(
    {
      showShareFragmentModal,
      shareFragment,
      getFragmentShares,
      resetShareFragmentModal,
    },
    dispatch
  );
};

const connector = connect(mapStateToProps, mapDispatchToProps);
type ShareFragmentModalPropsFromRedux = ConnectedProps<typeof connector>;
type ShareFragmentModalFormValues = Record<string, boolean>;
interface ShareFragmentModalProps {}

const ShareFragmentModal = ({
  showShareFragmentModal,
  shareFragment,
  fragmentId,
  instances,
  getFragmentShares,
  fragmentShares,
  currentInstanceId,
  context,
  fragmentSharing,
  fragmentSharingErrorMessage,
  resetShareFragmentModal,
}: ShareFragmentModalProps & ShareFragmentModalPropsFromRedux) => {
  const [formValues, setFormValues] = useState<ShareFragmentModalFormValues>({});
  const sharing = useRef<boolean>(fragmentSharing);
  const close = useCallback(() => {
    fragmentId && showShareFragmentModal(fragmentId, context, false);
    resetShareFragmentModal();
  }, [showShareFragmentModal, fragmentId, context, resetShareFragmentModal]);

  useEffect(() => {
    if (sharing.current !== fragmentSharing && fragmentSharing === false && !fragmentSharingErrorMessage) {
      close();
    }
    if (sharing.current !== fragmentSharing) {
      sharing.current = fragmentSharing;
    }
  }, [sharing, fragmentSharing, fragmentSharingErrorMessage, close]);

  useEffect(() => {
    fragmentId && getFragmentShares(fragmentId, context);
  }, [getFragmentShares, fragmentId, context]);

  useEffect(() => {
    if (fragmentShares && fragmentId && fragmentShares[fragmentId]) {
      const val: ShareFragmentModalFormValues = {};
      fragmentShares[fragmentId].forEach((instanceId) => {
        val[instanceId] = true;
      });
      setFormValues(val);
    }
  }, [fragmentShares, fragmentId]);

  const onInstanceCheck = (evt: React.ChangeEvent<HTMLInputElement>) => {
    setFormValues({
      ...formValues,
      [evt.target.name]: evt.target.checked,
    });
  };

  const share = () => {
    const checkedInstanceIds = Object.keys(formValues).filter((k) => formValues[k]);
    fragmentId && shareFragment(fragmentId, checkedInstanceIds, context);
  };

  return (
    <Modal
      title={tn('title')}
      className="share-fragment-modal"
      centered
      visible
      footer={
        <>
          {instances?.length <= 0 && (
            <Button key="cancel" onClick={close} type="primary">
              {tc('close')}
            </Button>
          )}
          {instances?.length > 0 && (
            <>
              <Button key="cancel" onClick={close}>
                {tc('cancel')}
              </Button>
              <Button key="ok" type="primary" onClick={share}>
                {tc('share')}
              </Button>
            </>
          )}
        </>
      }
      onOk={() => close()}
      onCancel={() => close()}
      destroyOnClose>
      <div className="content-container">
        {fragmentSharingErrorMessage && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={fragmentSharingErrorMessage}>
            {fragmentSharingErrorMessage}
          </InlineMessage>
        )}
        {instances?.length <= 0 && (
          <CenterLayout className="synri-share-fragment-empty">{tn('no_instance')}</CenterLayout>
        )}
        {instances?.length > 0 && (
          <>
            <div className="description">{tn('available_instances')}</div>
            <div className="synri-share-fragment-container">
              <div className="synri-share-fragment-scroll">
                {instances
                  .filter((instance: Instance) => instance.syncariId !== currentInstanceId)
                  .map((instance: Instance) => {
                    return (
                      <InputWithLabel
                        label={
                          <div className="instance-name-container" key={`instance-name-${instance.syncariId}`}>
                            <span>{instance.displayName || instance.name}</span>
                            <span className="instance-org-name">
                              {` ${tn('org_name', { orgName: instance.orgName || '' })}`}
                            </span>
                          </div>
                        }
                        key={instance.syncariId}
                        name={instance.syncariId}
                        datatype="checkbox"
                        onChange={onInstanceCheck}
                        checked={formValues[instance.syncariId]}
                      />
                    );
                  })}
              </div>
            </div>
          </>
        )}
      </div>
    </Modal>
  );
};

export default connector(ShareFragmentModal);
