-- Text to timestamptz input depends on the session time zone, so the function cannot be immutable.
alter function try_timestamptz(text) stable;
