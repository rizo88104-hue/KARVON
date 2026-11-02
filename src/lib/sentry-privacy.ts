// What Sentry may collect. Request bodies and query strings can carry phone numbers and SMS codes, cookies carry
// the session: none of them leave the server. Only harmless headers help to reproduce an error.
export const SENTRY_DATA_COLLECTION = {
  userInfo: false,
  cookies: false,
  httpHeaders: { request: { allow: ["user-agent", "accept-language", "x-request-id"] }, response: false },
  httpBodies: [],
  urlQueryParams: false,
};
