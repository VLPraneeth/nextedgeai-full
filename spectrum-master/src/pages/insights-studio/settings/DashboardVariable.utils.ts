import { DataCardSkullConfig } from 'store/insights-studio/types';

export const isVariableVisible = (name: string, configurations?: DataCardSkullConfig[]) => {
  return configurations?.some((configuration) => configuration.name === name);
};
