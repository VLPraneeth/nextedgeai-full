import { InstanceType } from 'store/instances/slice';

export interface UserflowUserAttributes {
  days_remaining?: number;
  email?: string;
  end_date?: string;
  first_name?: string;
  in_trial?: boolean;
  instance_id?: string;
  instance_type?: InstanceType;
  last_name?: string;
  pipeline_count?: number;
  record_count?: number;
  signed_up_at?: string;
  synapse_count?: number;
  insights_enabled?: boolean;
}
