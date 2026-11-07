create function try_numeric(value text) returns numeric
    language plpgsql immutable strict parallel safe as
$$
begin
    return value::numeric;
exception
    when others then return null;
end
$$;

create function try_timestamptz(value text) returns timestamptz
    language plpgsql immutable strict parallel safe as
$$
begin
    return value::timestamptz;
exception
    when others then return null;
end
$$;
