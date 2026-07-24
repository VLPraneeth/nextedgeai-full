import { Element, Node } from 'slate-modern';

import { Token } from 'store/tokens/types';
import { makeFakeToken } from 'store/tokens/utils';

import { SingleTokenRegex, SyncariToken, TokenRegex } from './constants';

export const makeParagraphBlock = (children: EnhancedNode[] = [makeTextNode('')]) => [
  {
    type: 'paragraph',
    children,
  },
];

export const makeTextNode = (text: string) => ({ text });

export const makeTokenNode = (token: Token) => ({
  type: SyncariToken,
  token,
  children: [{ text: '' }],
});

const isEnhancedNode = (variableToCheck: any): variableToCheck is EnhancedNode => {
  return variableToCheck && (Node.isNode(variableToCheck) || typeof variableToCheck.token !== 'undefined');
};

export type TokenElement = Element & { token: Token };
export type EnhancedNode = Node | TokenElement;
type EnhancedRegExpMatchArray = Pick<RegExpMatchArray, 0 | 1 | 'index'> & { end: number };
type ElementReducerState = { previous: EnhancedRegExpMatchArray | null; result: EnhancedNode[] };

export const isTokenElement = (varToCheck: any): varToCheck is TokenElement => {
  return typeof varToCheck.token !== 'undefined';
};

// SERIALIZER
// converts our Node list into a storable string
export const convertNodeListToString = (nodes: EnhancedNode[]) => {
  return nodes
    .flatMap((n, idx) =>
      n.type === 'paragraph'
        ? // Add a new line only when there are multiple paragraphs
          nodes.length > 1 && idx < nodes.length - 1
          ? [...(n.children as Array<unknown>), { text: '\n' }]
          : n.children
        : n
    )
    .map((node) => (isTokenElement(node) ? node.token.token : (node as EnhancedNode).text))
    .join('');
};

// PARSER
// Used to convert the stored string to a Node (Element) list for SlateJS
export const convertStringToNodeList = (str: string, getToken: (tokenKey: string) => Token = makeFakeToken) => {
  const elements = Array.from(str.matchAll(TokenRegex))
    .map(
      (match) =>
        ({
          index: match.index || 0,
          end: (match.index || 0) + match[0].length,
          0: match[0],
          1: match[1],
        } as EnhancedRegExpMatchArray)
    )
    .reduce(
      ({ previous, result }: ElementReducerState, item, idx, allItems) => {
        // we have the endIdx of our previous token (or it's our first loop and we're staring at 0)
        // so we need to find any text between our previous token and our current token
        // we'll use this range to create a text node
        const startIdx = previous?.end || 0;
        const endIdx = item.index;

        // get any text before our current token and after the previous token
        const leadingText = makeTextNode(str.slice(startIdx, endIdx));

        // convert found token into Token
        const token = makeTokenNode(getToken(item[0]));

        // get any text after our current token IF we're in our last loop
        const trailingText = idx === allItems.length - 1 && makeTextNode(str.slice(item.end));

        return {
          previous: item,
          result: [...result, leadingText, token, trailingText].filter(isEnhancedNode),
        };
      },
      { previous: null, result: [] } as ElementReducerState
    );

  // if we don't have any Nodes, then we probably didn't have any tokens, so make a single text node out of the input
  return elements.result.length ? elements.result : [makeTextNode(str)];
};

/**
 * Returns true if the string value provided is contains at least one valid
 * token
 */
export const isValidTokenValue = (value?: unknown) => {
  if (typeof value === 'string') {
    return Boolean(value.match(TokenRegex));
  }

  return false;
};

/**
 * Returns true if the value provided is a single valid token string
 */
export const isValidSingleTokenValue = (value?: unknown) => {
  if (typeof value === 'string') {
    return Boolean(value.trim().match(SingleTokenRegex));
  }

  return false;
};

// This is reintroducing the check the old version of isValidTokenValue to fix a
// few broken issues
export const valueSupportsTokens = (value?: string | string[] | boolean) => {
  return ['string', 'undefined'].includes(typeof value) || value === null;
};

export const isValidParagraphBlock = (value: EnhancedNode[]) => {
  let isValid = true;

  value.forEach((node) => {
    if (node.children && Array.isArray(node.children)) {
      node.children.forEach((child) => {
        if (child.text && typeof child.text !== 'string') {
          isValid = false;
        }
      });
    }
  });

  return isValid;
};
