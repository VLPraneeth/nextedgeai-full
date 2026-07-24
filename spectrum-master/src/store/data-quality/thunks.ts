import { createAsyncThunk } from '@reduxjs/toolkit';

import { deleteRequest, get, post } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { makeUrl } from 'utils/UrlUtil';

import { dfiRecalculationUpdate } from './slice';
import { DfiRuleFormValues, EntityDfiRulesPostData, EntityDfiRulesResponse } from './types';

const tn = tNamespaced('DataQualityRules');

export interface GetDfiRulesForEntityArgs {
  entityId: string;
}

export const getDfiRulesForEntity = createAsyncThunk<EntityDfiRulesResponse, GetDfiRulesForEntityArgs>(
  'dataQuality/rules/entity',
  ({ entityId }: GetDfiRulesForEntityArgs) => {
    const url = makeUrl(DataUrlConstants.DFI_RULES_FOR_ENTITY, { entityId });

    return get<EntityDfiRulesResponse>(url).then((resp) => resp.data);
  }
);

export interface SaveDfiRuleForEntityArgs {
  entityId: string;
  formValues: DfiRuleFormValues;
}

export const saveDfiRule = createAsyncThunk<
  EntityDfiRulesResponse,
  EntityDfiRulesPostData,
  { rejectValue: { errorMessage: string } }
>('dataQuality/rules/save', (entityRuleData, { rejectWithValue }) => {
  const url = makeUrl(DataUrlConstants.DFI_RULES_FOR_ENTITY, { entityId: entityRuleData.entityId });

  return post(url, entityRuleData)
    .then((resp) => resp.data)
    .catch((error) => {
      const formattedError = getErrorMessage(error);
      const errorMessage =
        'message' in formattedError && formattedError.message ? formattedError.message : tn('error_saving_rules');
      return rejectWithValue({ errorMessage });
    });
});

export const discardDfiRulesDraft = createAsyncThunk<
  EntityDfiRulesResponse,
  string,
  { rejectValue: { errorMessage: string } }
>('dataQuality/rules/discardDraft', (entityId, { rejectWithValue }) => {
  const url = makeUrl(DataUrlConstants.DFI_RULES_FOR_ENTITY, { entityId });

  return deleteRequest<EntityDfiRulesResponse>(url)
    .then((resp) => resp.data)
    .catch((error) => {
      const formattedError = getErrorMessage(error);
      const errorMessage =
        'message' in formattedError && formattedError.message ? formattedError.message : tn('unable_to_delete_draft');
      return rejectWithValue({ errorMessage });
    });
});

export const publishDfiRules = createAsyncThunk<
  EntityDfiRulesResponse,
  EntityDfiRulesPostData,
  { rejectValue: { errorMessage: string } }
>('dataQuality/rules/publish', (entityRuleData, { dispatch, rejectWithValue }) => {
  const url = makeUrl(DataUrlConstants.PUBLISH_DFI_RULES_FOR_ENTITY, { entityId: entityRuleData.entityId });

  return post<EntityDfiRulesResponse>(url, entityRuleData)
    .then((resp) => {
      // Set the recalc progress to zero since we don't get the first
      // progressPercentage message from the backend until after the
      // first batch is completed
      dispatch(
        dfiRecalculationUpdate({
          completed: false,
          progressPercentage: 0,
          entityId: entityRuleData.entityId,
        })
      );

      return resp.data;
    })
    .catch((error) => {
      const formattedError = getErrorMessage(error);
      const errorMessage =
        'message' in formattedError && formattedError.message ? formattedError.message : tn('error_publishing_rules');
      return rejectWithValue({ errorMessage });
    });
});

export const checkCustomRuleAssignmentExists = createAsyncThunk<
  boolean,
  void,
  { rejectValue: { errorMessage: string } }
>('dataQuality/rules/checkCustomRuleAssignmentExists', (_, { rejectWithValue }) => {
  const url = DataUrlConstants.IS_CUSTOM_RULE_ASSIGNMENT_EXISTS;

  return get<boolean>(url)
    .then((resp) => resp.data)
    .catch((error) => {
      const formattedError = getErrorMessage(error);
      const errorMessage =
        'message' in formattedError && formattedError.message
          ? formattedError.message
          : tn('error_checking_custom_rules');
      return rejectWithValue({ errorMessage });
    });
});
