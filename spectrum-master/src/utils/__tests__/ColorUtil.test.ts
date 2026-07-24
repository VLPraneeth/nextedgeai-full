import { hexToHSL } from '../ColorUtil';

describe('hexToHSL', () => {
  test('should convert hex color to HSL', () => {
    expect(hexToHSL('#000000')).toEqual({ h: 0, s: 0, l: 0 });
    expect(hexToHSL('#ff0000')).toEqual({ h: 0, s: 100, l: 50 });
    expect(hexToHSL('#00ff00')).toEqual({ h: 120, s: 100, l: 50 });
    expect(hexToHSL('#0000ff')).toEqual({ h: 240, s: 100, l: 50 });
    expect(hexToHSL('#ffffff')).toEqual({ h: 0, s: 0, l: 100 });
  });

  test('should convert short hex color to HSL', () => {
    expect(hexToHSL('#000')).toEqual({ h: 0, s: 0, l: 0 });
    expect(hexToHSL('#f00')).toEqual({ h: 0, s: 100, l: 50 });
    expect(hexToHSL('#0f0')).toEqual({ h: 120, s: 100, l: 50 });
    expect(hexToHSL('#00f')).toEqual({ h: 240, s: 100, l: 50 });
    expect(hexToHSL('#fff')).toEqual({ h: 0, s: 0, l: 100 });
  });
});
