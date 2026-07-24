import { safeDecodeBase64 } from 'utils/StringUtil';

import { noScoreSegment } from './constants';
import { SegmentConfig } from './types';

const FACTOR_KEY_DELIMITER = ':';

/**
 * in order to reference datascore contributing factors from separate areas
 * of spectrum, we encode the 3 identifying pieces together into an Id
 * entityId, fieldName, ruleId
 *
 * these are then joined with `:` and base64 encoded
 */

export const encodeFactorId = (entityId: string, fieldName: string, ruleId: string) =>
  btoa([entityId, fieldName, ruleId].join(FACTOR_KEY_DELIMITER));

export const decodeFactorId = (factorId: string) => safeDecodeBase64(factorId).split(FACTOR_KEY_DELIMITER);

export const getSegmentForValue = (segments: SegmentConfig[], value: number | null | undefined) => {
  if (typeof value !== 'number') {
    return noScoreSegment;
  }

  return segments.find((s) => s.min <= value && s.max >= value) || noScoreSegment;
};
