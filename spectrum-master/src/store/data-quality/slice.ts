import { createSlice, PayloadAction } from '@reduxjs/toolkit';

import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';

import { discardDfiRulesDraft, getDfiRulesForEntity, publishDfiRules, saveDfiRule } from './thunks';
import { DataQualityState } from './types';

type UpdateDfiRulesAction =
  | ReturnType<typeof getDfiRulesForEntity['fulfilled']>
  | ReturnType<typeof saveDfiRule['fulfilled']>;

export type DfiRecalculationUpdateAction = PayloadAction<{
  completed: boolean;
  entityId: string;
  progressPercentage: number;
}>;

export type ShowDfiRuleDetailsAction = PayloadAction<{
  visible: boolean;
  ruleId?: string;
}>;

export const initialState: DataQualityState = {
  dfiRuleDetailsOpen: false,
  dfiRulesByEntity: {},
  dfiRulesStatusByEntity: {},
  dfiRulesErrorByEntity: {},
  dfiRulesRecalculatingByEntity: {},
};

const dataQualitySlice = createSlice({
  name: 'dataQuality',
  initialState,
  reducers: {
    showDfiRuleDetails: (state, action: ShowDfiRuleDetailsAction) => {
      state.dfiRuleDetailsOpen = action.payload.visible;
      state.dfiRuleDetailsRuleId = action.payload.ruleId;
    },
    dfiRecalculationUpdate: (state, action: DfiRecalculationUpdateAction) => {
      state.dfiRulesRecalculatingByEntity[action.payload.entityId] = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(getDfiRulesForEntity.pending, (state, action) => {
      const {
        arg: { entityId },
      } = action.meta;

      state.dfiRulesErrorByEntity[entityId] = null;
      state.dfiRulesStatusByEntity[entityId] = AppConstants.FETCH_STATUS.LOADING;
    });

    builder.addCase(getDfiRulesForEntity.rejected, (state, action) => {
      const {
        arg: { entityId },
      } = action.meta;

      state.dfiRulesErrorByEntity[entityId] = action.error;
      state.dfiRulesStatusByEntity[entityId] = AppConstants.FETCH_STATUS.ERROR;
    });

    // getDfiRulesForEntity, saveDfiRule, publishDfiRules, and
    // discardDfiRulesDraft return the updated DFI Rules for an entity so all
    // are included here with the same reducer. Note: addMatcher has to come
    // after any instances of addCase.
    builder.addMatcher<UpdateDfiRulesAction>(
      (action) =>
        [
          getDfiRulesForEntity.fulfilled.toString(),
          saveDfiRule.fulfilled.toString(),
          publishDfiRules.fulfilled.toString(),
          discardDfiRulesDraft.fulfilled.toString(),
        ].includes(action.type),
      (state, action) => {
        // Remove the entityDef since we only need the activeFields
        const { entityDef, ...payloadToStore } = action.payload;

        state.dfiRulesByEntity[action.payload.entityId] = {
          ...payloadToStore,
          fields: entityDef.activeFields || EMPTY_ARRAY,
        };
        state.dfiRulesStatusByEntity[action.payload.entityId] = AppConstants.FETCH_STATUS.SUCCESS;
      }
    );
  },
});

export const {
  reducer,
  actions: { dfiRecalculationUpdate, showDfiRuleDetails },
} = dataQualitySlice;
