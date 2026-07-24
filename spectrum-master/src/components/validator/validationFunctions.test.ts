import { hasNoValue, hasSpaces, hasSpecialCharacters } from './validationFunctions';

describe('validation functions', () => {
  describe('hasNoValue', () => {
    it('returns true with no value present', () => {
      expect(hasNoValue()).toBe(true);
      expect(hasNoValue(null)).toBe(true);
      expect(hasNoValue('')).toBe(true);
      expect(hasNoValue('     ')).toBe(true);
    });
    it('returns false with a value', () => {
      expect(hasNoValue('test')).toBe(false);
      expect(hasNoValue('   test')).toBe(false);
      expect(hasNoValue('test    ')).toBe(false);
      expect(hasNoValue(true)).toBe(false);
      expect(hasNoValue(false)).toBe(false);
    });
  });

  describe('hasSpecialCharacters', () => {
    it('returns true if special characters present other than underscores are present', () => {
      expect(hasSpecialCharacters('test!')).toBe(true);
      expect(hasSpecialCharacters('test?')).toBe(true);
      expect(hasSpecialCharacters('test@')).toBe(true);
      expect(hasSpecialCharacters('test...')).toBe(true);
      expect(hasSpecialCharacters('test,test')).toBe(true);
      expect(hasSpecialCharacters('test-test')).toBe(true);
    });
    it('returns false for strings with no special characters other than underscores, or no value', () => {
      expect(hasSpecialCharacters('test')).toBe(false);
      expect(hasSpecialCharacters('test_test')).toBe(false);
      expect(hasSpaces(null)).toBe(false);
      expect(hasSpaces()).toBe(false);
    });
  });

  describe('hasSpaces', () => {
    it('returns true if special characters present other than underscores are present', () => {
      expect(hasSpaces('test this')).toBe(true);
    });
    it('returns false for strings without spaces or no value', () => {
      expect(hasSpaces('test')).toBe(false);
      expect(hasSpaces('test_test')).toBe(false);
      expect(hasSpaces('test...')).toBe(false);
      expect(hasSpaces('test,test')).toBe(false);
      expect(hasSpaces('test-test')).toBe(false);
      expect(hasSpaces(null)).toBe(false);
      expect(hasSpaces()).toBe(false);
    });
  });
});
