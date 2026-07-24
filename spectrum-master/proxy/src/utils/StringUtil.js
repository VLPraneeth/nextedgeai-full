//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
const CONST = {
  BASE64: 'base64',
};

/**
 * Decode the string to base64
 * @param {String} bin base 64 encoded string
 * @returns decoded string
 */
export function base64Decode(bin) {
  return Buffer.from(bin, CONST.BASE64).toString();
}

/**
 * Encode the string to base64 encoded string
 * @param {String} str string to be encoded
 * @returns base 64 encoded string
 */
export function base64Encode(str) {
  return Buffer.from(str).toString(CONST.BASE64);
}
