import cx from 'classnames';
import { useRef } from 'react';

import Button from 'components/Button';
import { useEnhancedDispatch } from 'hooks/redux';
import { CreateFieldModalMode, showCreateField } from 'store/fast-mapper/slice';
import { tNamespaced } from 'utils/i18nUtil';

import './AddSyncariField.scss';

const tn = tNamespaced('AddMapping');

export interface AddSyncariFieldProps {
  id: string;
}

export const AddSyncariField = ({ id }: AddSyncariFieldProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const dispatch = useEnhancedDispatch();

  const showCreateFieldModal = () => {
    const parentBoundingBox = containerRef.current?.parentElement?.getBoundingClientRect();

    dispatch(
      showCreateField({
        id,
        visible: true,
        mode: CreateFieldModalMode.CREATE,
        position: {
          top: parentBoundingBox?.top,
          left: parentBoundingBox?.left,
          width: parentBoundingBox?.width,
        },
      })
    );
  };

  return (
    <div ref={containerRef}>
      <Button
        className={cx('mapping-options', 'add-syncari-field')}
        data-testid="add-syncari-field-option"
        size="small"
        type="link"
        onMouseDown={showCreateFieldModal}>
        {tn('create_field')}
      </Button>
    </div>
  );
};
