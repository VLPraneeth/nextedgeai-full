//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Modal, Steps, Typography } from 'antd';
import { cloneDeep, first, isEmpty } from 'lodash';
import { useCallback } from 'react';

import {
  initializeConnectorModal,
  removeConnectorNode,
  showConnectorModal,
  testConnectorReset,
} from 'actions/connectorActions';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import { selectCurrentConnector } from 'selectors/connectorSelectors';
import { AuthTypes } from 'store/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { UserflowTags } from 'utils/UserflowTags';
import useSetState from 'utils/useSetState';

import ConnectorAuthenticate from './ConnectorAuthenticate';
import ConnectorConfigure from './ConnectorConfigure';
import ConnectorFinish from './ConnectorFinish';
import './ConnectorWizardModal.less';

const { Step } = Steps;
const { Text } = Typography;

const tn = tNamespaced('ConnectorWizardModal');

const STEP_MAP = {
  CONFIGURE: 0,
  AUTHENTICATE: 1,
  FINISH: 2,
};

const AUTH_TYPES = AppConstants.AUTH_TYPES;

interface ConnectorWizardModalState {
  currentStep: number;
  finishLabel: string;
  showCancel: boolean;
  showPrevious: boolean;
  validate?: boolean;
  authType?: AuthTypes;
  type?: string;
  metadataId?: string;
  syncRate?: number;
  status?: string;
  isAuthenticated: boolean;
}

const ConnectorWizardModal = () => {
  const connector = useEnhancedSelector(selectCurrentConnector);
  const connectorId = useEnhancedSelector((state) => state.connector.connectorId);
  const metadata = useEnhancedSelector((state) => state.connector.modalConnectorMetadata);

  const dispatch = useEnhancedDispatch();

  const [state, setState] = useSetState<ConnectorWizardModalState>({
    currentStep: 0,
    finishLabel: tc('finish'),
    isAuthenticated: false,
    showCancel: true,
    showPrevious: true,
  });

  const getDefaultAuthType = useCallback(() => {
    const defaultValue = first(metadata.supportedAuthTypes);

    return defaultValue ? defaultValue.authType : AUTH_TYPES.USER_PASSWORD_TOKEN;
  }, [metadata.supportedAuthTypes]);

  const initializeConnectorValues = useCallback(() => {
    if (connector) {
      const { id, key, apiConfig, autoSchemaSyncEntities, authConfig, setting, ...connectorValues } = connector;
      const syncRate = setting?.syncRate;
      const authType = connectorValues.metaConfig?.authType;

      setState({ ...authConfig, ...connectorValues, authType, syncRate });
    }
  }, [connector, setState]);

  useMountUnmountEffect(() => {
    if (!isEmpty(metadata)) {
      const { name, configId: metadataId } = metadata;
      setState({ authType: getDefaultAuthType(), type: name, metadataId });
    }
    if (connectorId) {
      initializeConnectorValues();
      dispatch(testConnectorReset());
    }

    return () => {
      dispatch(initializeConnectorModal());
    };
  });

  const close = () => {
    dispatch(showConnectorModal(false));
  };

  const finish = () => close();

  const cancel = () => {
    dispatch(removeConnectorNode(metadata.id));
    close();
  };

  const updateConnectorValues = (state: ConnectorWizardModalState) => {
    const { currentStep, ...connector } = state;
    setState(connector);
  };

  const validated = () => setState({ validate: false });

  const getConnectorValues = () => cloneDeep(state);

  const next = () => setState({ currentStep: state.currentStep + 1 });

  const previous = () => setState({ currentStep: state.currentStep - 1 });

  const validate = () => {
    setState({ validate: true });
  };

  const showFinishButtons = () =>
    setState({
      showCancel: false,
      showPrevious: false,
      finishLabel: tc('close'),
    });

  const getFooter = () => {
    let helpDocReference = null;

    // Only show the help article while the workflow is in progress
    if (metadata.helpUrl && state.currentStep !== STEP_MAP.FINISH) {
      helpDocReference = (
        <Text>
          <div dangerouslySetInnerHTML={{ __html: tn('need_help_html', { link: metadata.helpUrl }) }} />
        </Text>
      );
    }

    let nextButton;
    if (state.currentStep === STEP_MAP.FINISH) {
      nextButton = (
        <Button key="finish" type="primary" onClick={finish} data-userflow-tag={UserflowTags.SynapseStudio.Finish}>
          {state.finishLabel}
        </Button>
      );
    } else if (state.currentStep >= STEP_MAP.CONFIGURE) {
      const isAuthenticated = state.isAuthenticated || state.status === AppConstants.CONNECTOR_STATUS.AUTHENTICATED;

      nextButton = (
        <Button
          key="next"
          type="primary"
          onClick={validate}
          disabled={state.currentStep === STEP_MAP.AUTHENTICATE && !isAuthenticated}
          data-userflow-tag={UserflowTags.SynapseStudio.Next}>
          {tc('next')}
        </Button>
      );
    }
    let previousButton;
    if (state.currentStep > STEP_MAP.CONFIGURE && state.showPrevious) {
      previousButton = (
        <Button key="previous" onClick={previous}>
          {tc('previous')}
        </Button>
      );
    }
    const cancelButton = state.showCancel && (
      <Button key="cancel" onClick={cancel}>
        {tc('cancel')}
      </Button>
    );

    return (
      <div className="synri-connector-wizard-footer-container">
        <div>{helpDocReference}</div>
        <div>
          {cancelButton}
          {previousButton}
          {nextButton}
        </div>
      </div>
    );
  };

  const getContent = () => {
    const props = {
      authType: state.authType,
      setWizardModalState: setState,
      updateConnectorValues,
      getConnectorValues,
      next,
      finish,
      validate: state.validate,
      validated,
    };

    switch (state.currentStep) {
      case STEP_MAP.CONFIGURE:
        return <ConnectorConfigure {...props} />;
      case STEP_MAP.AUTHENTICATE:
        return <ConnectorAuthenticate {...props} />;
      case STEP_MAP.FINISH:
        return <ConnectorFinish {...props} showFinishButtons={showFinishButtons} close={close} />;
      default:
        throw new Error(`Invalid step ${state.currentStep}`);
    }
  };

  return (
    <Modal
      title={tn('create_title', { name: metadata.displayName || '' })}
      className="synri-connector-modal-wizard"
      centered
      keyboard={false}
      maskClosable={false}
      width={600}
      visible
      footer={getFooter()}
      onOk={close}
      onCancel={cancel}>
      <div className="synri-steps-container" data-userflow-tag={UserflowTags.SynapseStudio.Steps}>
        <Steps current={state.currentStep}>
          <Step title={tn('configure')} />
          <Step title={tn('authenticate')} />
          <Step title={tn('finish')} />
        </Steps>
      </div>
      <div className="synri-connector-wizard-container">{getContent()}</div>
    </Modal>
  );
};

export default ConnectorWizardModal;
