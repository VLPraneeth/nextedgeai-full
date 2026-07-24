import { variables } from 'utils/LessConstants';

export type Alignment = 'start' | 'center' | 'end' | 'baseline' | 'stretch';

export type Justification = 'start' | 'center' | 'end' | 'space-around' | 'space-between' | 'space-evenly';

export type Spacing = keyof typeof variables.spacings;
