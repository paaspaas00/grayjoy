const REGEX_API_CLIENT_ID = /get apiClientId\(\)\{return"([a-f0-9]{20})"\}/;
const REGEX_API_CLIENT_SECRET = /get apiClientSecret\(\)\{return"([a-f0-9]{40})"\}/;
const REGEX_APP_JS_URL = /static\/app\.[a-f0-9]+\.js/;
const REGEX_API_ENDPOINT = /API_ENDPOINT:\s*'(https:\/\/[^']+)'/;

const USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0';

const commonHeaders = {
  'User-Agent': USER_AGENT,
  Accept: '*/*',
  'Accept-Language': 'en-GB,en;q=0.5',
  Origin: 'https://www.dailymotion.com',
  DNT: '1',
  'Sec-GPC': '1',
  Connection: 'keep-alive',
  'Sec-Fetch-Dest': 'empty',
  'Sec-Fetch-Mode': 'cors',
  'Sec-Fetch-Site': 'same-site',
  'Cache-Control': 'no-cache',
};

// Extract API credentials and endpoint from Dailymotion's website
async function extractCredentials(): Promise<{ clientId: string; clientSecret: string; apiEndpoint: string }> {
  console.log('Fetching Dailymotion homepage...');
  const homepageResponse = await fetch('https://www.dailymotion.com', {
    headers: { 'User-Agent': USER_AGENT },
  });

  if (!homepageResponse.ok) {
    throw new Error(`Failed to fetch homepage: ${homepageResponse.status}`);
  }

  const homepageHtml = await homepageResponse.text();

  // Extract API endpoint from homepage
  const apiEndpointMatch = homepageHtml.match(REGEX_API_ENDPOINT);
  const apiEndpoint = apiEndpointMatch ? apiEndpointMatch[1] : 'https://graphql.api.dailymotion.com';
  console.log(`Extracted API endpoint: ${apiEndpoint}`);

  // Find the app.js URL
  const appJsMatch = homepageHtml.match(REGEX_APP_JS_URL);
  if (!appJsMatch) {
    throw new Error('Could not find app.js URL in homepage');
  }

  const appJsUrl = `https://static.neon-ssr.dailymotion.com/neon-user-ssr/${appJsMatch[0]}`;
  console.log(`Fetching app.js from ${appJsUrl}...`);

  const appJsResponse = await fetch(appJsUrl, {
    headers: { 'User-Agent': USER_AGENT },
  });

  if (!appJsResponse.ok) {
    throw new Error(`Failed to fetch app.js: ${appJsResponse.status}`);
  }

  const appJsContent = await appJsResponse.text();

  // Extract credentials
  const clientIdMatch = appJsContent.match(REGEX_API_CLIENT_ID);
  const clientSecretMatch = appJsContent.match(REGEX_API_CLIENT_SECRET);

  if (!clientIdMatch || !clientSecretMatch) {
    throw new Error('Could not extract API credentials from app.js');
  }

  const clientId = clientIdMatch[1];
  const clientSecret = clientSecretMatch[1];

  console.log(`Extracted client_id: ${clientId}`);
  console.log(`Extracted client_secret: ${clientSecret.substring(0, 8)}...`);

  return { clientId, clientSecret, apiEndpoint };
}

// Function to fetch OAuth token
async function fetchToken(clientId: string, clientSecret: string, apiEndpoint: string): Promise<string> {
  const body = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    grant_type: 'client_credentials',
  });

  const tokenUrl = `${apiEndpoint}/oauth/token`;
  console.log(`Fetching token from ${tokenUrl}...`);

  const response = await fetch(tokenUrl, {
    method: 'POST',
    headers: {
      ...commonHeaders,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: body.toString(),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const data = await response.json();
  console.log('Token fetched successfully');
  return data.access_token;
}

// Main function to setup GraphQL Codegen config
async function setupCodegenConfig() {
  const { clientId, clientSecret, apiEndpoint } = await extractCredentials();
  const token = await fetchToken(clientId, clientSecret, apiEndpoint);

  console.log(`Using API endpoint for schema: ${apiEndpoint}`);

  const config = {
    overwrite: true,
    schema: {
      [apiEndpoint]: {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
          ...commonHeaders,
          'Accept-Encoding': 'gzip, deflate, br, zstd',
          Priority: 'u=4',
        },
      },
    },
    generates: {
      './types/CodeGenDailymotion.d.ts': {
        plugins: ['typescript'],
      },
    },
  };

  return config;
}

export default new Promise((resolve, reject) => {
  setupCodegenConfig()
    .then((config) => {
      resolve(config);
    })
    .catch((error) => {
      console.error('Failed to setup GraphQL Codegen config:', error);
      reject(error);
    });
});
