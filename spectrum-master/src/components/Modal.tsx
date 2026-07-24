//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { default as AntModal, ModalProps as AntModalProps } from 'antd/lib/modal';

// Make the confirm static function available for our Modal
// as well. It will be enhanced down the road.
export interface ModalStaticProps {
  confirm: typeof AntModal.confirm;
  warning: typeof AntModal.warning;
  error: typeof AntModal.error;
}

export type ModalProps = AntModalProps & { children?: React.ReactNode };

const Modal = ({ maskClosable = false, ...props }: ModalProps) => {
  return <AntModal maskClosable={maskClosable} {...props} />;
};

Modal.confirm = AntModal.confirm;
Modal.warning = AntModal.warning;
Modal.error = AntModal.error;

export default Modal;
