import { User } from 'store/user/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { UserRole, UserPermission, CreateRolePayload, EditRolePayload } from './types';

const rbacApi = injectEndpoints({
  endpoints: (builder) => ({
    getAllRoles: builder.query<UserRole[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.SETTINGS_RBAC_ALL_ROLES }),
      providesTags: [tags.RBACList],
    }),
    getRoleById: builder.query<UserRole | undefined, { roleId: string }>({
      query: ({ roleId }) => ({ url: makeUrl(DataUrlConstants.SETTINGS_RBAC_ROLE_DETAILS, { roleId }) }),
      providesTags: [tags.RBACList],
    }),
    getAllPermissions: builder.query<UserPermission[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.SETTINGS_ALL_PERMISSIONS }),
    }),
    getAllUsers: builder.query<User[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.USER }),
    }),
    createRole: builder.mutation<UserRole, CreateRolePayload>({
      query: (role) => {
        return {
          url: makeUrl(DataUrlConstants.SETTINGS_RBAC_CREATE_ROLE),
          method: 'POST',
          body: role,
        };
      },
      invalidatesTags: [tags.RBACList],
    }),
    editRole: builder.mutation<UserRole, EditRolePayload>({
      query: (role) => {
        return {
          url: makeUrl(DataUrlConstants.SETTINGS_RBAC_ROLE_DETAILS, { roleId: role.roleId }),
          method: 'PUT',
          body: role,
        };
      },
      invalidatesTags: [tags.RBACList],
    }),
    deleteRole: builder.mutation<{ status: string }, { roleId: string }>({
      query: ({ roleId }) => {
        return {
          url: makeUrl(DataUrlConstants.SETTINGS_RBAC_ROLE_DETAILS, { roleId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.RBACList],
    }),
  }),
});

export const {
  useGetAllRolesQuery,
  useGetRoleByIdQuery,
  useGetAllPermissionsQuery,
  useGetAllUsersQuery,
  useCreateRoleMutation,
  useEditRoleMutation,
  useDeleteRoleMutation,
} = rbacApi;
