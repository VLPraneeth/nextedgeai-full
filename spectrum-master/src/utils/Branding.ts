const configuredHelpBaseUrl = process.env.REACT_APP_NEXTEDGE_HELP_BASE_URL?.replace(/\/$/, '');

export const nextEdgeHelpUrl = (articleSlug: string): string =>
  configuredHelpBaseUrl ? `${configuredHelpBaseUrl}/${articleSlug}` : window.location.origin;
