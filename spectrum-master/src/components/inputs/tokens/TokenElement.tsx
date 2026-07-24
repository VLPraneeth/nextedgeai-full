import cx from 'classnames';
import { Element, Node, Transforms } from 'slate-modern';
import { RenderElementProps, useFocused, useSelected, useEditor, ReactEditor } from 'slate-modern-react';

import Token from 'components/Token';
import { Token as TokenType } from 'store/tokens/types';

import { isTokenElement } from './utils';

import './Tokens.less';

const remove = (editor: ReactEditor, element: Element) => {
  const nodePath = ReactEditor.findPath(editor, element);
  Transforms.select(editor, nodePath);
  Transforms.removeNodes(editor, { match: (node) => isTokenElement(node) });
};

export const onRequestRemove = (editor: ReactEditor, element: Element) => {
  // yep…has to be in RAF
  requestAnimationFrame(() => {
    let nodePath = ReactEditor.findPath(editor, element);
    // The below logic is needed for the scenario where we remove the token node that is present between a text and a token node. https://syncari.atlassian.net/browse/SYN-15080
    const placeholderText = '#*#*#';
    // Temporarily insert the placeholder in place of the token node
    Transforms.insertNodes(editor, { text: placeholderText }, { at: nodePath, match: (node) => isTokenElement(node) });
    remove(editor, element);
    setTimeout(() => {
      const nodes = Node.descendants(editor, { reverse: false });

      for (const [node, path] of nodes) {
        if (node.text && (node.text as string).includes(placeholderText)) {
          const startIndex = (node.text as string).indexOf(placeholderText);
          const endIndex = startIndex + placeholderText.length;

          const range = {
            anchor: { path, offset: startIndex },
            focus: { path, offset: endIndex },
          };

          Transforms.select(editor, range);
          // Remove the placeholder by making it empty
          Transforms.insertNodes(editor, { text: '' }, { at: range });
          return;
        }
      }
    });
  });
};

const TokenElement = ({
  attributes,
  children,
  element,
  readOnly = false,
}: RenderElementProps & { readOnly?: boolean }) => {
  const editor = useEditor();

  const selected = useSelected();
  const focused = useFocused();

  const token = element.token as TokenType;

  const onDoubleClick = () => {
    requestAnimationFrame(() => {
      const nodePath = ReactEditor.findPath(editor, element);
      Transforms.insertNodes(editor, { text: token.token }, { at: nodePath, match: (node) => isTokenElement(node) });
      remove(editor, element);
    });
  };

  return (
    <Token
      className={cx({
        active: selected && focused,
      })}
      onDoubleClick={onDoubleClick}
      readOnly={readOnly}
      onRequestRemove={() => onRequestRemove(editor, element)}
      token={token}
      {...attributes}>
      {children}
    </Token>
  );
};

export default TokenElement;
