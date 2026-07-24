//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { capitalize, filter, kebabCase, map, trim } from 'lodash';

// Display name regex match used when converting a user selected display name to api name
export const standardApiNameRegex = /[^a-zA-Z0-9\s_-]/g;

// Regex match used when converting an entity or field display name to api name
export const schemaApiNameRegex = /[^a-zA-Z0-9\s_+-]/g;

/**
 * Copy a string to the clipboard
 * @param {String} textToCopy - string to be copied to the clipboard
 */
export async function copyStringToClipboard(textToCopy: string) {
  try {
    await navigator.clipboard.writeText(textToCopy);
  } catch {
    DEP_copyStringToClipboard(textToCopy);
  }
}

/**
 * Copy a string to the clipboard
 * @param {String} str - string to be copied to the clipboard
 * @deprecated depends on deprecated api `document.execCommand()`
 */
function DEP_copyStringToClipboard(str: string) {
  var el = document.createElement('textarea');
  el.value = str;
  el.setAttribute('readonly', '');
  el.style.position = 'absolute';
  el.style.left = '-9999px';
  document.body.appendChild(el);
  el.select();
  document.execCommand('copy');
  document.body.removeChild(el);
}

export interface FilterItem {
  displayName?: string | null;
  label?: string | null;
  apiName?: string | null;
  title?: string | null;
  name?: string | null;
  tooltipMessage?: string | null;
}

export function filterItems<T extends FilterItem>(items: T[], text: string) {
  return filter(items, (item) => {
    if (!item) {
      return false;
    }
    const name = (item.displayName || item.label || item.apiName || item.title || item.name || '').toLowerCase();
    let match = name.indexOf(text.toLowerCase()) !== -1;
    // Do a secondary match by tooltip message.
    // Note: This is an internal magic match. Need to make this configurable.
    if (!match && item.tooltipMessage) {
      match = item.tooltipMessage?.toLowerCase()?.indexOf(text.toLowerCase()) !== -1;
    }
    return match;
  });
}

export function humanize(str: string) {
  const kStr = kebabCase(str);
  let hu = str;
  if (kStr) {
    const words = kStr.split('-');
    const splitWords = map(words, (word) => {
      return `${word.toLowerCase()}`;
    });
    if (splitWords?.length > 0) {
      splitWords[0] = capitalize(splitWords[0]);
    }
    hu = splitWords.join(' ');
  }
  return trim(hu);
}

export function ellipsis(str: string, n: number) {
  return str.length > n ? str.substr(0, n - 1) + '…' : str;
}

export function replaceToken(str: string, tokens: Record<string, any>) {
  return Object.entries(tokens).reduce(
    (newStr, [key, value]) => newStr.replace(`{${key}}`, value?.toString() || ''),
    str
  );
}

export const replaceAll = (originalStr: string, target: string, replacement: string) =>
  originalStr.split(target).join(replacement);

export function hasToken(str: string) {
  return str?.match(/\{.*\}/);
}

export function getTokens(str: string) {
  let tokens;
  if (hasToken(str)) {
    // eslint-disable-next-line no-useless-escape
    const matchedTokens = str.match(/\{[^\}]+\}/gi);
    if (matchedTokens) {
      tokens = map(matchedTokens, (token) => trim(token, '{}'));
    }
  }
  return tokens;
}

export function calculateTextWidth(fontFamily: string, text = 'text', fontSize = 12, fontWeight = 600) {
  const testSpan = document.getElementById('CalculateTextWidth');
  const content = document.createTextNode(text);

  if (testSpan) {
    testSpan.style.fontSize = fontSize.toString();
    testSpan.style.fontFamily = fontFamily;
    testSpan.style.fontWeight = fontWeight.toString();
    testSpan.appendChild(content);
  }

  return {
    height: `${(testSpan?.clientHeight || 0) + 1}px`,
    width: `${(testSpan?.clientWidth || 0) + 1}px`,
  };
}

// replace a slice of a string with a new string
export const replaceSubstring = (originalString: string, start: number, end: number, replacementString: string) => {
  return [originalString.slice(0, start), replacementString, originalString.slice(end)].join('');
};

// won't crash on invalid inputs
export const safeDecodeBase64 = (b64: string = '') => {
  try {
    return atob(b64);
  } catch (err) {
    return '';
  }
};

export const safeJoin = (delimiter: string) => (...components: (string | null | undefined)[]) =>
  components.filter(Boolean).join(delimiter);

export const safeJoinWithSpace = safeJoin(' ');
export const safeJoinWithComma = safeJoin(', ');

export const truncateMiddle = (text = '', maxLength: number) => {
  if (!text || text.length < maxLength) {
    return text;
  }

  const maxLengthWithEllipse = maxLength - 1;
  const substringSize = Math.floor(maxLengthWithEllipse / 2);

  if (substringSize < 1) {
    throw new Error(`maxLength of ${maxLength} is too short to truncate text.`);
  }

  const startString = text.slice(0, substringSize);
  const endString = text.slice(-substringSize);

  return `${startString}…${endString}`;
};

const incrementNumberOnString = (originalString: string) => {
  // Extract the last digits from the end of the string
  const lastDigitsMatch = originalString.match(/\d+$/);

  if (!lastDigitsMatch) {
    // If no digits found, append 2 to the end
    return `${originalString} 2`;
  }

  // Extract the matched digits
  const lastDigits = lastDigitsMatch[0];

  // Convert the extracted digits to a number
  const number = parseInt(lastDigits, 10);

  // Increment the number by one
  const updatedNumber = number + 1;

  // Append the updated number to the original string
  const updatedString = originalString.replace(/\d+$/, updatedNumber.toString());

  return updatedString;
};

// This is an enhancement to generateUniqueName. It takes an array of existing
// names to compare against and augments it to include any newly created names.
// It also does not stop at 9 for unique names.
export const generateUniqueNamesCallback = (currentNames: string[]) => {
  return (newName: string) => {
    let uniqueName = newName;

    while (currentNames.includes(uniqueName)) {
      uniqueName = incrementNumberOnString(uniqueName);
    }

    currentNames.push(uniqueName);
    return uniqueName;
  };
};

export const MAX_UNIQUE_NAME_SUFFIX = 9;
/**
 * Generate a new name by appending a number next to the name. It will propose a name by appending a number
 * starting from 2 to 9 (MAX_UNIQUE_NAME_SUFFIX). Note that it will check with the name first before
 * appending incrementing number.
 *
 * @param name Original name
 * @param hasDuplicate Callback function to check if the new proposed name still have duplicate.
 * @returns new name
 */
export const generateUniqueName = (name: string, hasDuplicate: (newName: string) => boolean) => {
  let newName = name.trim();
  if (!newName) {
    return '';
  }

  if (hasDuplicate(newName)) {
    for (let count = 1; count <= MAX_UNIQUE_NAME_SUFFIX - 1 && hasDuplicate(newName); count++) {
      // If the last character in the string is a number then replace that
      // number with the incremented number
      const lastCharacter = parseInt(name.slice(-1), 10);
      const lastCharacterIsNumber = !isNaN(lastCharacter);
      if (lastCharacterIsNumber) {
        newName = `${name.slice(0, -1)}${lastCharacter + 1}`;
      } else {
        newName = `${name} ${count + 1}`;
      }
    }
  }
  return newName;
};

// capitalize each component of a string
export const toTitleCase = (str: string) => {
  try {
    return str
      .split(' ')
      .map((s) => capitalize(s))
      .join(' ');
  } catch (err) {
    return str;
  }
};

export const match = (pattern: string, text: string) =>
  // escape regex special characters of the pattern before passing to RegExp.
  // e.g. ?something -> \?something". $& is the matching string
  new RegExp(pattern.replace(/[#-.]|[[-^]|[?|{}]/g, '\\$&'), 'i').test(text);

/**
 * Converts a string into a sanitized api name. This is a copy of the
 * createApiName function in the backend repo under TextUtil.java.
 */
export const createApiName = (name: string, pattern: RegExp = standardApiNameRegex) => {
  return (
    name
      // Remove all characters except white space, letters, numbers, underscores, and dashes
      .replace(pattern, '')
      .trim()
      .replace(/\s+/g, '_')
      .toLowerCase()
  );
};

export const entityIdIsValid = (entityId?: string) => {
  if (typeof entityId !== 'string') {
    return false;
  }

  return entityId.length === 24;
};

export const routeToMatch = (routeString: string) => {
  return routeString.replace(/{/g, ':').replace(/}/g, '');
};

// Used for decoding strings with multi-byte characters like č
export function base64DecodeUtf8(str: string) {
  const binaryString = atob(str);

  const charCodeArray = Array.from(binaryString, (c) => c.charCodeAt(0));
  const byteArray = new Uint8Array(charCodeArray);
  const decoder = new TextDecoder('utf-8');
  const decodedString = decoder.decode(byteArray);

  return decodedString;
}

export const safeDecodeURIComponent = (str: string) => {
  try {
    return decodeURIComponent(str);
  } catch (e) {
    return str;
  }
};
