import { Button, Icon, Popover } from 'antd';
import cx from 'classnames';
import { useCallback, useMemo, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Divider, HStack, Stack } from 'components/layout';
import { tNamespaced, tc } from 'utils/i18nUtil';

import './JsonInput.scss';

const tn = tNamespaced('SwitchCaseInput');

export interface JsonInputPopupProps {
  defaultValue?: string;
  onChange?: (value: string) => void;
}

export const JsonInputPopup = ({ defaultValue, onChange }: JsonInputPopupProps) => {
  const [editVisible, setEditVisible] = useState(false);

  const onEdit = useCallback(() => setEditVisible(true), []);

  return (
    <InputWithLabel
      label={tc('value')}
      className="json-input-popup"
      input={
        <div className={cx('json-input-popup__input', defaultValue && 'json-input-popup__input--not-empty')}>
          <HStack spacing="xxxs" className="json-input-popup__preview">
            {defaultValue ? (
              <span className="json-input-popup__text" onClick={onEdit}>
                {defaultValue}
              </span>
            ) : (
              <a onClick={onEdit}>{tn('add_json_expression')}</a>
            )}
            {defaultValue ? <Icon type="edit" onClick={onEdit} /> : null}
          </HStack>
          {editVisible ? (
            <JsonInputEditor
              defaultValue={defaultValue}
              onSave={(value: string) => {
                onChange?.(value);
              }}
              onClose={() => {
                setEditVisible(false);
              }}
            />
          ) : null}
        </div>
      }
    />
  );
};

export interface JsonInputEditorProps {
  defaultValue?: string;
  onClose: () => void;
  onSave?: (value: string) => void;
}

export const JsonInputEditor = ({ defaultValue, onClose, onSave }: JsonInputEditorProps) => {
  const [bodyValue, setBodyValue] = useState(defaultValue || '');
  const save = useCallback(() => {
    onSave?.(bodyValue);
    onClose();
  }, [bodyValue, onClose, onSave]);

  const options = useMemo(
    () => ({
      highlightFormatting: true,
      maxBlockquoteDepth: 0,
      fencedCodeBlockHighlighting: true,
      mode: 'javascript',
      lineNumbers: true,
    }),
    []
  );

  return (
    <Popover
      placement="bottomLeft"
      trigger="click"
      overlayClassName="json-input-editor"
      visible
      content={
        <Stack className="json-input-editor__inputs">
          <CodeMirror
            value={bodyValue}
            options={options}
            onBeforeChange={(editor, data, value) => setBodyValue(value)}
          />
          <Divider y="z" />
          <div className="json-input-editor__footer">
            <Button size="small" onClick={onClose}>
              {tc('cancel')}
            </Button>
            <Button size="small" type="primary" onClick={save}>
              {tc('save')}
            </Button>
          </div>
        </Stack>
      }
    />
  );
};
