import { Icon } from 'antd';
import { find } from 'lodash';
import { useCallback, useEffect, useRef, useState } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { Instance, LOCAL_STORAGE_INSTANCE_ID } from 'store/instances/slice';
import { useSelectCurrentInstance } from 'store/user/selector.hooks';
import { selectAllUserInstances } from 'store/user/selectors';
import { tCommon as tc, tNamespaced } from 'utils/i18nUtil';

import Modal from './Modal';

const tn = tNamespaced('AlertInstanceMismatch');

// Show a modal to the user
const AlertInstanceMismatch = () => {
  const { id: instanceId } = useSelectCurrentInstance();
  const instances: Instance[] = useEnhancedSelector(selectAllUserInstances);

  const [activeInstanceId, setActiveInstanceId] = useState<null | string>();
  const modalRef = useRef<ReturnType<typeof Modal.confirm> | null>(null);

  useEffect(() => {
    if (activeInstanceId) {
      const activeInstance = find(instances, { syncariId: activeInstanceId });

      if (modalRef.current) {
        modalRef.current.destroy();
      }

      modalRef.current = Modal.confirm({
        title: tn('active_instance_name', { name: activeInstance?.displayName }),
        content: tn('multiple_syncari_windows_open'),
        okCancel: false,
        okText: tc('refresh'),
        type: 'warning',
        icon: <Icon type="info-circle" />,
        onOk: () => {
          // hard refresh the SPA to pick up new instance data
          window.location.assign('/');
        },
      });
    }
  }, [activeInstanceId, instances]);

  const setModalVisibleOnMismatch = useCallback(() => {
    const activeInstanceId = localStorage.getItem(LOCAL_STORAGE_INSTANCE_ID);

    // Check if the localStorage instanceId matches the instanceId in redux
    if (activeInstanceId && instanceId && instanceId !== activeInstanceId) {
      // We're setting state here instead of showing the modal because this
      // event listener may be triggered multiple times.
      setActiveInstanceId(activeInstanceId);
    }
  }, [instanceId]);

  useEffect(() => {
    window.addEventListener('storage', setModalVisibleOnMismatch);

    return () => {
      window.removeEventListener('storage', setModalVisibleOnMismatch);
    };
  }, [setModalVisibleOnMismatch]);

  return null;
};

export default AlertInstanceMismatch;
