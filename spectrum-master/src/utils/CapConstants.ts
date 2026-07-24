//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Capability Constant strings
//

const CapConstants: Record<string, string> = {
  // User with org admin role
  ADMIN: 'Org Admin',

  // User with instance admin role
  INSTANCE_ADMIN: 'Instance Admin',

  // User with super admin role
  SUPER_ADMIN: 'Super Admin',

  // User with sync manager role
  SYNC_MANAGER: 'Sync Manager',

  // User with viewer role
  VIEWER: 'Viewer',

  // User with `ghosted` attribute true
  // A user "ghosted" in the instance. In unix, this is like a user sudo'ed to a different user
  // to gain access to a resource. The resource in this case is instance.
  GHOSTED: 'ghosted',

  // User with `isGhostUser` attribute true
  // User can request a ghost access into an instance.
  // In unix, this user is a sudo'er.
  IS_GHOST_USER: 'Is Ghost User',

  // User with Insights shared dashboard viewer role(light user)
  DASHBOARD_LIGHT_VIEWER: 'Dashboard Light Viewer',
};

export const RoleGroup = {
  SUPER_GHOST: [CapConstants.SUPER_ADMIN, CapConstants.GHOSTED],
  ADMIN_SUPER_GHOST: [CapConstants.ADMIN, CapConstants.INSTANCE_ADMIN, CapConstants.SUPER_ADMIN, CapConstants.GHOSTED],
};

export default CapConstants;
