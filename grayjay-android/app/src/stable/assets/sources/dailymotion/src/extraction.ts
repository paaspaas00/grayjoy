import { AnonymousUserAuthorization } from '../types/types';
import {
  BASE_URL,
  BASE_URL_API,
  createAuthRegexByTextLength,
  REGEX_INITIAL_DATA_API_AUTH_1,
  REGEX_API_CLIENT_ID,
  REGEX_API_CLIENT_SECRET,
  REGEX_APP_JS_URL,
  REGEX_API_ENDPOINT,
  USER_AGENT,
} from './constants';
import { objectToUrlEncodedString, generateUUIDv4 } from './util';

export function oauthClientCredentialsRequest(
  httpClient: IHttp,
  url: string,
  clientId: string,
  secret: string,
  visitorId: string,
  throwOnInvalid = false,
): HttpResponse {
  if (!httpClient || !url || !clientId || !secret) {
    throw new ScriptException(
      'Invalid parameters provided to oauthClientCredentialsRequest',
    );
  }

  const body = objectToUrlEncodedString({
    client_id: clientId,
    client_secret: secret,
    grant_type: 'client_credentials',
    visitor_id: visitorId
  });

  try {
    return httpClient.POST(
      url,
      body,
      {
        'User-Agent': USER_AGENT,
        'Content-Type': 'application/x-www-form-urlencoded',
        Origin: BASE_URL,
        DNT: '1',
        'Sec-GPC': '1',
        Connection: 'keep-alive',
        'Sec-Fetch-Dest': 'empty',
        'Sec-Fetch-Mode': 'cors',
        'Sec-Fetch-Site': 'same-site',
        Priority: 'u=4',
        Pragma: 'no-cache',
        'Cache-Control': 'no-cache',
      },
      false,
    );
  } catch (error) {
    log('OAuth request exception: ' + (error instanceof Error ? error.message : String(error)));
    if (throwOnInvalid) {
      throw new ScriptException('Failed to obtain OAuth client credentials');
    }
    return null;
  }
}

export function extractClientCredentials(detailsRequestHtml, httpClient?: IHttp) {

  const result = [];

  // Try new regex patterns on homepage first (credentials might be inlined)
  let clientIdMatch = detailsRequestHtml.body.match(REGEX_API_CLIENT_ID);
  let clientSecretMatch = detailsRequestHtml.body.match(REGEX_API_CLIENT_SECRET);

  if (clientIdMatch && clientSecretMatch && clientIdMatch[1] && clientSecretMatch[1]) {
    result.unshift({
      clientId: clientIdMatch[1],
      secret: clientSecretMatch[1],
    });
    log('Successfully extracted API credentials from homepage');
    return result;
  }

  // Credentials not in homepage HTML - fetch app.js where they are typically located
  if (httpClient) {
    const appJsMatch = detailsRequestHtml.body.match(REGEX_APP_JS_URL);
    if (appJsMatch) {
      const appJsUrl = `https://static.neon-ssr.dailymotion.com/neon-user-ssr/${appJsMatch[0]}`;
      log(`Fetching app.js from ${appJsUrl}`);

      try {
        const appJsResponse = httpClient.GET(appJsUrl, {
          'User-Agent': USER_AGENT,
        }, false);

        if (appJsResponse?.isOk) {
          clientIdMatch = appJsResponse.body.match(REGEX_API_CLIENT_ID);
          clientSecretMatch = appJsResponse.body.match(REGEX_API_CLIENT_SECRET);

          if (clientIdMatch && clientSecretMatch && clientIdMatch[1] && clientSecretMatch[1]) {
            result.unshift({
              clientId: clientIdMatch[1],
              secret: clientSecretMatch[1],
            });
            log('Successfully extracted API credentials from app.js');
            return result;
          } else {
            log('Credentials not found in app.js content');
          }
        } else {
          log(`Failed to fetch app.js: ${appJsResponse?.code}`);
        }
      } catch (error) {
        log(`Error fetching app.js: ${error}`);
      }
    } else {
      log('Could not find app.js URL in homepage');
    }
  }

  // Fallback to old regex pattern on homepage
  const match = detailsRequestHtml.body.match(REGEX_INITIAL_DATA_API_AUTH_1);

  if (match?.length === 2 && match[0] && match[1]) {
    result.unshift({
      clientId: match[0],
      secret: match[1],
    });
    log('Successfully extracted API credentials using old regex pattern');
  } else {
    log('Failed to extract API credentials using regex. Trying DOM parsing.');

    const htmlElement = domParser.parseFromString(
      detailsRequestHtml.body,
      'text/html',
    );
    const extractedId = getScriptVariableByTextLength(htmlElement, 20);
    const extractedSecret = getScriptVariableByTextLength(htmlElement, 40);

    if (extractedId && extractedSecret) {
      result.unshift({
        clientId: extractedId,
        secret: extractedSecret,
      });

      log(`Successfully extracted API credentials using DOM parsing`);
    } else {
      log('Failed to extract API credentials using all methods');
    }
  }

  return result;
}

export function getScriptVariableByTextLength(htmlElement, length: number) {
  const scriptTags = htmlElement.querySelectorAll(
    'script[type="text/javascript"]',
  );

  if (!scriptTags.length) {
    console.error('No script tags found.');
    return null; // or throw an error, depending on your use case
  }

  let pageContent = '';

  scriptTags.forEach((tag) => {
    pageContent += tag.outerHTML;
  });

  let matches = createAuthRegexByTextLength(length).exec(pageContent);

  if (matches?.length == 2) {
    return matches[1];
  }
}

export function extractApiEndpoint(homepageHtml: string): string {
  const match = homepageHtml.match(REGEX_API_ENDPOINT);
  if (match && match[1]) {
    log(`Extracted API endpoint: ${match[1]}`);
    return match[1];
  }
  log(`Could not extract API endpoint from homepage, using fallback: ${BASE_URL_API}`);
  return BASE_URL_API;
}

export function getTokenFromClientCredentials(
  httpClient: IHttp,
  credentials,
  visitorId: string,
  authUrl?: string,
  throwOnInvalid = false,
) {
  let result: AnonymousUserAuthorization = {
    isValid: false,
  };

  const tokenUrl = authUrl || `${BASE_URL_API}/oauth/token`;

  for (const credential of credentials) {
    const res = oauthClientCredentialsRequest(
      httpClient,
      tokenUrl,
      credential.clientId,
      credential.secret,
      visitorId,
    );

    if (res?.isOk) {
      const anonymousTokenResponse = JSON.parse(res.body);

      if (
        !anonymousTokenResponse.token_type ||
        !anonymousTokenResponse.access_token
      ) {
        log('Invalid token response body: ' + res.body);
        if (throwOnInvalid) {
          throw new ScriptException('', 'Invalid token response: ' + res.body);
        }
      }

      result = {
        anonymousUserAuthorizationToken: `${anonymousTokenResponse.token_type} ${anonymousTokenResponse.access_token}`,
        anonymousUserAuthorizationTokenExpirationDate:
          Date.now() + anonymousTokenResponse.expires_in * 1000,
        isValid: true,
      };

      break;
    } else {
      log(`Token request failed: code=${res?.code}, body=${res?.body?.substring(0, 200)}`);
    }
  }

  return result;
}
