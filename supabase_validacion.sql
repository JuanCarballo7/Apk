-- Ejecutar completo en Supabase Dashboard > SQL Editor, después de supabase_admin.sql.
-- Validación de los QR de las entradas en el ingreso al autódromo (solo superadmin).
-- Se puede volver a ejecutar sin perder datos.

alter table public.orders add column if not exists usada_at timestamptz;
alter table public.orders add column if not exists usada_por uuid references auth.users(id) on delete set null;

create index if not exists orders_codigo_idx on public.orders(codigo);

-- Valida una orden por su código (y el id que viaja en el QR, si está).
-- Con p_registrar = true además marca el ingreso; una orden solo se puede usar una vez.
-- resultado: VALIDA | YA_USADA | CANCELADA | PENDIENTE | NO_EXISTE
create or replace function public.validar_entrada(
    p_codigo text,
    p_orden_id bigint default null,
    p_registrar boolean default false
)
returns jsonb
language plpgsql
security definer set search_path = ''
as $fn$
declare
    v_orden public.orders%rowtype;
    v_resultado text;
    v_registrada boolean := false;
begin
    if not public.es_superadmin() then
        raise exception 'NO_AUTORIZADO' using errcode = '42501';
    end if;
    select o.* into v_orden
    from public.orders o
    where o.codigo = upper(trim(p_codigo))
      and (p_orden_id is null or o.id = p_orden_id)
    for update;
    if not found then
        return jsonb_build_object('resultado', 'NO_EXISTE', 'codigo', upper(trim(p_codigo)));
    end if;
    v_resultado := case
        when v_orden.estado = 'cancelada' then 'CANCELADA'
        when v_orden.estado = 'pendiente' then 'PENDIENTE'
        when v_orden.usada_at is not null then 'YA_USADA'
        else 'VALIDA'
    end;
    if p_registrar and v_resultado = 'VALIDA' then
        update public.orders o
        set usada_at = now(), usada_por = auth.uid()
        where o.id = v_orden.id
        returning o.* into v_orden;
        v_registrada := true;
    end if;
    return jsonb_build_object(
        'resultado', v_resultado,
        'registrada', v_registrada,
        'orden_id', v_orden.id,
        'codigo', v_orden.codigo,
        'total', v_orden.total,
        'created_at', v_orden.created_at,
        'usada_at', v_orden.usada_at,
        'comprador', (
            select trim(p.first_name || ' ' || p.last_name)
            from public.profiles p where p.id = v_orden.user_id
        ),
        'email', (select u.email::text from auth.users u where u.id = v_orden.user_id),
        'items', coalesce((
            select jsonb_agg(jsonb_build_object('nombre', i.nombre, 'cantidad', i.cantidad) order by i.id)
            from public.order_items i where i.order_id = v_orden.id
        ), '[]'::jsonb)
    );
end;
$fn$;

revoke execute on function public.validar_entrada(text, bigint, boolean) from public, anon;
grant execute on function public.validar_entrada(text, bigint, boolean) to authenticated;

-- Recarga el caché de la API para que la app vea la función enseguida.
notify pgrst, 'reload schema';
