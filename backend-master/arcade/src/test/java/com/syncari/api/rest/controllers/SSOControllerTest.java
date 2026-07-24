package com.syncari.api.rest.controllers;

import com.syncari.core.service.authz.AuthzService;
import com.syncari.api.core.util.SSOConfigTransformer;
import com.syncari.api.core.util.Util;
import com.syncari.api.rest.controllers.data.RedirectResponse;
import com.syncari.api.rest.controllers.data.SSOAuthConfigDTO;
import com.syncari.api.rest.controllers.exceptions.UnauthorizedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Organization;
import com.syncari.core.model.SSOAuthConfig;
import com.syncari.core.model.SSOAuthProvider;
import com.syncari.core.model.User;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.security.Permissions;
import com.syncari.core.service.SubscriptionService;
import com.syncari.core.service.UserService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SSOControllerTest extends AbstractSyncariTest {

    @Autowired
    SSOController ssoController;

    @Autowired
    SubscriptionService subService;

    @Autowired
    OrganizationRepo organizationRepo;

    @Autowired
    SSOConfigTransformer ssoConfigTransformer;

    @Value("${saml.x509.key}")
    private String x509Key;

    private static final String VALID_ENCODED_SAML_RESP = "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz48c2FtbDJwOlJlc3BvbnNlIERlc3RpbmF0aW9uPSJodHRwOi8vbG9jYWxob3N0OjgwODAvYXBpL3YxL3Nzby9zYW1sLzVmODc2OTIzNzI0ZTZjMTFhYTY1NzMzZSIgSUQ9ImlkNTEzODM0ODYxNjI5MjY3MjE2NTIwNTQxMDYiIElzc3VlSW5zdGFudD0iMjAyMC0xMC0xNlQyMTowODoyOC45MDFaIiBWZXJzaW9uPSIyLjAiIHhtbG5zOnNhbWwycD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOnByb3RvY29sIiB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiPjxzYW1sMjpJc3N1ZXIgRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6bmFtZWlkLWZvcm1hdDplbnRpdHkiIHhtbG5zOnNhbWwyPSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXNzZXJ0aW9uIj5odHRwOi8vd3d3Lm9rdGEuY29tL2V4azc4M2prN2hUdU5iZTFXNWQ1PC9zYW1sMjpJc3N1ZXI+PGRzOlNpZ25hdHVyZSB4bWxuczpkcz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnIyI+PGRzOlNpZ25lZEluZm8+PGRzOkNhbm9uaWNhbGl6YXRpb25NZXRob2QgQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxLzEwL3htbC1leGMtYzE0biMiLz48ZHM6U2lnbmF0dXJlTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8wNC94bWxkc2lnLW1vcmUjcnNhLXNoYTI1NiIvPjxkczpSZWZlcmVuY2UgVVJJPSIjaWQ1MTM4MzQ4NjE2MjkyNjcyMTY1MjA1NDEwNiI+PGRzOlRyYW5zZm9ybXM+PGRzOlRyYW5zZm9ybSBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNlbnZlbG9wZWQtc2lnbmF0dXJlIi8+PGRzOlRyYW5zZm9ybSBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMTAveG1sLWV4Yy1jMTRuIyI+PGVjOkluY2x1c2l2ZU5hbWVzcGFjZXMgUHJlZml4TGlzdD0ieHMiIHhtbG5zOmVjPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxLzEwL3htbC1leGMtYzE0biMiLz48L2RzOlRyYW5zZm9ybT48L2RzOlRyYW5zZm9ybXM+PGRzOkRpZ2VzdE1ldGhvZCBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMDQveG1sZW5jI3NoYTI1NiIvPjxkczpEaWdlc3RWYWx1ZT5uNlBydW9oaSsxT2Q0NnI3eHI2L3ZuUU1FcUhmc1pXV3didjNSekVRSjdvPTwvZHM6RGlnZXN0VmFsdWU+PC9kczpSZWZlcmVuY2U+PC9kczpTaWduZWRJbmZvPjxkczpTaWduYXR1cmVWYWx1ZT5WNmtCeG5vQmY4THYrZDdZLys0akdtVVRqcExxNXJwbHlMdHZxeHJoVTJqeklCOUsxOFJjdWZmbnpPRzQ4aDN2UDdRVVJkaWpQR1hNelcyOXFNMjhHaDA2aEYrRnhodXhOOW85ZjV2ZUlZS3BsSkRWMm9xNWNYY05hRTlSa0YzQmRnUmY5TXNRRWZlNlV1NHhva2ovWk5pS2wzb2tpUXJLTk44RXJrY1RscXhFOUVGUkZrTjc3RGVXRGx6NkYwZnZCNm9RSlpNOU5BRExadDJiZENLWVB5YkJQTXdNZEk3WDVJQnNlRFNnVFQvQW9RUTFHeWhLcitheWRGcERKSW5BMVhNQUhTSWVLbTRGNGM4U2lvK2FsazdaUGFlVlp5RnY4YllyY0Q1dVpSS1BXLzdlRlhVWFozVm5ENVV3cW14SnpreUEvcUt6MVg4ZmgrdHBJeU9tTHc9PTwvZHM6U2lnbmF0dXJlVmFsdWU+PGRzOktleUluZm8+PGRzOlg1MDlEYXRhPjxkczpYNTA5Q2VydGlmaWNhdGU+TUlJRHBEQ0NBb3lnQXdJQkFnSUdBWFQvd1dub01BMEdDU3FHU0liM0RRRUJDd1VBTUlHU01Rc3dDUVlEVlFRR0V3SlZVekVUTUJFRwpBMVVFQ0F3S1EyRnNhV1p2Y201cFlURVdNQlFHQTFVRUJ3d05VMkZ1SUVaeVlXNWphWE5qYnpFTk1Bc0dBMVVFQ2d3RVQydDBZVEVVCk1CSUdBMVVFQ3d3TFUxTlBVSEp2ZG1sa1pYSXhFekFSQmdOVkJBTU1DbVJsZGkwM01qQXlNelF4SERBYUJna3Foa2lHOXcwQkNRRVcKRFdsdVptOUFiMnQwWVM1amIyMHdIaGNOTWpBeE1EQTJNakV4TURNNVdoY05NekF4TURBMk1qRXhNVE01V2pDQmtqRUxNQWtHQTFVRQpCaE1DVlZNeEV6QVJCZ05WQkFnTUNrTmhiR2xtYjNKdWFXRXhGakFVQmdOVkJBY01EVk5oYmlCR2NtRnVZMmx6WTI4eERUQUxCZ05WCkJBb01CRTlyZEdFeEZEQVNCZ05WQkFzTUMxTlRUMUJ5YjNacFpHVnlNUk13RVFZRFZRUUREQXBrWlhZdE56SXdNak0wTVJ3d0dnWUoKS29aSWh2Y05BUWtCRmcxcGJtWnZRRzlyZEdFdVkyOXRNSUlCSWpBTkJna3Foa2lHOXcwQkFRRUZBQU9DQVE4QU1JSUJDZ0tDQVFFQQpwVXJJSTZSbEF6eXJUSnRPNFROWDgrN3dPOGZuQ3FGMEt1Y2tDb2JBSUhFaVZOTG96SXMxZnh1VmNHTWRzTXlTRjV5UDlTbVJVMFFjCm1GbGI5VFBPS04yUjhNTFhaQnpLTmx4RVZtMjN2SXkwc2l2M3pmc0lEdkF5NDU5ejhWMVNqQmtMNFFxRUR2MGEvRW1ieFptV1RxclgKTHZnTGtFTzVUVmNRWGtsY21GTFl6QmVudXpPdHQ2T1BPQXFRaTZwR0hLMnl5WWJzMk13bGQ1a1AzdWZsQ2ZoSXJhclRDOEhKdkhsVAorZ0hEQTdpdGxPV1R1ZXNvelZ6TGRmYjFrZkE4MjlucVV1OGhlYjB0NW5pT1h6ME1TK2NBOFJuN3Q4dzNESGNCZHZyS1lIcmp1YmJBClp3YzRjUXBJSWxSNmdNV3FRR2FLaUw5WE9vV3liR1lVZTRyNUpRSURBUUFCTUEwR0NTcUdTSWIzRFFFQkN3VUFBNElCQVFBMFFaTlEKcFM3cko2WmUxUDN4eEFRb0tNNGltRms3MGhBMXgxdmhqRm9BZjkzaWdWQ2pYVXFGS3hrU0xLdmlNUVp4NEFJQ2RaMmtTckpTOFRSbApTa2drd3lSMUlHUTNEOFdvSlVUQldsUUxPcndtUFNmSlRjaTlIZUIwYno1Y2NhR3BteUhZaXhFenRZZVRVWE1qZUJzVmVYbk1SOTIrCm5uN0hWcHdJWGkvSnpwbks3cU5TYjFaZkxMSGZkWC9mUW5Ra3dGaFdSN3JoV01ZYzBkS0luTVg2L3Z5TVBuVkxpdjU1R3FWa3FaZDEKWU9OdUtxMStVbWNsNVBFcEJ4em94a0dVdjJiMnlyZDJmOTNWUk1CQ01IalJFWVVMWHhCT09QY0lBdGFlVmEwaDBHWndjUkJnVWs0TQpXU255dmdpcXNPZEErbysxeXhOL2ZMeE5YTnhZWTRJTDwvZHM6WDUwOUNlcnRpZmljYXRlPjwvZHM6WDUwOURhdGE+PC9kczpLZXlJbmZvPjwvZHM6U2lnbmF0dXJlPjxzYW1sMnA6U3RhdHVzIHhtbG5zOnNhbWwycD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOnByb3RvY29sIj48c2FtbDJwOlN0YXR1c0NvZGUgVmFsdWU9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDpzdGF0dXM6U3VjY2VzcyIvPjwvc2FtbDJwOlN0YXR1cz48c2FtbDI6QXNzZXJ0aW9uIElEPSJpZDUxMzgzNDg2MTYzNzExMjg4MTE3NjkwNjciIElzc3VlSW5zdGFudD0iMjAyMC0xMC0xNlQyMTowODoyOC45MDFaIiBWZXJzaW9uPSIyLjAiIHhtbG5zOnNhbWwyPSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXNzZXJ0aW9uIiB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiPjxzYW1sMjpJc3N1ZXIgRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6bmFtZWlkLWZvcm1hdDplbnRpdHkiIHhtbG5zOnNhbWwyPSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXNzZXJ0aW9uIj5odHRwOi8vd3d3Lm9rdGEuY29tL2V4azc4M2prN2hUdU5iZTFXNWQ1PC9zYW1sMjpJc3N1ZXI+PGRzOlNpZ25hdHVyZSB4bWxuczpkcz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnIyI+PGRzOlNpZ25lZEluZm8+PGRzOkNhbm9uaWNhbGl6YXRpb25NZXRob2QgQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxLzEwL3htbC1leGMtYzE0biMiLz48ZHM6U2lnbmF0dXJlTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8wNC94bWxkc2lnLW1vcmUjcnNhLXNoYTI1NiIvPjxkczpSZWZlcmVuY2UgVVJJPSIjaWQ1MTM4MzQ4NjE2MzcxMTI4ODExNzY5MDY3Ij48ZHM6VHJhbnNmb3Jtcz48ZHM6VHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI2VudmVsb3BlZC1zaWduYXR1cmUiLz48ZHM6VHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8xMC94bWwtZXhjLWMxNG4jIj48ZWM6SW5jbHVzaXZlTmFtZXNwYWNlcyBQcmVmaXhMaXN0PSJ4cyIgeG1sbnM6ZWM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMTAveG1sLWV4Yy1jMTRuIyIvPjwvZHM6VHJhbnNmb3JtPjwvZHM6VHJhbnNmb3Jtcz48ZHM6RGlnZXN0TWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8wNC94bWxlbmMjc2hhMjU2Ii8+PGRzOkRpZ2VzdFZhbHVlPnZWL3RFRXlPMDBiUCtjdlRjanQvOHNzS09JNUJnS2I2ZnhKUUNPSm9JdjQ9PC9kczpEaWdlc3RWYWx1ZT48L2RzOlJlZmVyZW5jZT48L2RzOlNpZ25lZEluZm8+PGRzOlNpZ25hdHVyZVZhbHVlPmsxMkdiZVkwSkpLcUs2eGI2bnpadWN2UzFEQUV4Sk9UeGFFSUd0SWxYRGNSY2hZbzdQTWg1MFQzTHBNbDgxakp4bG5IVjRobURGNVZXd3p6L3VOVXlEYTlqdW5JMklPNXJmQjNRRmZUMEJNaXJHQzN6bmhRZzJibXJIa1ZtSkVBUWtjZE1EWkM2b0ZKWW9RbTdxeG1jWXY0OU1uRjlnSjYybFc3TGkwMVIxTVBkdXlWdEltT3BNamJkNG9DTVhZNGJKTHJLTHE1TkpaakFuakt4cmc1c3N1U0tsR2hSOG8rWWdQWm1BOGlSUHJaYzE2TVIwZ1BMQnFUZ04rakhCMkxzSmhkcmQ3cXlKTS9GWEtuSUU2YTFQNHFHOGJXZ2gvanBGVVVvR1V0a0d4UUd0Z3NGTlBkcW9FWUJQUmlCTDdqbkZLUmRyV3lRak00aUpVSy9UQUI0dz09PC9kczpTaWduYXR1cmVWYWx1ZT48ZHM6S2V5SW5mbz48ZHM6WDUwOURhdGE+PGRzOlg1MDlDZXJ0aWZpY2F0ZT5NSUlEcERDQ0FveWdBd0lCQWdJR0FYVC93V25vTUEwR0NTcUdTSWIzRFFFQkN3VUFNSUdTTVFzd0NRWURWUVFHRXdKVlV6RVRNQkVHCkExVUVDQXdLUTJGc2FXWnZjbTVwWVRFV01CUUdBMVVFQnd3TlUyRnVJRVp5WVc1amFYTmpiekVOTUFzR0ExVUVDZ3dFVDJ0MFlURVUKTUJJR0ExVUVDd3dMVTFOUFVISnZkbWxrWlhJeEV6QVJCZ05WQkFNTUNtUmxkaTAzTWpBeU16UXhIREFhQmdrcWhraUc5dzBCQ1FFVwpEV2x1Wm05QWIydDBZUzVqYjIwd0hoY05NakF4TURBMk1qRXhNRE01V2hjTk16QXhNREEyTWpFeE1UTTVXakNCa2pFTE1Ba0dBMVVFCkJoTUNWVk14RXpBUkJnTlZCQWdNQ2tOaGJHbG1iM0p1YVdFeEZqQVVCZ05WQkFjTURWTmhiaUJHY21GdVkybHpZMjh4RFRBTEJnTlYKQkFvTUJFOXJkR0V4RkRBU0JnTlZCQXNNQzFOVFQxQnliM1pwWkdWeU1STXdFUVlEVlFRRERBcGtaWFl0TnpJd01qTTBNUnd3R2dZSgpLb1pJaHZjTkFRa0JGZzFwYm1adlFHOXJkR0V1WTI5dE1JSUJJakFOQmdrcWhraUc5dzBCQVFFRkFBT0NBUThBTUlJQkNnS0NBUUVBCnBVcklJNlJsQXp5clRKdE80VE5YOCs3d084Zm5DcUYwS3Vja0NvYkFJSEVpVk5Mb3pJczFmeHVWY0dNZHNNeVNGNXlQOVNtUlUwUWMKbUZsYjlUUE9LTjJSOE1MWFpCektObHhFVm0yM3ZJeTBzaXYzemZzSUR2QXk0NTl6OFYxU2pCa0w0UXFFRHYwYS9FbWJ4Wm1XVHFyWApMdmdMa0VPNVRWY1FYa2xjbUZMWXpCZW51ek90dDZPUE9BcVFpNnBHSEsyeXlZYnMyTXdsZDVrUDN1ZmxDZmhJcmFyVEM4SEp2SGxUCitnSERBN2l0bE9XVHVlc296VnpMZGZiMWtmQTgyOW5xVXU4aGViMHQ1bmlPWHowTVMrY0E4Um43dDh3M0RIY0JkdnJLWUhyanViYkEKWndjNGNRcElJbFI2Z01XcVFHYUtpTDlYT29XeWJHWVVlNHI1SlFJREFRQUJNQTBHQ1NxR1NJYjNEUUVCQ3dVQUE0SUJBUUEwUVpOUQpwUzdySjZaZTFQM3h4QVFvS000aW1GazcwaEExeDF2aGpGb0FmOTNpZ1ZDalhVcUZLeGtTTEt2aU1RWng0QUlDZFoya1NySlM4VFJsClNrZ2t3eVIxSUdRM0Q4V29KVVRCV2xRTE9yd21QU2ZKVGNpOUhlQjBiejVjY2FHcG15SFlpeEV6dFllVFVYTWplQnNWZVhuTVI5MisKbm43SFZwd0lYaS9KenBuSzdxTlNiMVpmTExIZmRYL2ZRblFrd0ZoV1I3cmhXTVljMGRLSW5NWDYvdnlNUG5WTGl2NTVHcVZrcVpkMQpZT051S3ExK1VtY2w1UEVwQnh6b3hrR1V2MmIyeXJkMmY5M1ZSTUJDTUhqUkVZVUxYeEJPT1BjSUF0YWVWYTBoMEdad2NSQmdVazRNCldTbnl2Z2lxc09kQStvKzF5eE4vZkx4TlhOeFlZNElMPC9kczpYNTA5Q2VydGlmaWNhdGU+PC9kczpYNTA5RGF0YT48L2RzOktleUluZm8+PC9kczpTaWduYXR1cmU+PHNhbWwyOlN1YmplY3QgeG1sbnM6c2FtbDI9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphc3NlcnRpb24iPjxzYW1sMjpOYW1lSUQgRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoxLjE6bmFtZWlkLWZvcm1hdDplbWFpbEFkZHJlc3MiPmFiaGluYXZAc3luY2FyaS5jb208L3NhbWwyOk5hbWVJRD48c2FtbDI6U3ViamVjdENvbmZpcm1hdGlvbiBNZXRob2Q9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDpjbTpiZWFyZXIiPjxzYW1sMjpTdWJqZWN0Q29uZmlybWF0aW9uRGF0YSBOb3RPbk9yQWZ0ZXI9IjIwMjAtMTAtMTZUMjE6MTM6MjguOTAyWiIgUmVjaXBpZW50PSJodHRwOi8vbG9jYWxob3N0OjgwODAvYXBpL3YxL3Nzby9zYW1sLzVmODc2OTIzNzI0ZTZjMTFhYTY1NzMzZSIvPjwvc2FtbDI6U3ViamVjdENvbmZpcm1hdGlvbj48L3NhbWwyOlN1YmplY3Q+PHNhbWwyOkNvbmRpdGlvbnMgTm90QmVmb3JlPSIyMDIwLTEwLTE2VDIxOjAzOjI4LjkwMloiIE5vdE9uT3JBZnRlcj0iMjAyMC0xMC0xNlQyMToxMzoyOC45MDJaIiB4bWxuczpzYW1sMj0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmFzc2VydGlvbiI+PHNhbWwyOkF1ZGllbmNlUmVzdHJpY3Rpb24+PHNhbWwyOkF1ZGllbmNlPmh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9hcGkvdjEvc3NvL3NhbWwvNWY4NzY5MjM3MjRlNmMxMWFhNjU3MzNlPC9zYW1sMjpBdWRpZW5jZT48L3NhbWwyOkF1ZGllbmNlUmVzdHJpY3Rpb24+PC9zYW1sMjpDb25kaXRpb25zPjxzYW1sMjpBdXRoblN0YXRlbWVudCBBdXRobkluc3RhbnQ9IjIwMjAtMTAtMTZUMjE6MDg6MjguOTAxWiIgU2Vzc2lvbkluZGV4PSJpZDE2MDI4ODI1MDg5MDEuMTk5MzM0MjE0MSIgeG1sbnM6c2FtbDI9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphc3NlcnRpb24iPjxzYW1sMjpBdXRobkNvbnRleHQ+PHNhbWwyOkF1dGhuQ29udGV4dENsYXNzUmVmPnVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphYzpjbGFzc2VzOlBhc3N3b3JkUHJvdGVjdGVkVHJhbnNwb3J0PC9zYW1sMjpBdXRobkNvbnRleHRDbGFzc1JlZj48L3NhbWwyOkF1dGhuQ29udGV4dD48L3NhbWwyOkF1dGhuU3RhdGVtZW50PjxzYW1sMjpBdHRyaWJ1dGVTdGF0ZW1lbnQgeG1sbnM6c2FtbDI9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphc3NlcnRpb24iPjxzYW1sMjpBdHRyaWJ1dGUgTmFtZT0iZmlyc3ROYW1lIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDI6QXR0cmlidXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4bWxuczp4c2k9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hLWluc3RhbmNlIiB4c2k6dHlwZT0ieHM6c3RyaW5nIj5BYmhpbmF2PC9zYW1sMjpBdHRyaWJ1dGVWYWx1ZT48L3NhbWwyOkF0dHJpYnV0ZT48c2FtbDI6QXR0cmlidXRlIE5hbWU9Imxhc3ROYW1lIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDI6QXR0cmlidXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4bWxuczp4c2k9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hLWluc3RhbmNlIiB4c2k6dHlwZT0ieHM6c3RyaW5nIj5NYXVyeWE8L3NhbWwyOkF0dHJpYnV0ZVZhbHVlPjwvc2FtbDI6QXR0cmlidXRlPjxzYW1sMjpBdHRyaWJ1dGUgTmFtZT0iZW1haWwiIE5hbWVGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1sMjpBdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiIHhtbG5zOnhzaT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEtaW5zdGFuY2UiIHhzaTp0eXBlPSJ4czpzdHJpbmciPmFiaGluYXZAc3luY2FyaS5jb208L3NhbWwyOkF0dHJpYnV0ZVZhbHVlPjwvc2FtbDI6QXR0cmlidXRlPjwvc2FtbDI6QXR0cmlidXRlU3RhdGVtZW50Pjwvc2FtbDI6QXNzZXJ0aW9uPjwvc2FtbDJwOlJlc3BvbnNlPg==";

    @Test
    public void samlAuthentication_EmptySamlResponseString() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("SAMLResponse", "");

        HttpServletResponse response = new MockHttpServletResponse();
        try {
            ssoController.samlAuthentication("123", request, response);
            fail();
        } catch (UnauthorizedException e){
            assertEquals("User authentication failed. Please check with account admin to access Syncari", e.getMessage());
        }
    }

    @Test
    public void samlAuthentication_InvalidOrg() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("SAMLResponse", VALID_ENCODED_SAML_RESP);

        HttpServletResponse response = new MockHttpServletResponse();
        try {
            ssoController.samlAuthentication("123", request, response);
            fail();
        } catch (UnauthorizedException e){
            assertEquals("User authentication failed. Please check with account admin to access Syncari", e.getMessage());
        }
    }

    @Test
    public void samlAuthentication_SSONotEnabled() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("SAMLResponse", VALID_ENCODED_SAML_RESP);

        Organization org = SyncariContext.getOrganziation();
        HttpServletResponse response = new MockHttpServletResponse();
        try{
            ssoController.samlAuthentication(org.getId(), request, response);
            fail();
        } catch (UnauthorizedException e){
            assertEquals("User authentication failed. Please check with account admin to access Syncari", e.getMessage());
        }
    }

    @Test
    public void samlAuthentication_UserNotFound() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("SAMLResponse", VALID_ENCODED_SAML_RESP);
        Organization org = SyncariContext.getOrganziation();

        try {
            enableSSOInOrg(org);
            HttpServletResponse response = new MockHttpServletResponse();
            ssoController.samlAuthentication(org.getId(), request, response);
            fail();
        } catch (UnauthorizedException e){
            assertEquals("User authentication failed. Please check with account admin to access Syncari", e.getMessage());
        } finally {
            disableSSOInOrg(org);
        }
    }

    @Test
    public void samlAuthentication_Valid() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("SAMLResponse", VALID_ENCODED_SAML_RESP);
        Organization org = SyncariContext.getOrganziation();

        try {
            User user = new User();
            user.setEmail("abhinav@syncari.com");
            user.setId("testid");
            user.setCurrentInstanceId(org.getInstances().get(0).getSyncariId());
            user.setAvailableInstances(org.getInstances().stream().map(i -> i.getSyncariId()).collect(Collectors.toSet()));
            user.setStatus(Status.ACTIVE);
            UserService mockUserService = mock(UserService.class);
            doReturn(Optional.of(user)).when(mockUserService).findActiveUserByEmail("abhinav@syncari.com");
            ssoController.setUserService(mockUserService);
            ssoController.userService = mockUserService;

            AuthzService mockAuthzService = mock(AuthzService.class);
            doReturn(Permissions.adminPermissions().stream()).when(mockAuthzService).listPrivileges("abhinav@syncari.com");
            ssoController.setAuthzService(mockAuthzService);

            enableSSOInOrg(org);
            HttpServletResponse response = new MockHttpServletResponse();
            ssoController.samlAuthentication(org.getId(), request, response);

            assertEquals(HttpServletResponse.SC_OK, response.getStatus());
            assertNotNull(response.getHeader("authorization"));

            verify(mockAuthzService).listPrivileges("abhinav@syncari.com");
            verify(mockUserService).findActiveUserByEmail("abhinav@syncari.com");
        } finally {
            disableSSOInOrg(org);
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_SSO})
    public void updateSSOConfig_InvalidOrg(){
        try {
            ssoController.updateSSOConfig("123", null);
            fail();
        } catch (SyncariValidationException e){
            assertEquals("Invalid Org", e.getMessage());
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_SSO})
    public void updateSSOConfig(){
        Organization org = SyncariContext.getOrganziation();
        try {
            SSOAuthConfigDTO ssoAuthConfig = new SSOAuthConfigDTO().setSsoUrl("http://some_url").setEntityId("http://entityId")
                    .setProvider("OKTA").setCertificate(x509Key);

            SSOAuthConfigDTO updated = ssoController.updateSSOConfig(org.getId(), ssoAuthConfig);
            assertNotNull(updated);
            assertEquals(updated.getCertificate(), ssoAuthConfig.getCertificate());
            assertEquals(updated.getProvider(), ssoAuthConfig.getProvider());
            assertEquals(updated.getSsoUrl(), ssoAuthConfig.getSsoUrl());
            assertEquals(updated.getEntityId(), ssoAuthConfig.getEntityId());
        } finally {
            disableSSOInOrg(org);
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_SSO})
    public void getSSOConfig(){
        Organization org = SyncariContext.getOrganziation();

        try {
            SSOAuthConfigDTO retrieved = ssoController.getSSOConfig(org.getId());
            assertNull(retrieved);

            org = enableSSOInOrg(org);
            retrieved = ssoController.getSSOConfig(org.getId());
            assertNotNull(retrieved);
            SSOAuthConfigDTO orgSSOConfig = ssoConfigTransformer.toSSOAuthConfigDTO(org.getSsoConfig());
            assertEquals(retrieved.getCertificate(), orgSSOConfig.getCertificate());
            assertEquals(retrieved.getProvider(), orgSSOConfig.getProvider());
            assertEquals(retrieved.getSsoUrl(), orgSSOConfig.getSsoUrl());
            assertEquals(retrieved.getEntityId(), orgSSOConfig.getEntityId());
        } finally {
            disableSSOInOrg(org);
        }
    }
    
    @Test
    @WithMockUser(username = "test@email.com")
    public void zendeskJwt() throws IOException {
        HttpServletResponse response = new MockHttpServletResponse();
        ssoController.zendeskSso(response, "https://syncari.zendesk.com/tickets/123");
        assertTrue(response.getHeader("Location").startsWith("https://syncari.zendesk.com/access/jwt?return_to="));
        assertTrue(response.getHeader("Location").endsWith("return_to=https://syncari.zendesk.com/tickets/123"));
        assertNotNull(response.getHeader("x-jwt-token"));
        assertTrue(response.getHeader("x-redirect-method").endsWith("POST"));


        ssoController.zendeskSso(response, "https://some.domain.com/tickets/123");
        assertTrue(response.getHeader("Location").startsWith("http://localhost:3000"));
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {WRITE_SSO, READ_SSO})
    public void disable(){
    	Organization org = SyncariContext.getOrganziation();
		SSOAuthConfigDTO ssoAuthConfig = new SSOAuthConfigDTO().setSsoUrl("http://some_url").setEntityId("http://entityId")
				.setProvider("OKTA").setCertificate(x509Key);
		
		SSOAuthConfigDTO updated = ssoController.updateSSOConfig(org.getId(), ssoAuthConfig);
		assertNotNull(updated);
		
		ssoController.disable(org.getId());
		assertNull(ssoController.getSSOConfig(org.getId()));
    }

    private Organization enableSSOInOrg(Organization org){
        SSOAuthConfig ssoAuthConfig = new SSOAuthConfig().setSsoUrl("http://some_url").setEntityId("http://entityId")
                .setProvider(SSOAuthProvider.OKTA).setX509Key(x509Key);
        ssoAuthConfig = subService.updateSSOForOrg(org, ssoAuthConfig);
        org.setSsoConfig(ssoAuthConfig);
        return org;
    }

    private void disableSSOInOrg(Organization org){
        org.setSsoConfig(null);
        organizationRepo.save(org);
    }
}
