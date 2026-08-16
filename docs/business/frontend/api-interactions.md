# API Interactions

The frontend interacts with the backend APIs primarily via Axios and TanStack React Query.

## TanStack Query Configuration
- **Caching**: Queries are configured to *not* refetch on window focus by default (`refetchOnWindowFocus: false`).
- **Retries**: Automatic retries on query failures are disabled (`retry: false`).

## Axios Interceptors (Assumed / Standard Architecture)
- Interceptors typically handle injecting the `Authorization: Bearer <token>` header for requests if an access token exists.
- Refresh token logic is triggered implicitly during initialization, but 401 handling during normal usage may trigger a logout or token refresh depending on `authClient` configuration.

## Global Error Handling
A `GlobalApiErrorHandler` component is wrapped around the application routing. It listens for unhandled or critical API errors (e.g. 500, network issues) and surfaces them appropriately to the user (via toast or error boundary).
