import AppConstants from 'utils/AppConstants';

import { DataScoreStatus, EnhancedDataScoreFactor } from './types';
import { encodeFactorId } from './utils';

export interface DataScoreFixtureParams {
  entityId?: string;
  entityName?: string;
  status?: DataScoreStatus;
  score?: number;
  label?: string;
  percentIncrease?: number;
  fieldNames?: [string, string, string];
  ruleIds?: [string, string, string];
}

export const getDataScoreTestState = (config?: DataScoreFixtureParams) => {
  const {
    entityId = '5e613d6598a68d0001ab85b7',
    entityName = 'Account',
    status = 'available',
    score = 7,
    label = 'Poor',
    percentIncrease = 0,
    fieldNames = ['BillingState', 'Website', 'BillingPostalCode'],
    ruleIds = ['5fd065a57df51d113e4a541c', '5fd065a57df51d113e4a541c', '5fd065a57df51d113e4a541c'],
  } = config || {};

  return {
    dataScoreByEntity: {
      [entityId]: {
        status,
        data: {
          score,
          label,
          sourceScore: null,
          percentIncrease,
          factors: ([
            {
              category: 'bottom',
              label: `Is Not Empty / ${fieldNames[0]}`,
              description: 'Rule to check if the value is not empty',
              ruleId: ruleIds[0],
              fieldName: fieldNames[0],
              entityId,
              averageScore: 0,
              filterCondition: {
                operator: 'lte',
                left: {
                  datatype: 'integer',
                  type: 'variable',
                  value: `rule_isNotEmpty_${fieldNames[0]}`,
                },
                right: {
                  value: 0,
                  type: 'literal',
                },
              },
              factorId: encodeFactorId(entityId, fieldNames[0], ruleIds[0]),
            },
            {
              category: 'bottom',
              label: `Is Not Empty / ${fieldNames[1]}`,
              description: 'Rule to check if the value is not empty',
              ruleId: ruleIds[1],
              fieldName: fieldNames[1],
              entityId,
              averageScore: 0,
              filterCondition: {
                operator: 'lte',
                left: {
                  datatype: 'integer',
                  type: 'variable',
                  value: `rule_isNotEmpty_${fieldNames[1]}`,
                },
                right: {
                  value: 0,
                  type: 'literal',
                },
              },
              factorId: encodeFactorId(entityId, fieldNames[1], ruleIds[1]),
            },
            {
              category: 'bottom',
              label: `Is Not Empty / ${fieldNames[2]}`,
              description: 'Rule to check if the value is not empty',
              ruleId: ruleIds[2],
              fieldName: fieldNames[2],
              entityId,
              averageScore: 0,
              filterCondition: {
                operator: 'lte',
                left: {
                  datatype: 'integer',
                  type: 'variable',
                  value: `rule_isNotEmpty_${fieldNames[2]}`,
                },
                right: {
                  value: 0,
                  type: 'literal',
                },
              },
              factorId: encodeFactorId(entityId, fieldNames[2], ruleIds[2]),
            },
            // gotta please the TS overlords
          ] as unknown[]) as EnhancedDataScoreFactor[],
          trend: {
            rangeInDays: 30,
            deltaPercent: 0,
            dataPoints: {
              '2021-01-30': 7,
              '2021-01-31': 7,
              '2021-02-01': 7,
              '2021-02-02': 7,
              '2021-02-03': 7,
              '2021-02-04': 7,
              '2021-02-05': 7,
              '2021-02-08': 7,
              '2021-02-09': 7,
              '2021-02-10': 7,
              '2021-02-11': 7,
              '2021-02-12': 7,
              '2021-02-13': 7,
              '2021-02-18': 7,
              '2021-02-19': 7,
              '2021-02-23': 7,
              '2021-02-24': 7,
              '2021-02-25': 7,
              '2021-02-26': 7,
              '2021-02-27': 7,
              '2021-03-01': 7,
            },
          },
          entityName,
          links: null,
        },
      },
    },
    dataScoreStatusByEntity: {
      '5e613d6598a68d0001ab85b7': AppConstants.FETCH_STATUS.SUCCESS,
    },
    dataScoreErrorByEntity: {
      '5e613d6598a68d0001ab85b7': null,
    },
  };
};
