import { parseJSON } from 'utils/JsonUtil';

describe('parseJSON', () => {
  it('should return a JSON object when the input string is valid JSON', () => {
    const inputString = '{"name": "John", "age": 30}';

    const result = parseJSON(inputString);

    expect(result).toEqual({ name: 'John', age: 30 });
  });

  it('should return the input string as is when it is not valid JSON', () => {
    const inputString = 'This is not a valid JSON string';

    const result = parseJSON(inputString);

    expect(result).toEqual(inputString);
  });

  it('should handle empty JSON objects', () => {
    const inputString = '{}';

    const result = parseJSON(inputString);

    expect(result).toEqual({});
  });

  it('should handle JSON arrays', () => {
    const inputString = '[1, 2, 3]';

    const result = parseJSON(inputString);

    expect(result).toEqual([1, 2, 3]);
  });

  it('should handle nested JSON objects', () => {
    const inputString = '{"name": "John", "age": 30, "address": {"street": "123 Main St", "city": "Anytown"}}';

    const result = parseJSON(inputString);

    expect(result).toEqual({ name: 'John', age: 30, address: { street: '123 Main St', city: 'Anytown' } });
  });

  it('should handle escaped characters in JSON strings', () => {
    const inputString = '{"name": "John \\"Doe\\""}';

    const result = parseJSON(inputString);

    expect(result).toEqual({ name: 'John "Doe"' });
  });

  it('should handle empty input strings', () => {
    const inputString = '';

    const result = parseJSON(inputString);

    expect(result).toEqual('');
  });
});
