import { can } from 'utils/AppUtil';
import CapConstants from 'utils/CapConstants';

describe('can', () => {
  test('should return true when one permission is met', () => {
    const result = can([CapConstants.SUPER_ADMIN], [CapConstants.SUPER_ADMIN, CapConstants.ADMIN]);
    expect(result).toBe(true);
  });

  test('should return false when no permissions are met', () => {
    const result = can([CapConstants.ADMIN], [CapConstants.SUPER_ADMIN]);
    expect(result).toBe(false);
  });
});
