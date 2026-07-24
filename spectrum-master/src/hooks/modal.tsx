import Input from 'antd/lib/input';
import Modal, { ModalFuncProps } from 'antd/lib/modal';
import { useCallback, useEffect, useState, useRef } from 'react';
import * as React from 'react';

import { tNamespaced } from 'utils/i18nUtil';

type AntModal = ReturnType<typeof Modal.confirm>;

// we're monkey patching the update functionality to maintain our customized content
type EnhancedModal = AntModal & {
  _originalUpdate?: AntModal['update'];
};

const tn = tNamespaced('Modals.UserInputConfirmation');

export const useUserInputConfirmationModal = (confirmationText = 'DELETE', confirmationInputPlaceholder?: string) => {
  const modalRef = useRef<EnhancedModal | null>(null);
  const placeholder = confirmationInputPlaceholder || tn('confirmation_text_placeholder', { confirmationText });
  const [userInput, setUserInput] = useState('');

  useEffect(() => {
    if (userInput === confirmationText) {
      modalRef.current?.update({ okButtonProps: { disabled: false } });
    } else {
      modalRef.current?.update({ okButtonProps: { disabled: true } });
    }
  }, [userInput, confirmationText]);

  const getModalContent = useCallback(
    (content: React.ReactNode) => (
      <div>
        <div>{content}</div>
        <br />
        <label>
          <Input required placeholder={placeholder} onChange={(evt) => setUserInput(evt.target.value)} />
        </label>
      </div>
    ),
    [placeholder]
  );

  const updateModal = useCallback(
    ({ content, ...newProps }: ModalFuncProps) => {
      if (modalRef.current) {
        const updateFn = modalRef.current._originalUpdate || modalRef.current.update;

        if (content) {
          updateFn({
            content: getModalContent(content),
            ...newProps,
          });
        } else {
          updateFn(newProps);
        }
      }
    },
    [getModalContent]
  );

  const showModal = useCallback(
    ({ content: givenContent, okButtonProps: givenOkButtonProps, ...modalProps }: ModalFuncProps) => {
      const content = getModalContent(givenContent);

      const modal: EnhancedModal = Modal.confirm({
        content,
        okButtonProps: { disabled: true, ...givenOkButtonProps },
        ...modalProps,
      });

      // monkey-patching the modal instance so that we can call `update` on the returned ref
      // in order to update the contents of the modal from a consumer. Helpful if you need to
      // add other controls or dynamic items to the modal content
      modal._originalUpdate = modal.update;
      modal.update = updateModal;

      modalRef.current = modal;
      return modal;
    },
    [getModalContent, updateModal]
  );

  return showModal;
};
