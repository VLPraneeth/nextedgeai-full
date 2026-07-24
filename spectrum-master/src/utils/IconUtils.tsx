import * as React from 'react';

/* When passing an Icon to antd Icon like, <Icon component={iconComponent} /> we need to wrap the icon
 * to tell antd that it's a React element.
 *
 * @example
 *
 */

export const wrapIcon = (IconComponent: React.FC): React.FC => (props) => <IconComponent {...props} />;
