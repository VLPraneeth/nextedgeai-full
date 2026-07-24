import { User } from 'store/user/types';

export interface UserPermission {
  resourceId?: string;
  privilegeId: string;
  displayName: string;
}

export interface UserRole {
  id: string;
  name: string;
  description?: string;
  active: boolean;
  privileges: UserPermission[];
  users: Omit<User, 'timezone' | 'ghosted'>[];
  system: boolean;
  tags?: string[];
}

export interface CreateRolePayload extends Omit<UserRole, 'id' | 'privileges' | 'users' | 'system'> {
  privileges: string[];
  users: string[];
}

export interface EditRolePayload extends CreateRolePayload {
  roleId: string;
}
