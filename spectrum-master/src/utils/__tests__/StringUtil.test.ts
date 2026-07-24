import {
  MAX_UNIQUE_NAME_SUFFIX,
  base64DecodeUtf8,
  createApiName,
  generateUniqueName,
  generateUniqueNamesCallback,
  match,
  replaceAll,
  safeDecodeURIComponent,
  truncateMiddle,
} from '../StringUtil';

// TextDecoder is a WebAPI, mocking it here
(global as any).TextDecoder = class {
  decode(buf: BufferSource) {
    return Buffer.from(buf as any).toString('utf-8');
  }
};

test('replaceAll', () => {
  expect(replaceAll('hello-world-test-test-test', '-', ' ')).toBe('hello world test test test');
  expect(replaceAll('hello__world_test_test_test', '_', ':')).toBe('hello::world:test:test:test');
  expect(replaceAll('hello world test test test', ' ', '-')).toBe('hello-world-test-test-test');
});

describe('truncateMiddle', () => {
  test('should truncate text in the middle', () => {
    const text = '1d2GRaVoGShy0BCNPGJJzDKYT7CWa3JCu5Ta0JxWypgQ-sheet1-2';
    const result = truncateMiddle(text, 10);

    expect(result.length).toBeLessThanOrEqual(10);
    expect(result).toEqual('1d2G…t1-2');
  });

  test('should not truncate text that is shorter than maxLength', () => {
    const text = '1d2GRaVoG';
    const result = truncateMiddle(text, 10);

    expect(result).toEqual(text);
  });
});

describe('string match', () => {
  // prettier-ignore
  test.each([
    ['Sub',  'subscription',     true],
    ['sub',  'subscription',     true],
    ['A12',  'subscriptionA12B', true],
    ['some', 'subscription',     false],
    ['/',    'subscription',     false],
    ['\\',   'subscription',     false],
    ['$',    'subscription',     false],
    ['{',    'subscription',     false],
  ])(`match with pattern %s and text %s returns %s`, (pattern, text, expectedStatus) => {
      expect(match(pattern, text)).toBe(expectedStatus);
    })
});

describe('generate unique name', () => {
  it('should return the same name', () => {
    expect(generateUniqueName('foo', () => false)).toBe('foo');
  });
  it('should return empty string when empty string is provided', () => {
    expect(generateUniqueName('', () => true)).toBe('');
  });
  it('should use max suffix', () => {
    expect(generateUniqueName('foo', () => true)).toBe(`foo ${MAX_UNIQUE_NAME_SUFFIX}`);
  });
  it('should increment when starting without an ending number', () => {
    expect(generateUniqueName('foo', (newName) => ['foo', 'foo 2'].includes(newName))).toBe('foo 3');
  });
  it('should replace the last digit when incrementing up', () => {
    expect(generateUniqueName('foo 2', (newName) => ['foo', 'foo 2'].includes(newName))).toBe('foo 3');
  });
  it('should replace the last digit even when multiple digits exist', () => {
    expect(generateUniqueName('foo 12', (newName) => ['foo', 'foo 12'].includes(newName))).toBe('foo 13');
  });
  it('should increase the count even if there is no match lower than the number', () => {
    expect(generateUniqueName('foo 5', (newName) => ['foo', 'foo 5'].includes(newName))).toBe('foo 6');
  });
});

describe('createApiName should sanitize names', () => {
  test.each([
    ['A great NAME', 'a_great_name'],
    ['A  great   NAME  ', 'a_great_name'],
    ['A  great *&^  NAME  ', 'a_great_name'],
  ])(`createApiName converts %s to %s`, (text, expectedApiName) => {
    expect(createApiName(text)).toBe(expectedApiName);
  });
});

describe('base64DecodeUtf8', () => {
  it('should correctly decode UTF-8 multi-byte characters', () => {
    const encodedString = 'ewogICAgInRlc3QiOiAiUm9rIEtvdmHEjSIKfQ==';
    expect(base64DecodeUtf8(encodedString)).toBe('{\n    "test": "Rok Kovač"\n}');
  });

  it('should correctly decode ASCII characters', () => {
    const encodedString = btoa('Hello, world!');
    expect(base64DecodeUtf8(encodedString)).toBe('Hello, world!');
  });

  it('should handle an empty string', () => {
    const encodedString = btoa('');
    expect(base64DecodeUtf8(encodedString)).toBe('');
  });
});

describe('generateUniqueNamesCallback', () => {
  it('should increment the number at the end of a name to make it unique', () => {
    const makeNameUnique = generateUniqueNamesCallback(['Fred']);

    let result = makeNameUnique('Fred');
    expect(result).toBe('Fred 2');

    result = makeNameUnique(result);
    expect(result).toBe('Fred 3');

    result = makeNameUnique(result);
    expect(result).toBe('Fred 4');
  });

  it('should support double digts', () => {
    const makeNameUnique = generateUniqueNamesCallback(['Fred 9']);

    let result = makeNameUnique('Fred 9');
    expect(result).toBe('Fred 10');

    result = makeNameUnique(result);
    expect(result).toBe('Fred 11');
  });

  it('should only be looking for digits at the end of the string', () => {
    const makeNameUnique = generateUniqueNamesCallback(['Fred 11 Smith']);

    const result = makeNameUnique('Fred 11 Smith');
    expect(result).toBe('Fred 11 Smith 2');
  });

  it("should not change the name if it's not in the array", () => {
    const makeNameUnique = generateUniqueNamesCallback([]);

    const name = 'Name not in names array';
    const result = makeNameUnique(name);
    expect(result).toBe(name);
  });
});

describe('safe decodeURI', () => {
  // prettier-ignore
  test.each([
    ['test', 'test'],
    ['test%20test', 'test test'],
    ['test%test', 'test%test'],
  ])(`safedecodeURI %s returns %s`, (input, expectedStatus) => {
      expect(safeDecodeURIComponent(input)).toBe(expectedStatus);
    })
});
