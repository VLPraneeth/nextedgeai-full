//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export const maxZIndex = 2147483648;

const getDocumentRemSize = () => {
  return parseFloat(getComputedStyle(document.documentElement).fontSize);
};

export const baseRemSize = getDocumentRemSize() || 14;

/**
 * extracts the first number in a string in order to grab a size
 * examples,
 * - '5rem' -> 5
 * - '512px' -> 512
 */
const getFloatFromSizeWithUnits = (sizeStr: string) => {
  const [foundSize] = sizeStr.match(/\d+/) || [];

  const parsedSize = foundSize ? Number.parseFloat(foundSize) : null;

  if (!parsedSize || Number.isNaN(parsedSize)) {
    if (process.env.NODE_ENV !== 'production') {
      throw new Error(
        `Invalid size string provided (${sizeStr}), cannot convert. In production this will return the equivalent of 1rem.`
      );
    }

    // return our base size because we have an invalid string
    return baseRemSize;
  }

  return parsedSize;
};

/**
 * converts a rem size or rem size string to pixels, as a number
 */
export const remToPixels = (remSize: string | number, remInPixels = baseRemSize) => {
  if (typeof remSize === 'string') {
    const parsedSize = getFloatFromSizeWithUnits(remSize);
    return parsedSize * remInPixels;
  }

  return remSize * remInPixels;
};

/**
 * converts a pixel value or pixel string value into a rem string
 */
export const pixelsToRem = (pxSize: string | number, remInPixels = baseRemSize) => {
  const toRemStr = (val: number) => `${val / remInPixels}rem`;

  if (typeof pxSize === 'string') {
    return toRemStr(getFloatFromSizeWithUnits(pxSize));
  }

  return toRemStr(pxSize);
};
