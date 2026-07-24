import { SingleTokenInputType, singleTokenEligible } from './types';

export const isSingleTokenEligible = (variableToCheck: any): variableToCheck is SingleTokenInputType => {
  return singleTokenEligible.includes(variableToCheck);
};
