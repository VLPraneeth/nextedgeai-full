import { Token } from 'store/tokens/types';
import { makeFakeToken } from 'store/tokens/utils';

import {
  convertNodeListToString,
  convertStringToNodeList,
  isValidSingleTokenValue,
  isValidTokenValue,
  makeTextNode,
  makeTokenNode,
} from '../utils';

test('convertNodeListToString gives us a string with tokens encoded', () => {
  const fakeFieldToken = makeFakeToken('{{Test/Entity/Field}}');
  const fakeSyncariDateToken = makeFakeToken('{{Syncari/Misc/SystemDate}}');

  const nodeList = [makeTokenNode(fakeFieldToken), makeTextNode('-'), makeTokenNode(fakeSyncariDateToken)];

  expect(convertNodeListToString(nodeList)).toBe('{{Test/Entity/Field}}-{{Syncari/Misc/SystemDate}}');
});

const FIELD = '{{Field}}';
const SYSTEM_DATE = '{{SystemDate}}';
const SYNCARI_SYSTEM_DATE = '{{Syncari.System Date}}';

const fakeTokensMap: Record<string, Token> = {
  [FIELD]: makeFakeToken(FIELD, 'Field'),
  [SYSTEM_DATE]: makeFakeToken(SYSTEM_DATE, 'System Date'),
  [SYNCARI_SYSTEM_DATE]: makeFakeToken(SYNCARI_SYSTEM_DATE, 'Syncari System Date'),
};

// fake getToken fn so we can attach some shortLabels
const _getToken = (tokenKey: string) => fakeTokensMap[tokenKey];

test('convertStringToNodeList parses existing tokenized string properly', () => {
  const inputString = '{{Field}}-{{SystemDate}}-{{Syncari.System Date}}';
  const nodeList = convertStringToNodeList(inputString, _getToken);

  // our plaintext representation should be the shortLabels
  expect(nodeList).toEqual([
    makeTextNode(''),
    makeTokenNode(fakeTokensMap[FIELD]),
    makeTextNode('-'),
    makeTokenNode(fakeTokensMap[SYSTEM_DATE]),
    makeTextNode('-'),
    makeTokenNode(fakeTokensMap[SYNCARI_SYSTEM_DATE]),
    makeTextNode(''),
  ]);

  // converting back without changes should give us the oriignal string
  expect(convertNodeListToString(nodeList)).toBe(inputString);
});

test('convertStringToNodeList parses string with tokens and leading non-tokens', () => {
  const inputString = 'leading-text-{{Field}}';
  const nodeList = convertStringToNodeList(inputString, _getToken);

  expect(nodeList).toEqual([makeTextNode('leading-text-'), makeTokenNode(fakeTokensMap[FIELD]), makeTextNode('')]);

  // converting back without changes should give us the oriignal string
  expect(convertNodeListToString(nodeList)).toBe(inputString);
});

test('convertStringToNodeList parses string with tokens and mixed non-tokens', () => {
  const inputString = 'leading-{{Field}}-mixed non tokens-{{SystemDate}}-trailing';
  const nodeList = convertStringToNodeList(inputString, _getToken);

  expect(nodeList).toEqual([
    makeTextNode('leading-'),
    makeTokenNode(fakeTokensMap[FIELD]),
    makeTextNode('-mixed non tokens-'),
    makeTokenNode(fakeTokensMap[SYSTEM_DATE]),
    makeTextNode('-trailing'),
  ]);

  // converting back without changes should give us the oriignal string
  expect(convertNodeListToString(nodeList)).toBe(inputString);
});

test('convertStringToNodeList parses string with tokens and trailing non-tokens', () => {
  const inputString = '{{Field}}-I have no tokens yet';
  const nodeList = convertStringToNodeList(inputString, _getToken);

  expect(nodeList).toEqual([
    makeTextNode(''),
    makeTokenNode(fakeTokensMap[FIELD]),
    makeTextNode('-I have no tokens yet'),
  ]);

  // converting back without changes should give us the oriignal string
  expect(convertNodeListToString(nodeList)).toBe(inputString);
});

test('convertStringToNodeList parses string without tokens', () => {
  const inputString = 'I have no tokens yet';
  const nodeList = convertStringToNodeList(inputString, _getToken);

  // our plaintext representation should be the shortLabels
  expect(nodeList).toEqual([makeTextNode('I have no tokens yet')]);

  // converting back without changes should give us the oriignal string
  expect(convertNodeListToString(nodeList)).toBe(inputString);
});

test('isValidTokenValue returns true when any token string exists', () => {
  const singleToken = '{{single.token}}';
  expect(isValidTokenValue(singleToken)).toBe(true);

  const tokenWithExtraString = '{{single.token}} extra string';
  expect(isValidTokenValue(tokenWithExtraString)).toBe(true);

  const multipleTokens = '{{single.token}} {{another.token}}';
  expect(isValidTokenValue(multipleTokens)).toBe(true);

  const invalidToken = '{single.token}';
  expect(isValidTokenValue(invalidToken)).toBe(false);
});

test('isValidSingleTokenValue returns true when any token string exists', () => {
  const singleToken = '{{single.token}}';
  expect(isValidSingleTokenValue(singleToken)).toBe(true);

  const invalidToken = '{{single.token}} extra string';
  expect(isValidSingleTokenValue(invalidToken)).toBe(false);

  const missingBracket = '{single.token}';
  expect(isValidSingleTokenValue(missingBracket)).toBe(false);
});
