import AntModal, { ModalProps as AntModalProps } from 'antd/lib/modal';
import * as React from 'react';
import { useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { noop } from 'utils/AppUtil';

import { BatchOperationMode } from './types';

import './OperationModal.less';

export interface EnhancedModalProps
  extends Omit<AntModalProps, 'afterClose' | 'onCancel' | 'visible' | 'okButtonProps'> {
  children: React.ReactNode;
  okButtonProps?: {
    title?: string;
    type?: 'primary' | 'danger' | 'default' | 'dashed' | 'link' | 'ghost';
    disabled?: boolean;
    loading?: boolean;
  };
}

export const EnhancedModal = ({ children, okButtonProps, ...props }: EnhancedModalProps) => {
  const { modalProps } = useOperationModalContext();
  const antModalButtonProps = {
    okText: okButtonProps?.title,
    okType: okButtonProps?.type,
    okButtonProps: {
      disabled: okButtonProps?.disabled,
      loading: okButtonProps?.loading,
    },
  };
  return (
    <AntModal className="synri-batch-operation-modal" width={640} {...antModalButtonProps} {...props} {...modalProps}>
      <div className="synri-operation-modal-body">{children}</div>
    </AntModal>
  );
};

export interface OperationModalContextShape {
  modalProps: {
    afterClose: () => void;
    onCancel: () => void;
    visible: boolean;
  };
  closeModal: () => void;
}

const OperationModalContext = React.createContext<OperationModalContextShape>({
  modalProps: {
    afterClose: noop,
    onCancel: noop,
    visible: false,
  },
  closeModal: noop,
});

export const useOperationModalContext = () => useContext(OperationModalContext);

export type OperationModalProviderProps = {
  mode: BatchOperationMode;
  onRequestClose: () => void;
  children?: React.ReactNode;
};

const OperationModalProvider = ({ children, mode, onRequestClose }: OperationModalProviderProps) => {
  const [isVisible, setIsVisible] = useState(() => mode !== BatchOperationMode.NONE);

  const afterClose = useCallback(() => onRequestClose(), [onRequestClose]);
  const closeModal = useCallback(() => setIsVisible(false), []);

  useEffect(() => {
    setIsVisible(mode !== BatchOperationMode.NONE);
  }, [mode]);

  const contextValue = useMemo(
    () => ({
      modalProps: {
        afterClose,
        onCancel: closeModal,
        visible: isVisible,
      },
      closeModal,
    }),
    [afterClose, closeModal, isVisible]
  );

  return <OperationModalContext.Provider value={contextValue}>{children}</OperationModalContext.Provider>;
};

export default OperationModalProvider;
