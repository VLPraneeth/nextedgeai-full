// Based from https://github.com/ianstormtaylor/slate/blob/slate-react%400.100.1/site/examples/richtext.tsx
import { htmlToSlate } from '@slate-serializers/html';
import isHotkey from 'is-hotkey';
import { HTMLAttributes, MouseEvent, useCallback, useMemo } from 'react';
import { Editor, Transforms, createEditor, Descendant, Element as SlateElement, BaseEditor, Node, Text } from 'slate';
import { withHistory } from 'slate-history';
import { Editable, withReact, useSlate, Slate } from 'slate-react';

import AppConstants from 'utils/AppConstants';

import { Button, Icon, Toolbar } from './RichText.components';

import './RichTextInput.scss';

const HOTKEYS: Record<string, string> = {
  'mod+b': 'bold',
  'mod+i': 'italic',
  'mod+u': 'underline',
  'mod+`': 'code',
};

const LIST_TYPES = ['numbered-list', 'bulleted-list'];
const TEXT_ALIGN_TYPES = ['left', 'center', 'right', 'justify'];

interface SerializeParam {
  text?: string;
  type?: string;
  bold?: boolean;
  code?: boolean;
  italic?: boolean;
  underline?: boolean;
  children: SerializeParam[];
}

export const serializeNode = (nodes: Node[]) => nodes.map((node) => serialize(node as SerializeParam)).join('');

const serialize: (value: SerializeParam) => string = (value) => {
  if (Text.isText(value)) {
    let string = value.text;
    if (value.bold) {
      string = `<strong>${value.text}</strong>`;
    } else if (value.code) {
      string = `<code>${value.text}</code>`;
    } else if (value.italic) {
      string = `<em>${value.text}</em>`;
    } else if (value.underline) {
      string = `<u>${value.text}</u>`;
    }
    return string;
  }

  const children = value.children.map((node) => serialize(node)).join('');

  switch (value.type) {
    case 'block-quote':
    case 'blockquote':
    case 'qoute':
      return `<blockquote>${children}</blockquote>`;
    case 'bulleted-list':
    case 'ul':
      return `<ul>${children}</ul>`;
    case 'heading-one':
    case 'h1':
      return `<h1>${children}</h1>`;
    case 'heading-two':
    case 'h2':
      return `<h2>${children}</h2>`;
    case 'list-item':
    case 'li':
      return `<li>${children}</li>`;
    case 'numbered-list':
    case 'ol':
      return `<ol>${children}</ol>`;
    case 'paragraph':
    case 'p':
      return `<p>${children}</p>`;
    default:
      return children;
  }
};

export const deserializeNode: (value: string) => Descendant[] = (value) => htmlToSlate(value);

export interface RichTextInputHtmlProps {
  name: string;
  value?: string;
  defaultValue?: string;
  placeholder?: string;
  onChange?: (value: any) => void;
  displayMode?: string;
}

const RichTextInputHtml = ({
  value,
  defaultValue,
  onChange,
  name,
  placeholder,
  displayMode,
}: RichTextInputHtmlProps) => {
  const localValue = value || defaultValue || '<p></p>';

  const onChangeHandler = (value: Descendant[]) => {
    onChange?.({
      target: {
        name,
        value: serializeNode(value),
      },
    });
  };

  if (displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY) {
    return <div dangerouslySetInnerHTML={{ __html: localValue }} />;
  }

  return (
    <RichTextInput
      key={value === undefined ? 'empty' : 'non-empty'}
      defaultValue={deserializeNode(localValue)}
      onChange={onChangeHandler}
      placeholder={placeholder}
    />
  );
};

export interface RichTextInputProps {
  defaultValue: Descendant[];
  onChange?: (value: Descendant[]) => void;
  placeholder?: string;
}

const RichTextInput = ({ defaultValue, onChange, placeholder = '' }: RichTextInputProps) => {
  const renderElement = useCallback((props: any) => <Element {...props} />, []);
  const renderLeaf = useCallback((props: any) => <Leaf {...props} />, []);
  const editor = useMemo(() => withHistory(withReact(createEditor())), []);

  return (
    <div className="synri-rich-text-container">
      <Slate editor={editor} initialValue={defaultValue} onValueChange={onChange}>
        <Toolbar>
          <MarkButton format="bold" icon="format_bold" />
          <MarkButton format="italic" icon="format_italic" />
          <MarkButton format="underline" icon="format_underlined" />
          <MarkButton format="code" icon="code" />
          <BlockButton format="heading-one" icon="looks_one" />
          <BlockButton format="heading-two" icon="looks_two" />
          <BlockButton format="block-quote" icon="format_quote" />
          <BlockButton format="numbered-list" icon="format_list_numbered" />
          <BlockButton format="bulleted-list" icon="format_list_bulleted" />
        </Toolbar>
        <Editable
          renderElement={renderElement}
          renderLeaf={renderLeaf}
          placeholder={placeholder}
          spellCheck
          autoFocus
          onKeyDown={(event) => {
            for (const hotkey in HOTKEYS) {
              if (isHotkey(hotkey, event as any)) {
                event.preventDefault();
                const mark = HOTKEYS[hotkey];
                toggleMark(editor, mark);
              }
            }
          }}
        />
      </Slate>
    </div>
  );
};

const toggleBlock = (editor: BaseEditor, format: string) => {
  const isActive = isBlockActive(editor, format, TEXT_ALIGN_TYPES.includes(format) ? 'align' : 'type');
  const isList = LIST_TYPES.includes(format);

  Transforms.unwrapNodes(editor, {
    match: (n) =>
      !Editor.isEditor(n) &&
      SlateElement.isElement(n) &&
      LIST_TYPES.includes((n as any).type) &&
      !TEXT_ALIGN_TYPES.includes(format),
    split: true,
  });
  let newProperties: any;
  if (TEXT_ALIGN_TYPES.includes(format)) {
    newProperties = {
      align: isActive ? undefined : format,
    };
  } else {
    newProperties = {
      type: isActive ? 'paragraph' : isList ? 'list-item' : format,
    };
  }
  Transforms.setNodes<SlateElement>(editor, newProperties);

  if (!isActive && isList) {
    const block = { type: format, children: [] };
    Transforms.wrapNodes(editor, block);
  }
};

const toggleMark = (editor: BaseEditor, format: string) => {
  const isActive = isMarkActive(editor, format);

  if (isActive) {
    Editor.removeMark(editor, format);
  } else {
    Editor.addMark(editor, format, true);
  }
};

const isBlockActive = (editor: BaseEditor, format: string, blockType = 'type') => {
  const { selection } = editor;
  if (!selection) {
    return false;
  }

  const [match] = Array.from(
    Editor.nodes(editor, {
      at: Editor.unhangRange(editor, selection),
      match: (n) => !Editor.isEditor(n) && SlateElement.isElement(n) && (n as any)[blockType] === format,
    })
  );

  return !!match;
};

const isMarkActive = (editor: BaseEditor, format: string) => {
  const marks: any = Editor.marks(editor);
  return marks ? marks[format] === true : false;
};

interface ElementProps {
  attributes: HTMLAttributes<HTMLDivElement | HTMLUListElement | HTMLLIElement>;
  children: React.ReactNode | undefined;
  element: any;
}

const Element = ({ attributes, children, element }: ElementProps) => {
  const style = { textAlign: element.align };
  switch (element.type) {
    case 'block-quote':
    case 'blockquote':
    case 'qoute':
      return (
        <blockquote style={style} {...(attributes as any)}>
          {children}
        </blockquote>
      );
    case 'bulleted-list':
    case 'ul':
      return (
        <ul style={style} {...attributes}>
          {children}
        </ul>
      );
    case 'heading-one':
    case 'h1':
      return (
        <h1 style={style} {...attributes}>
          {children}
        </h1>
      );
    case 'heading-two':
    case 'h2':
      return (
        <h2 style={style} {...attributes}>
          {children}
        </h2>
      );
    case 'list-item':
    case 'li':
      return (
        <li style={style} {...attributes}>
          {children}
        </li>
      );
    case 'numbered-list':
    case 'ol':
      return (
        <ol style={style} {...attributes}>
          {children}
        </ol>
      );
    default:
      return (
        <p style={style} {...attributes}>
          {children}
        </p>
      );
  }
};

interface LeafAttributes {
  bold: boolean;
  code: boolean;
  italic: boolean;
  underline: boolean;
}

interface LeafProps {
  attributes: HTMLAttributes<HTMLDivElement | HTMLUListElement | HTMLLIElement>;
  children: React.ReactNode | undefined;
  leaf: LeafAttributes;
}

const Leaf = ({ attributes, children, leaf }: LeafProps) => {
  if (leaf.bold) {
    children = <strong>{children}</strong>;
  }

  if (leaf.code) {
    children = <code>{children}</code>;
  }

  if (leaf.italic) {
    children = <em>{children}</em>;
  }

  if (leaf.underline) {
    children = <u>{children}</u>;
  }

  return <span {...attributes}>{children}</span>;
};

const BlockButton = ({ format, icon }: { format: string; icon: any }) => {
  const editor = useSlate();
  return (
    <Button
      active={isBlockActive(editor, format, TEXT_ALIGN_TYPES.includes(format) ? 'align' : 'type')}
      onMouseDown={(event: MouseEvent<HTMLButtonElement>) => {
        event.preventDefault();
        toggleBlock(editor, format);
      }}>
      <Icon>{icon}</Icon>
    </Button>
  );
};

const MarkButton = ({ format, icon }: { format: string; icon: any }) => {
  const editor = useSlate();
  return (
    <Button
      active={isMarkActive(editor, format)}
      onMouseDown={(event: MouseEvent<HTMLButtonElement>) => {
        event.preventDefault();
        toggleMark(editor, format);
      }}>
      <Icon>{icon}</Icon>
    </Button>
  );
};

export default RichTextInputHtml;
