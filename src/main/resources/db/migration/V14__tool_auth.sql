-- UNIVERSAL-OPERATOR-PLAN B.5: a tool that authenticates as an OAuth 2.0 client and gets its
-- own access token (client_credentials). Holds the token endpoint and the names of the
-- secrets with the client id and secret — never their values. NULL: the tool's headers and
-- address carry whatever authentication it needs, as before.
ALTER TABLE tool ADD COLUMN auth JSONB;
