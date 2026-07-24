import { createContext, useContext } from 'react';

import { SkullReactContext } from './skull.types';

export const SkullConfigContext = createContext<SkullReactContext>({} as any);
export const SkullConfigProvider = SkullConfigContext.Provider;
export const SkullConfigConsumer = SkullConfigContext.Consumer;

export const useSkullConfigContext = () => useContext(SkullConfigContext);
