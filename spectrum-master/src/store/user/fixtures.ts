import { merge } from 'lodash';

import { _getDefaultState } from './reducer';
import { UserState } from './types';

export const getEmptyUserState = (user?: Partial<UserState>): UserState => merge(_getDefaultState(), user);
