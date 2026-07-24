import { colors } from 'utils/LessConstants';

const rgbFromString = (color: string) => {
  // If RGB --> store the red, green, blue values in separate variables
  const colorMatch = color.match(/^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*(\d+(?:\.\d+)?))?\)$/);

  if (!colorMatch) {
    const hex = +(
      // turn it into a number by taking the
      (
        '0x' + // hexadecimal prefix and the
        color
          .slice(1) // numerical portion,
          .replace(
            // and
            color.length < 4 ? /./g : '', // if the #xxx form is used // replace each character
            '$&$&' // with itself twice.
          )
      )
    );

    return {
      r: hex >> 16,
      b: (hex >> 8) & 255,
      g: hex & 255,
    };
  }

  return {
    r: Number(colorMatch[1]),
    g: Number(colorMatch[2]),
    b: Number(colorMatch[3]),
  };
};

export const isColorLightOrDark = (color: string, threshold = 127.5) => {
  const { r, g, b } = rgbFromString(color);

  // HSP equation from http://alienryderflex.com/hsp.html
  const hsp = Math.sqrt(0.299 * (r * r) + 0.587 * (g * g) + 0.114 * (b * b));

  // Using the HSP value, determine whether the color is light or dark
  if (hsp > threshold) {
    return 'light';
  }

  return 'dark';
};

export const getTextColorForBackgroundColor = (bgColor: string, threshold = 160) =>
  isColorLightOrDark(bgColor, threshold) === 'dark' ? colors.white : colors.gray900;

export function hexToHSL(hex: string) {
  // Convert hex to RGB first
  let r = 0,
    g = 0,
    b = 0;
  if (hex.length === 4) {
    r = +('0x' + hex[1] + hex[1]);
    g = +('0x' + hex[2] + hex[2]);
    b = +('0x' + hex[3] + hex[3]);
  } else if (hex.length === 7) {
    r = +('0x' + hex[1] + hex[2]);
    g = +('0x' + hex[3] + hex[4]);
    b = +('0x' + hex[5] + hex[6]);
  }
  // Then to HSL
  r /= 255;
  g /= 255;
  b /= 255;
  let cmin = Math.min(r, g, b),
    cmax = Math.max(r, g, b),
    delta = cmax - cmin,
    h = 0,
    s = 0,
    l = 0;

  if (delta === 0) {
    h = 0;
  } else if (cmax === r) {
    h = ((g - b) / delta) % 6;
  } else if (cmax === g) {
    h = (b - r) / delta + 2;
  } else {
    h = (r - g) / delta + 4;
  }

  h = Math.round(h * 60);

  if (h < 0) {
    h += 360;
  }

  l = (cmax + cmin) / 2;
  s = delta === 0 ? 0 : delta / (1 - Math.abs(2 * l - 1));
  s = +(s * 100).toFixed(1);
  l = +(l * 100).toFixed(1);

  return { h, s, l };
}
