import { createAction, createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { SelectValue } from 'antd/lib/select';

import { FetchStatus } from 'store/types';
import { deleteRequest, get, post, put } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { getReducerDefaultValues } from 'utils/LocalStorageUtil';
import RouteConstants from 'utils/RouteConstants';
import { ValuesOf } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';

import { mockTrialInstanceState } from './useCurrentInstanceState/mockInstanceState';

const tn = tNamespaced('Settings.Instances');

export type InstanceType = 'sandbox' | 'production' | 'trial' | 'internal';
export type InstanceStatus = ValuesOf<typeof AppConstants.INSTANCE_STATUS>;

export const LOCAL_STORAGE_INSTANCE_ID = 'LOCAL_STORAGE_INSTANCE_ID';

export interface Instance {
  displayName: string;
  features: string[];
  name: string;
  orgId: string | null;
  orgName: string | null;
  planId: string | null;
  planName: string | null;
  status: InstanceStatus;
  syncariId: string;
  type: InstanceType;
  hasGhostAccessOnInstance: boolean;
  createdByName?: string;
  createdByEmail?: string;

  // Below properties are only included on instance returned with InstanceState
  active: boolean;
  quota: InstanceQuota[];
  trial: boolean;
}

export type OrgInstancesMap = Record<string, Instance>;

// All properties are undefined until API request executes
export interface InstanceState {
  instance?: Instance;
  numberOfRecordsLeft?: number;
  publishLimitExpired?: boolean;
  recordLimitExpired?: boolean;
  trialDaysLeft?: number;
  trialExpired?: boolean;
  expiryDate?: string;
  numberofSynapses?: number;
  numberofPipelines?: number;
}

export enum InstanceQuotaType {
  days = 'TRIAL_DAYS_LIMIT',
  pipelines = 'PIPELINE_PUBLISH_LIMIT',
  records = 'RECORDS_LIMIT',
}
interface InstanceQuota {
  connectorId: string | null;
  createdAt: string | null;
  createdBy: string | null;
  id: string | null;
  type: ValuesOf<InstanceQuotaType>;
  updatedAt: string | null;
  updatedBy: string | null;
  value: string;
}

interface InstanceCopyModalState {
  visible: boolean;
  modalType?: InstanceCopyModalType;
  syncariId?: string;
}

interface InstanceEditModalState {
  visible: boolean;
  instance: Instance;
}

export enum InstanceCopyModalType {
  ORG_ONLY = 'ORG_ONLY',
  GLOBAL = 'GLOBAL',
}

export interface InstanceSlice {
  currentInstanceState: InstanceState;
  instanceCreatingStatus: FetchStatus;
  instanceCreatingErrorMessage: string | null;
  instanceUpdatingStatus: FetchStatus;
  instanceUpdatingErrorMessage: string | null;
  instanceModalVisible: boolean;
  instanceModalEditInstance?: Instance | null;
  instanceCopyModal: InstanceCopyModalState;
  instanceCopyStatus: FetchStatus;
  instances: Instance[];
  instancesStatus: FetchStatus;
  manageTrialInstanceId: string;
  pendingInstanceUpdates: Record<string, FetchStatus>;
}

export function _getDefaultState(): InstanceSlice {
  return {
    // You can override the deafult values of the instance by
    // putting some values in the local storage
    ...getReducerDefaultValues(AppConstants.REDUCER_NAME.INSTANCE),
    currentInstanceState: {},
    instanceCreatingStatus: AppConstants.FETCH_STATUS.IDLE,
    instanceUpdatingStatus: AppConstants.FETCH_STATUS.IDLE,
    instanceModalVisible: false,
    instanceCopyModal: {
      visible: false,
    },
    instances: [],
    instancesStatus: AppConstants.FETCH_STATUS.IDLE,
    pendingInstanceUpdates: {},
  };
}

export const getInstances = createAsyncThunk<Instance[]>('instances/instances', (_, { rejectWithValue }) => {
  return get<Instance[]>(DataUrlConstants.INSTANCE)
    .then((resp) => resp.data)
    .catch((err) => {
      window.location.assign(RouteConstants.LOGIN);
      return rejectWithValue(err.message);
    });
});

export interface CreateInstanceParams {
  instanceName: string;
  displayName: string;
  orgId: string;
  planName: string;
  type: InstanceType;
}

export const createInstance = createAsyncThunk<Instance, CreateInstanceParams>(
  'instances/instance/create',
  (params: CreateInstanceParams, { rejectWithValue }) => {
    return post<Instance>(DataUrlConstants.INSTANCE, params)
      .then((resp) => resp.data)
      .catch((err) => {
        return rejectWithValue(err?.response?.data?.message || tn('fallback_create_instance_error'));
      });
  }
);

export interface EditInstanceParams {
  displayName: string;
  type: InstanceType;
}
export const updateInstance = createAsyncThunk<Instance, EditInstanceParams>(
  'instances/instance',
  (params: EditInstanceParams, { rejectWithValue }) => {
    return put<Instance>(DataUrlConstants.INSTANCE, params)
      .then((resp) => resp.data)
      .catch((err) => {
        return rejectWithValue(err?.response?.data?.message || tn('fallback_create_instance_error'));
      });
  }
);

export const deleteInstance = createAsyncThunk(
  'instances/instance/delete',
  (instanceId: string, { rejectWithValue }) => {
    return deleteRequest<null>(makeUrl(DataUrlConstants.DELETE_INSTANCE, { instanceId }))
      .then((resp) => resp.data)
      .catch((err) => {
        return rejectWithValue(err?.response?.data?.message || tn('fallback_delete_instance_error'));
      });
  }
);

export const switchInstance = createAsyncThunk(
  'instances/instance/switch',
  (instanceId: string, { rejectWithValue }) => {
    return get(makeUrl(DataUrlConstants.SWITCH_INSTANCE, { instanceId }))
      .then((resp) => {
        // hard refresh the SPA to pick up new data
        window.location.assign('/');
      })
      .catch((err) => {
        rejectWithValue(err?.response?.error?.message || tn('fallback_switch_instance_error'));
      });
  }
);

export const copyInstance = createAsyncThunk(
  'instances/instance/copy',
  (
    params: { sourceInstanceId: string; destinationInstanceId: string; emailRecipients: SelectValue },
    { rejectWithValue }
  ) => {
    const { sourceInstanceId, destinationInstanceId, ...requestBody } = params;
    return post(makeUrl(DataUrlConstants.COPY_INSTANCE, { sourceInstanceId, destinationInstanceId }), requestBody)
      .then((resp) => resp.data)
      .catch((err) => {
        return rejectWithValue(err?.response?.error?.message);
      });
  }
);

export const getInstanceState = createAsyncThunk<InstanceState, string>(
  'instances/instance/state',
  (instanceId, { rejectWithValue }) => {
    // For Testing only
    if (localStorage.getItem(AppConstants.SIMULATE_TRIAL_INSTANCE) === 'true') {
      return Promise.resolve(mockTrialInstanceState);
    }

    return get<InstanceState>(makeUrl(DataUrlConstants.INSTANCE_STATE, { instanceId }))
      .then((resp) => resp.data)
      .catch((err) => {
        return rejectWithValue(err?.response?.error?.message || tn('fallback_switch_instance_error'));
      });
  }
);

export const setManageTrialInstanceId = createAction<string>('manage-trial-modal');

const initialState = _getDefaultState();

const instancesSlice = createSlice({
  name: 'instances',
  initialState,
  reducers: {
    showInstanceModal: {
      // instead of just a callback, we'll use this config object so we
      // can make the flag optional on the action creator
      reducer: (state, action: PayloadAction<boolean>) => {
        state.instanceModalVisible = action.payload;
        state.instanceModalEditInstance = null;
      },
      // this let's us make the flag optional
      prepare: (flag = true) => {
        return { payload: flag };
      },
    },
    showInstanceCopyModal: {
      reducer: (state, action: PayloadAction<InstanceCopyModalState>) => {
        state.instanceCopyModal.visible = action.payload.visible;
        state.instanceCopyModal.modalType = action.payload.modalType;
        state.instanceCopyModal.syncariId = action.payload.syncariId;
      },
      prepare: (value?: InstanceCopyModalState) => {
        return {
          payload: value || { visible: false },
        };
      },
    },
    showInstanceEditModal: {
      reducer: (state, action: PayloadAction<InstanceEditModalState>) => {
        state.instanceModalVisible = action.payload.visible;
        state.instanceModalEditInstance = action.payload.instance;
      },
      prepare: (value: InstanceEditModalState) => {
        return {
          payload: value || { visible: false },
        };
      },
    },
    resetInstanceModalState: {
      reducer: (state) => {
        state.instanceCreatingStatus = AppConstants.FETCH_STATUS.IDLE;
        state.instanceCreatingErrorMessage = null;
        state.instanceUpdatingStatus = AppConstants.FETCH_STATUS.IDLE;
        state.instanceUpdatingErrorMessage = null;
      },
      prepare: (value?: InstanceEditModalState) => {
        return {
          payload: value || { visible: false },
        };
      },
    },
  },
  extraReducers: (builder) => {
    // getInstances
    builder.addCase(getInstances.pending, (state, action) => {
      state.instancesStatus = AppConstants.FETCH_STATUS.LOADING;
    });
    builder.addCase(getInstances.fulfilled, (state, action) => {
      state.instancesStatus = AppConstants.FETCH_STATUS.SUCCESS;
      state.instances = action.payload || [];
    });
    builder.addCase(getInstances.rejected, (state, action) => {
      state.instancesStatus = AppConstants.FETCH_STATUS.ERROR;
    });

    // createInstance
    builder.addCase(createInstance.pending, (state, action) => {
      state.instanceCreatingStatus = AppConstants.FETCH_STATUS.LOADING;
      state.instanceCreatingErrorMessage = null;
    });
    builder.addCase(createInstance.fulfilled, (state, action) => {
      state.instanceCreatingStatus = AppConstants.FETCH_STATUS.SUCCESS;
      state.instances.push(action.payload);
    });
    builder.addCase(createInstance.rejected, (state, action) => {
      state.instanceCreatingStatus = AppConstants.FETCH_STATUS.ERROR;
      state.instanceCreatingErrorMessage = action.payload as string;
    });

    // editInstance
    builder.addCase(updateInstance.pending, (state, action) => {
      state.instanceUpdatingStatus = AppConstants.FETCH_STATUS.LOADING;
      state.instanceUpdatingErrorMessage = null;
    });
    builder.addCase(updateInstance.fulfilled, (state, action) => {
      state.instanceUpdatingStatus = AppConstants.FETCH_STATUS.SUCCESS;
    });
    builder.addCase(updateInstance.rejected, (state, action) => {
      state.instanceUpdatingStatus = AppConstants.FETCH_STATUS.ERROR;
      state.instanceUpdatingErrorMessage = action.payload as string;
    });

    // copyInstance
    builder.addCase(copyInstance.pending, (state, action) => {
      state.instanceCopyStatus = AppConstants.FETCH_STATUS.LOADING;
    });

    builder.addCase(copyInstance.fulfilled, (state, action) => {
      state.instanceCopyStatus = AppConstants.FETCH_STATUS.SUCCESS;
    });

    builder.addCase(copyInstance.rejected, (state, action) => {
      state.instanceCopyStatus = AppConstants.FETCH_STATUS.ERROR;
    });

    // deleteInstance
    builder.addCase(deleteInstance.pending, (state, action) => {
      const instanceId = action.meta.arg;
      state.pendingInstanceUpdates[instanceId] = AppConstants.FETCH_STATUS.LOADING;
    });
    builder.addCase(deleteInstance.fulfilled, (state, action) => {
      const instanceId = action.meta.arg;
      state.pendingInstanceUpdates[instanceId] = AppConstants.FETCH_STATUS.SUCCESS;
      state.instances = state.instances.filter((instance) => instance.syncariId !== instanceId);
    });
    builder.addCase(deleteInstance.rejected, (state, action) => {
      const instanceId = action.meta.arg;
      state.pendingInstanceUpdates[instanceId] = AppConstants.FETCH_STATUS.ERROR;
    });

    // getInstanceState
    builder.addCase(getInstanceState.fulfilled, (state, action) => {
      state.currentInstanceState = action.payload;
    });

    // setManageTrailInstanceId
    builder.addCase(setManageTrialInstanceId, (state, action) => {
      state.manageTrialInstanceId = action.payload;
    });

    // Possibly add state for this later
    // builder.addCase(switchInstance.pending, (state, action) => {});
    // builder.addCase(switchInstance.fulfilled, (state, action) => {});
    // builder.addCase(switchInstance.rejected, (state, action) => {});
  },
});

export default instancesSlice;
export const {
  actions: { showInstanceModal, showInstanceCopyModal, showInstanceEditModal, resetInstanceModalState },
  reducer,
} = instancesSlice;
export const thunks = {
  getInstances,
  createInstance,
  deleteInstance,
  switchInstance,
};
