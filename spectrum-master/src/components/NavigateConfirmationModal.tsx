import { Modal } from 'antd';
import { createContext, useContext, useMemo, useRef, useState } from 'react';

import { tc } from 'utils/i18nUtil';

interface Content {
  title: string;
  message: string;
}

const ModalContext = createContext<
  | {
      isOpen: boolean;
      openModal: (open: boolean) => Promise<unknown>;
      content: {
        title: string;
        message: string;
      };
      setContent: (content: Content) => void;
    }
  | undefined
>(undefined);

export const NavigateConfirmationModal = ({ children }: { children?: React.ReactNode }) => {
  const [isOpen, setIsOpen] = useState<boolean>(false);
  const [content, setContent] = useState({
    title: '',
    message: '',
  });
  const resolveRef = useRef((data: boolean) => {});

  const value = useMemo(() => {
    return {
      isOpen,
      openModal: (open: boolean) => {
        setIsOpen(open);
        return new Promise((resolve) => {
          resolveRef.current = resolve;
        });
      },
      content,
      setContent: (content: Content) => setContent(content),
    };
  }, [content, isOpen]);

  const handleConfirm = () => {
    resolveRef.current(true);
    setIsOpen(false);
  };

  const handleCancel = () => {
    resolveRef.current(false);
    setIsOpen(false);
  };

  return (
    <ModalContext.Provider value={value}>
      {children}
      <Modal
        title={content.title}
        destroyOnClose
        visible={isOpen}
        onOk={handleConfirm}
        onCancel={handleCancel}
        okText={tc('exit')}>
        <div>{content.message}</div>
      </Modal>
    </ModalContext.Provider>
  );
};

export const useNavigateConfirmationModal = () => {
  const context = useContext(ModalContext);
  if (context === undefined) {
    throw new Error(`useNavigateConfirmationModal must be used within NavigateConfirmationModal`);
  }
  return context;
};
