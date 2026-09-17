/**
 * Persistence model for the MVP.
 *
 * <p>{@code team_id} on tasks and standups is copied from the authenticated
 * user's team. It is never accepted from a client payload. Query methods that
 * omit {@code team_id} can leak cross-team rows; API layers must use the
 * team-scoped repository methods.
 */
package com.sethdevkh.restapi.domain;
