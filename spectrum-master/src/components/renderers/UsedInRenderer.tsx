import { Link } from '@reach/router';

import Join from 'components/Join';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/StringUtil';

interface UsedInCellProps {
  references: LinkReference[];
}

export const UsedInCell = ({ references }: UsedInCellProps) => {
  return (
    <Join delimiter=", ">
      {references.map(({ displayText, route }) => {
        const { route: urlKey, ...routeParams } = route as LinkReference['route'];
        return (
          <Link key={urlKey} to={replaceToken(RouteConstants[urlKey], routeParams)}>
            {displayText}
          </Link>
        );
      })}
    </Join>
  );
};

interface LinkReference<P = Record<string, string>> {
  displayText: string;
  route: { route: keyof typeof RouteConstants } & P;
}

const UsedInRenderer = (usedIn: LinkReference[] | null) =>
  Array.isArray(usedIn) ? <UsedInCell references={usedIn} /> : null;

export default UsedInRenderer;
