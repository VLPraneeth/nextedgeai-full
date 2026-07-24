import cx from 'classnames';
import * as React from 'react';
import { useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { createEditor, Editor, Location, Node, Operation, Path, Range, Transforms, Element } from 'slate-modern';
import { Editable, ReactEditor, RenderElementProps, Slate, withReact } from 'slate-modern-react';
import { useDebouncedCallback } from 'use-debounce';

import Spinner from 'components/Spinner';
import usePreviousValue from 'hooks/usePreviousValue';
import { EditableProps, RenderLeafProps } from 'slate-modern-react/dist/components/editable';
import { useTokensError, useTokensForSelectedNode } from 'store/tokens/hooks';
import { Token, TokenError } from 'store/tokens/types';
import AppConstants from 'utils/AppConstants';

import { LeftValue } from '../types';
import { SyncariToken } from './constants';
import { TokenRegex } from './constants';
import { TokenDropdownList } from './TokenDropdownList';
import TokenElement, { onRequestRemove } from './TokenElement';
import TokenSelector from './TokenSelector';
import {
  convertNodeListToString,
  convertStringToNodeList,
  EnhancedNode,
  isTokenElement,
  isValidParagraphBlock,
  makeParagraphBlock,
  makeTextNode,
  makeTokenNode,
} from './utils';

import './Tokens.less';

const withTokens = (editor: Editor & ReactEditor) => {
  const { isInline, isVoid } = editor;

  editor.isInline = (element) => {
    return element.type === SyncariToken ? true : isInline(element);
  };

  editor.isVoid = (element) => {
    return element.type === SyncariToken ? true : isVoid(element);
  };

  return editor;
};

const serialize = (nodes: Node[]) => {
  return nodes.map((n) => Node.string(n)).join('\n');
};

const containsToken = (value: Node[]) => {
  const matches = serialize(value).match(TokenRegex);

  return !!matches;
};

type EditorKeyHandler = (editor: Editor & ReactEditor, evt: React.KeyboardEvent<HTMLDivElement>) => void;

function getNodeBasedOnCursor(editor: Editor) {
  if (!editor.selection || !Range.isCollapsed(editor.selection)) {
    return null;
  }

  const { anchor } = editor.selection;
  const anchorPath = anchor.path;
  const anchorOffset = anchor.offset;

  if (anchorOffset === 0) {
    if (anchorPath.length > 2) {
      // The cursor is at the start of the node. Basically node is focused.
      const currentNode = Node.get(editor, anchorPath.slice(0, 2));
      return currentNode;
    } else if (anchorPath[anchorPath.length - 1] > 0) {
      // The cursor is after the node
      const previousPath = Path.previous(anchorPath);
      const previousNode = Node.get(editor, previousPath);
      return previousNode;
    } else {
      return null;
    }
  } else {
    // The cursor is within a text node
    const leafNode = Node.leaf(editor, anchorPath);
    return leafNode;
  }
}

const handleDelete: EditorKeyHandler = (editor, evt) => {
  const { selection } = editor;

  // if our selection is a single point, let's check to see if it's
  // a token. If it is, then we'll stop the event propogation and remove
  // our token Element
  if (selection && Range.isCollapsed(selection)) {
    try {
      const node = getNodeBasedOnCursor(editor);

      if (node && isTokenElement(node)) {
        evt.preventDefault();
        onRequestRemove(editor, node as Element);
      }
    } catch (err) {
      /* noop */
    }
  }
};

const handleEnter: EditorKeyHandler = (_editor, evt) => {
  evt.preventDefault();
};

const makeElementRenderer = (readOnly: boolean) => ({ attributes, element, children }: RenderElementProps) => {
  switch (element.type) {
    case SyncariToken:
      return (
        <TokenElement readOnly={readOnly} attributes={attributes} element={element}>
          {children}
        </TokenElement>
      );
    default:
      return (
        <span className="tokens-textarea-unstyled" {...attributes}>
          {children}
        </span>
      );
  }
};

const renderLeaf = ({ attributes, children }: RenderLeafProps) => <span {...attributes}>{children}</span>;

export interface TokenTextAreaProps {
  /* this is called onBlur so we don't incur performance hits */
  onChange?: (fakeEvt: React.ChangeEvent<HTMLInputElement>) => void;
  value?: string;
  rows?: number;
  id?: string;
  labelId?: string;
  'aria-labelledby'?: string;
  name?: string;
  displayMode?: string;
  suppliedTokens?: Record<string, Token[]>;
  showTokenSelector?: boolean;
  tokenSelectorLabel?: string;
  onBlur?: () => void;
  onFocus?: () => void;
  disabled?: boolean;
  leftValue?: LeftValue;
}

const TokenTextArea = React.forwardRef<TokenTextAreaRef, TokenTextAreaProps>(
  (
    {
      id,
      labelId: providedLabelId,
      name,
      onChange,
      value: providedValue,
      displayMode,
      onBlur,
      onFocus,
      suppliedTokens,
      showTokenSelector = false,
      tokenSelectorLabel,
      rows = 1,
      disabled,
      leftValue,
      ...rest
    },
    ref
  ) => {
    const value = providedValue && typeof providedValue === 'string' ? providedValue : '';
    const labelId = rest['aria-labelledby'] || providedLabelId;

    const { tokens: serverTokens, getToken, isLoading, isSuccess } = useTokensForSelectedNode({
      skip: Boolean(suppliedTokens),
      suppliedTokens,
    });

    const tokensError = useTokensError(serverTokens);

    const [editorState, setEditorState] = useState<EnhancedNode[]>(() => makeParagraphBlock([makeTextNode(value)]));
    const readOnly = useMemo(() => displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY, [displayMode]);
    const tokens = suppliedTokens ? suppliedTokens : serverTokens;

    // while hydrating state, we want to ensure we use the original value
    const [originalValue, setOriginalValue] = useState(value);
    const [valueHydrated, setValueHydrated] = useState(() => !(value && value !== ''));
    const previousValue = usePreviousValue(value);

    useEffect(() => {
      // TODO: Make it behave like html input value attribute on edit mode. Restricting it to readonly for now.
      // TODO: Add support for defaultValue if the
      // user of this component does need to update the value
      if (value !== previousValue) {
        if (readOnly) {
          setOriginalValue(value);
          setEditorState(makeParagraphBlock(convertStringToNodeList(value, getToken)));
        }
      }
    }, [value, previousValue, getToken, readOnly]);

    // need a fake ChangeEvent for InputContainer consumers
    const [handleChangeDebounced, , callPending] = useDebouncedCallback(
      (value: string) => {
        const fakeEvent = {
          target: {
            name,
            id,
            value,
          },
        } as React.ChangeEvent<HTMLInputElement>;

        onChange?.(fakeEvent);
      },
      50,
      { maxWait: 1000, leading: true }
    );

    useEffect(() => {
      // we're done loading and we still have a value to hydrate
      if ((isSuccess || suppliedTokens) && !valueHydrated) {
        setEditorState(makeParagraphBlock(convertStringToNodeList(originalValue, getToken)));
        setValueHydrated(true);
      }
    }, [getToken, isSuccess, valueHydrated, originalValue, suppliedTokens]);

    useEffect(() => {
      handleChangeDebounced(convertNodeListToString(editorState));
    }, [editorState, handleChangeDebounced]);

    return (
      <ControlledTokenTextArea
        ref={ref}
        id={id}
        labelId={labelId}
        onBlur={() => {
          callPending();
          onBlur?.();
        }}
        onFocus={onFocus}
        onChange={setEditorState}
        readOnly={disabled || readOnly}
        rows={rows}
        tokensLoading={isLoading}
        tokensError={tokensError}
        getToken={getToken}
        tokens={tokens}
        value={editorState}
        showTokenSelector={showTokenSelector}
        tokenSelectorLabel={tokenSelectorLabel}
        leftValue={leftValue}
      />
    );
  }
);

export type InsertTokenHandler = (
  token: Token,
  insertOptions?: Parameters<typeof Transforms.insertNodes>[2],
  moveOptions?: Parameters<typeof Transforms.move>[1],
  requestFocus?: boolean
) => void;

export type TokenTextAreaRef = {
  blur: () => void;
  focus: () => void;
  insertToken: InsertTokenHandler;
};

type ControlledTokenTextAreaProps = Omit<TokenTextAreaProps, 'displayMode' | 'onChange' | 'value'> & {
  onBlur?: EditableProps['onBlur'];
  onChange: (nodes: EnhancedNode[]) => void;
  tokens: Record<string, Token[]>;
  getToken: (tokenKey: string) => Token;
  showTokenSelector?: boolean;
  tokenSelectorLabel?: string;
  tokensLoading?: boolean;
  tokensError?: TokenError;
  readOnly?: boolean;
  value?: EnhancedNode[];
  leftValue?: LeftValue;
};

export const ControlledTokenTextArea = React.forwardRef<TokenTextAreaRef, ControlledTokenTextAreaProps>(
  (
    {
      id,
      labelId,
      onChange,
      value: providedValue,
      showTokenSelector,
      tokenSelectorLabel,
      tokensLoading = false,
      tokensError,
      getToken,
      tokens,
      readOnly = false,
      onBlur,
      onFocus,
      rows = 1,
      leftValue,
    },
    ref
  ) => {
    // Track selection state for our insertNode fn
    const selection = useRef<Location | undefined>(undefined);

    // have to do a nice ref dance to stabilize editor properly :/
    const editorRef = useRef<ReactEditor>();
    if (!editorRef.current) {
      const _editor = withTokens(withReact(createEditor()));
      const { apply: originalEditorApply } = _editor;

      // we want to catch any selection updates, so we'll patch
      // the apply method
      _editor.apply = (operation: Operation) => {
        // call original method
        originalEditorApply(operation);

        const { type: opType, newProperties } = operation;

        if (opType === 'set_selection' && Location.isLocation(newProperties)) {
          selection.current = newProperties;
        }
      };

      editorRef.current = _editor;
    }

    const editor = editorRef.current;

    const initialValue =
      providedValue && isValidParagraphBlock(providedValue) ? providedValue : makeParagraphBlock([makeTextNode('')]);

    const previousLeftValue = usePreviousValue(leftValue);

    // Track the editor state in order to prevent overriding user input
    // if the component rerenders and the provided value doesn't match the
    // providedValue.
    const [value, setValue] = useState(initialValue);
    const [isEdited, setIsEdited] = useState(false);

    // Only use the default value from the parent if tokens are not loading
    // and if the edit flag has been cleared onEditorChange.
    useEffect(() => {
      if (!isEdited) {
        setValue(initialValue);
      }
    }, [initialValue, isEdited]);

    // When the LHS datatype changes, clear the RHS value.
    useEffect(() => {
      if (leftValue?.datatype && previousLeftValue?.datatype && leftValue.datatype !== previousLeftValue.datatype) {
        setValue(makeParagraphBlock([makeTextNode('')]));
      }
    }, [leftValue?.datatype, previousLeftValue?.datatype]);

    const onEditorChange = (node: Node[]) => {
      // While the tokens are loading, keep track of if the value has been edited.
      // This needs to be tracked, because if the tokens resolve as the user is
      // typing, then the state update will clear out the input.
      if (!isEdited && tokensLoading) {
        setIsEdited(true);
      }

      // Clear the flag if tokens are no longer loading.
      if (isEdited && !tokensLoading) {
        setIsEdited(false);
      }

      setValue(node);
      onChange(node);

      const { selection } = editor;

      if (selection && Range.isCollapsed(selection)) {
        const [start] = Range.edges(selection);
        const wordBefore = Editor.before(editor, start, { unit: 'word' });
        const before = wordBefore && Editor.before(editor, wordBefore);
        const beforeRange = before && Editor.range(editor, before, start);
        const beforeText = beforeRange && Editor.string(editor, beforeRange);
        const beforeMatch = beforeText && beforeText?.trim()?.match(/^{{(\w*)$/);
        const after = Editor.after(editor, start);
        const afterRange = Editor.range(editor, start, after);
        const afterText = Editor.string(editor, afterRange);
        const afterMatch = afterText.match(/^(\s|$)/);

        if (beforeMatch && afterMatch) {
          setTarget(beforeRange);
          setSearch(beforeMatch[1]);
          setIndex(0);
          return;
        }
      }

      setTarget(null);
    };

    const disableEditing = readOnly || (tokensLoading && containsToken(value));

    const isTextArea = rows > 1;

    const keyHandlers: Record<string, EditorKeyHandler> = useMemo(() => {
      const baseHandlers = {
        Backspace: handleDelete,
        Delete: handleDelete,
      };

      return isTextArea ? baseHandlers : { Enter: handleEnter, ...baseHandlers };
    }, [isTextArea]);

    // Trim text that is pasted to avoid accidentally copying whitespace
    const trimPastedText = useCallback(
      (event: React.ClipboardEvent<HTMLDivElement>) => {
        const textString = (event.clipboardData || (window as any).clipboardData)?.getData('text')?.trim();
        if (textString) {
          editor.insertText(textString);
        }
        event.preventDefault();
        event.stopPropagation();
      },
      [editor]
    );

    // Store current editor position so we don't lose the value
    // in the following callbacks during a rerender.
    const currentEditorSelection = useMemo(() => {
      return editor.selection;
    }, [editor.selection]);

    const blur = useCallback(() => ReactEditor.blur(editor), [editor]);
    const focus = useCallback(() => ReactEditor.focus(editor), [editor]);
    const insertToken = useCallback(
      (
        token: Token,
        insertOptions?: Parameters<typeof Transforms.insertNodes>[2],
        moveOptions?: Parameters<typeof Transforms.move>[1],
        requestFocus = true
      ) => {
        const tokenNode = makeTokenNode(token);

        try {
          Transforms.insertNodes(editor, tokenNode, {
            ...(Location.isLocation(currentEditorSelection) && { at: currentEditorSelection }),
            ...insertOptions,
          });
        } catch (err) {
          // There are circumstances were Slate doesn't give us an updated selection,
          // and the insertNode call will fail with the invalid selection.
          // In these cases, we will try to re-insert the token without the selection range
          Transforms.insertNodes(editor, tokenNode);
        }
        Transforms.move(editor, moveOptions);

        if (requestFocus) {
          ReactEditor.focus(editor);
        }
      },
      [editor, currentEditorSelection]
    );

    useImperativeHandle(ref, () => ({
      blur,
      focus,
      insertToken,
    }));

    const renderElement = useCallback((props: RenderElementProps) => makeElementRenderer(readOnly)(props), [readOnly]);

    const [target, setTarget] = useState<Range | undefined | null>();
    const [search, setSearch] = useState('');
    const [index, setIndex] = useState(0);
    const endRef = useRef<HTMLDivElement | null>(null);

    const filteredTokens = search
      ? tokens.suppliedTokens?.filter((c) => c.shortLabel.toLowerCase().startsWith(search.toLowerCase())).slice(0, 10)
      : tokens.suppliedTokens;

    useEffect(() => {
      if (target) {
        const el = endRef.current;
        let domRange;
        try {
          domRange = ReactEditor.toDOMRange(editor, target);
        } catch (e) {
          console.error(e);
          return;
        }
        const rect = domRange.getBoundingClientRect();
        if (el?.style) {
          el.style.top = `${rect.top + window.pageYOffset + 24}px`;
          el.style.left = `${rect.left + window.pageXOffset}px`;
        }
      }
    }, [editor, target]);

    const handleKeyDown = useCallback(
      (evt: React.KeyboardEvent<HTMLDivElement>) => {
        keyHandlers[evt.key]?.(editor, evt);
        if (target) {
          switch (evt.key) {
            case 'ArrowDown':
              evt.preventDefault();
              const prevIndex = index >= filteredTokens.length - 1 ? 0 : index + 1;
              setIndex(prevIndex);
              break;
            case 'ArrowUp':
              evt.preventDefault();
              const nextIndex = index <= 0 ? filteredTokens.length - 1 : index - 1;
              setIndex(nextIndex);
              break;
            case 'Tab':
            case 'Enter':
              evt.preventDefault();
              // Delete the user typed token
              editor.deleteBackward('word');
              insertToken({
                datatype: filteredTokens[index].datatype,
                group: '',
                label: filteredTokens[index].shortLabel,
                shortLabel: filteredTokens[index].shortLabel,
                token: filteredTokens[index].value,
                value: filteredTokens[index].value,
              });
              setTarget(null);
              break;
            case 'Escape':
              evt.preventDefault();
              setTarget(null);
              break;
          }
        }
      },
      [filteredTokens, editor, index, insertToken, keyHandlers, target]
    );

    return (
      <div className={cx('tokens-input-container', { 'synri-tokens-readonly': readOnly })}>
        {showTokenSelector && !readOnly && (
          <TokenSelector
            tokens={tokens}
            label={tokenSelectorLabel}
            tokensLoading={tokensLoading}
            tokensError={tokensError}
            onTokenSelect={(token) => {
              Transforms.insertNodes(editor, makeTokenNode(getToken(token)));
              Transforms.move(editor);
              ReactEditor.focus(editor);
            }}
          />
        )}
        <div
          className={cx(
            'tokens-textarea-container',
            isTextArea && 'synri-tokens-textarea',
            disableEditing && 'tokens-textarea-container--disabled'
          )}
          onClick={focus}>
          <Slate editor={editor} onChange={onEditorChange} value={value}>
            <Editable
              onPaste={trimPastedText}
              id={id}
              data-testid={id}
              aria-labelledby={labelId}
              onBlur={onBlur}
              onFocus={onFocus}
              onKeyDown={handleKeyDown}
              renderElement={renderElement}
              renderLeaf={renderLeaf}
              readOnly={disableEditing}
            />
            {target && <TokenDropdownList ref={endRef} tokens={filteredTokens} selectedTokenIndex={index} />}
          </Slate>
          {tokensLoading && containsToken(value) && <Spinner className="tokens-textarea-container__spinner" />}
        </div>
      </div>
    );
  }
);

export default TokenTextArea;
