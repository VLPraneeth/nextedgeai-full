//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Icon from 'antd/lib/icon';
import { useRef } from 'react';

import { Stack } from 'components/layout';
import { Text } from 'components/typography';
import { useUserInputConfirmationModal } from 'hooks/modal';
import { tNamespaced } from 'utils/i18nUtil';
import { colors } from 'utils/LessConstants';

const tn = tNamespaced('NodeConfig');

export const useConfirmQuickStartExecuteModal = () => {
  const showDeleteModal = useUserInputConfirmationModal('PROCEED');
  const modalRef = useRef<ReturnType<typeof showDeleteModal> | null>(null);

  return (title: string, message: string, okButtonText: string, onOk?: () => void) => {
    modalRef.current = showDeleteModal({
      width: 500,
      icon: <Icon type="exclamation-circle" style={{ color: colors.red300 }} />,
      title,
      okText: okButtonText,
      content: (
        <Stack>
          <Text beDangerous>{message}</Text>
          <span>
            <Text beDangerous>{tn('confirm_proceed')}</Text>
          </span>
        </Stack>
      ),
      onOk,
    });
  };
};
