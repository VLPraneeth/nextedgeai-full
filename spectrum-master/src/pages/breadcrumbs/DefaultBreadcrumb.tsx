import { RouteComponentProps } from '@reach/router';
import BreadcrumbSeparator from 'antd/lib/breadcrumb/BreadcrumbSeparator';
import { startCase } from 'lodash';
import React, { useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { tNamespacedOptional } from 'utils/i18nUtil';

const tn = tNamespacedOptional('UrlUtil');

export interface DefaultBreadcrumbProps extends RouteComponentProps {
  children?: React.ReactNode;
  path?: string;
}
export const DefaultBreadcrumb = ({ location }: DefaultBreadcrumbProps) => {
  const breadcrumbs = useMemo(() => {
    if (location?.pathname) {
      const breadcrumbPath: string[] = [];
      const paths = location.pathname.split('/');
      return paths?.map((path, idx) => {
        breadcrumbPath.push(path);
        return (
          <React.Fragment key={path}>
            {idx > 1 && <BreadcrumbSeparator />}
            <BreadcrumbLink to={breadcrumbPath.join('/')}>
              {startCase(getPathnameI18nText(breadcrumbPath.join('/')) || path)}
            </BreadcrumbLink>
          </React.Fragment>
        );
      });
    }
  }, [location?.pathname]);
  return <>{breadcrumbs}</>;
};

const getPathnameI18nText = (path: string) => {
  const tName = tn(path);
  return tName !== `UrlUtil.${path}` && tName;
};
