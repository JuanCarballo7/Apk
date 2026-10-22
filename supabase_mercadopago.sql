-- Ejecutar completo en Supabase Dashboard > SQL Editor, después de supabase_saldo.sql.
-- Pago de entradas con Mercado Pago (Checkout Pro). El pago con saldo sigue funcionando.
-- Se puede volver a ejecutar sin perder datos. Si volvés a ejecutar supabase_admin.sql o
-- supabase_saldo.sql, ejecutá este archivo de nuevo (reemplaza crear_orden).
--
-- Flujo Mercado Pago:
--   1. La Edge Function mp-crear-pago llama a reservar_orden_mp: orden 'pendiente' con el stock reservado.
--   2. El usuario paga en Mercado Pago.
--   3. La Edge Function mp-webhook consulta el pago a Mercado Pago y llama a mp_confirmar_pago.
--   4. Las órdenes pendientes sin pago se cancelan solas a los 30 minutos y devuelven el stock.

-- ============================================================
-- COLUMNAS NUEVAS EN ÓRDENES
-- ============================================================
alter table public.orders add column if not exists metodo_pago text not null default 'saldo';
alter table public.orders drop constraint if exists orders_metodo_pago_check;
alter table public.orders add constraint orders_metodo_pago_check
    check (metodo_pago in ('saldo', 'mercadopago'));
alter table public.orders add column if not exists mp_preference_id text;
alter table public.orders add column if not exists mp_payment_id text;
alter table public.orders add column if not exists mp_estado text;
alter table public.orders add column if not exists pagado_at timestamptz;

create index if not exists orders_pendientes_mp_idx
    on public.orders(created_at) where estado = 'pendiente' and metodo_pago = 'mercadopago';

-- ============================================================
-- FUNCIONES INTERNAS (no se pueden llamar desde la app)
-- ============================================================

-- Carga los items de una orden con precio y stock de la base, descuenta el stock y devuelve el total.
create or replace function public._cargar_items_orden(p_orden bigint, p_items jsonb)
returns numeric
language plpgsql
security definer set search_path = ''
as $$
declare
    v_total numeric(14, 2) := 0;
    v_item record;
    v_ticket public.tickets%rowtype;
begin
    if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
        raise exception 'CARRITO_VACIO' using errcode = '22023';
    end if;

    for v_item in
        select x.ticket_id, x.cantidad
        from jsonb_to_recordset(p_items) as x(ticket_id text, cantidad integer)
    loop
        if v_item.cantidad is null or v_item.cantidad < 1 or v_item.cantidad > 10 then
            raise exception 'CANTIDAD_INVALIDA' using errcode = '22023';
        end if;

        select t.* into v_ticket
        from public.tickets t
        where t.id = v_item.ticket_id and t.activo
        for update;

        if not found then
            raise exception 'NO_DISPONIBLE: %', v_item.ticket_id using errcode = 'P0001';
        end if;
        if v_ticket.stock < v_item.cantidad then
            raise exception 'SIN_STOCK: %', v_ticket.nombre using errcode = 'P0001';
        end if;

        update public.tickets t set stock = t.stock - v_item.cantidad where t.id = v_ticket.id;

        insert into public.order_items (order_id, ticket_id, nombre, cantidad, precio_unitario)
        values (p_orden, v_ticket.id, v_ticket.nombre, v_item.cantidad, v_ticket.precio);

        v_total := v_total + v_ticket.precio * v_item.cantidad;
    end loop;

    update public.orders o set total = v_total where o.id = p_orden;
    return v_total;
end;
$$;

create or replace function public._nuevo_codigo_orden()
returns text
language plpgsql
security definer set search_path = ''
as $$
declare
    v_codigo text;
begin
    loop
        v_codigo := 'GP26-' || (100000 + floor(random() * 900000))::int::text;
        exit when not exists (select 1 from public.orders o where o.codigo = v_codigo);
    end loop;
    return v_codigo;
end;
$$;

-- Cancela una orden pendiente y devuelve su stock. No hace nada si ya no está pendiente.
create or replace function public._cancelar_orden_pendiente(p_orden bigint)
returns boolean
language plpgsql
security definer set search_path = ''
as $$
begin
    update public.orders o set estado = 'cancelada'
    where o.id = p_orden and o.estado = 'pendiente';
    if not found then
        return false;
    end if;

    update public.tickets t
    set stock = t.stock + i.cantidad
    from public.order_items i
    where i.order_id = p_orden and i.ticket_id = t.id;
    return true;
end;
$$;

-- Libera las reservas de Mercado Pago que nadie pagó en 30 minutos.
-- Las que tienen un pago en proceso (ej. pago en efectivo) se esperan hasta 3 días.
create or replace function public._liberar_reservas_vencidas()
returns void
language plpgsql
security definer set search_path = ''
as $$
declare
    v_id bigint;
begin
    for v_id in
        select o.id from public.orders o
        where o.estado = 'pendiente'
          and o.metodo_pago = 'mercadopago'
          and (
              (o.mp_payment_id is null and o.created_at < now() - interval '30 minutes')
              or o.created_at < now() - interval '3 days'
          )
        for update skip locked
    loop
        perform public._cancelar_orden_pendiente(v_id);
    end loop;
end;
$$;

-- ============================================================
-- FUNCIONES PARA LA APP (usuario logueado)
-- ============================================================

-- Pago con saldo: misma lógica que supabase_saldo.sql, ahora usando las funciones internas.
create or replace function public.crear_orden(p_items jsonb)
returns jsonb
language plpgsql
security definer set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_orden bigint;
    v_codigo text;
    v_total numeric(14, 2);
    v_saldo numeric(14, 2);
begin
    if v_user is null then
        raise exception 'NO_AUTENTICADO' using errcode = '42501';
    end if;

    perform public._liberar_reservas_vencidas();

    -- Se bloquea el perfil primero: dos compras a la vez no pueden gastar el mismo saldo.
    select p.saldo into v_saldo from public.profiles p where p.id = v_user for update;
    if not found then
        raise exception 'USUARIO_NO_ENCONTRADO' using errcode = 'P0002';
    end if;

    v_codigo := public._nuevo_codigo_orden();
    insert into public.orders (user_id, total, codigo, estado, metodo_pago, pagado_at)
    values (v_user, 0, v_codigo, 'confirmada', 'saldo', now())
    returning id into v_orden;

    v_total := public._cargar_items_orden(v_orden, p_items);

    if v_saldo < v_total then
        raise exception 'SALDO_INSUFICIENTE' using errcode = 'P0001';
    end if;

    v_saldo := v_saldo - v_total;
    update public.profiles p set saldo = v_saldo where p.id = v_user;

    if v_total > 0 then
        insert into public.saldo_movimientos (user_id, monto, saldo_final, motivo, order_id, creado_por)
        values (v_user, -v_total, v_saldo, 'Compra ' || v_codigo, v_orden, v_user);
    end if;

    return jsonb_build_object('id', v_orden, 'codigo', v_codigo, 'total', v_total, 'saldo', v_saldo);
end;
$$;

-- Reserva el stock en una orden pendiente para pagarla con Mercado Pago.
create or replace function public.reservar_orden_mp(p_items jsonb)
returns jsonb
language plpgsql
security definer set search_path = ''
as $$
declare
    v_user uuid := auth.uid();
    v_orden bigint;
    v_codigo text;
    v_total numeric(14, 2);
begin
    if v_user is null then
        raise exception 'NO_AUTENTICADO' using errcode = '42501';
    end if;

    perform public._liberar_reservas_vencidas();

    v_codigo := public._nuevo_codigo_orden();
    insert into public.orders (user_id, total, codigo, estado, metodo_pago)
    values (v_user, 0, v_codigo, 'pendiente', 'mercadopago')
    returning id into v_orden;

    v_total := public._cargar_items_orden(v_orden, p_items);
    if v_total <= 0 then
        raise exception 'MONTO_INVALIDO' using errcode = '22023';
    end if;

    return jsonb_build_object('id', v_orden, 'codigo', v_codigo, 'total', v_total);
end;
$$;

-- El usuario abandona un pago de Mercado Pago que todavía no hizo: se libera el stock.
create or replace function public.cancelar_mi_orden_mp(p_order_id bigint)
returns boolean
language plpgsql
security definer set search_path = ''
as $$
begin
    if not exists (
        select 1 from public.orders o
        where o.id = p_order_id
          and o.user_id = auth.uid()
          and o.metodo_pago = 'mercadopago'
          and o.mp_payment_id is null
    ) then
        raise exception 'NO_AUTORIZADO' using errcode = '42501';
    end if;
    return public._cancelar_orden_pendiente(p_order_id);
end;
$$;

-- ============================================================
-- FUNCIÓN PARA LAS EDGE FUNCTIONS (solo service_role)
-- ============================================================

-- Aplica el estado de un pago consultado a la API de Mercado Pago.
-- p_status: approved | pending | in_process | authorized | rejected | cancelled | refunded | charged_back
create or replace function public.mp_confirmar_pago(
    p_order_id bigint,
    p_payment_id text,
    p_status text,
    p_monto numeric,
    p_moneda text
)
returns jsonb
language plpgsql
security definer set search_path = ''
as $$
declare
    v_orden public.orders%rowtype;
    v_hay_stock boolean;
begin
    select o.* into v_orden from public.orders o where o.id = p_order_id for update;
    if not found or v_orden.metodo_pago <> 'mercadopago' then
        return jsonb_build_object('resultado', 'ORDEN_NO_ENCONTRADA');
    end if;

    -- Otro pago de la misma orden ya la confirmó: no se toca.
    if v_orden.estado = 'confirmada' and v_orden.mp_payment_id is distinct from p_payment_id then
        return jsonb_build_object('resultado', 'YA_CONFIRMADA', 'estado', v_orden.estado);
    end if;

    update public.orders o
    set mp_payment_id = p_payment_id, mp_estado = p_status
    where o.id = p_order_id;

    if p_status = 'approved' then
        if p_moneda is distinct from 'ARS' or p_monto < v_orden.total then
            raise warning 'Pago % de la orden % no coincide: % % (esperado ARS %)',
                p_payment_id, p_order_id, p_monto, p_moneda, v_orden.total;
            return jsonb_build_object('resultado', 'MONTO_NO_COINCIDE');
        end if;

        if v_orden.estado = 'pendiente' then
            update public.orders o set estado = 'confirmada', pagado_at = now() where o.id = p_order_id;
        elsif v_orden.estado = 'cancelada' then
            -- La reserva venció pero el pago entró igual: se vuelve a tomar el stock si todavía hay.
            select bool_and(t.stock >= i.cantidad) into v_hay_stock
            from public.order_items i
            join public.tickets t on t.id = i.ticket_id
            where i.order_id = p_order_id;

            if coalesce(v_hay_stock, false) then
                update public.tickets t
                set stock = t.stock - i.cantidad
                from public.order_items i
                where i.order_id = p_order_id and i.ticket_id = t.id;
                update public.orders o set estado = 'confirmada', pagado_at = now() where o.id = p_order_id;
            else
                raise warning 'Orden % pagada (pago %) sin stock disponible: hay que reembolsar',
                    p_order_id, p_payment_id;
                return jsonb_build_object('resultado', 'REQUIERE_REEMBOLSO');
            end if;
        end if;
    elsif p_status in ('rejected', 'cancelled') then
        perform public._cancelar_orden_pendiente(p_order_id);
    elsif p_status in ('refunded', 'charged_back') then
        -- Devolución del pago: la entrada deja de ser válida en el ingreso.
        update public.orders o set estado = 'cancelada' where o.id = p_order_id;
    end if;

    select o.* into v_orden from public.orders o where o.id = p_order_id;
    return jsonb_build_object('resultado', 'OK', 'estado', v_orden.estado);
end;
$$;

-- ============================================================
-- PERMISOS
-- ============================================================
-- Las órdenes solo se crean por crear_orden / reservar_orden_mp: así nadie puede armar
-- una orden pendiente a mano (con precios inventados) y hacerla pasar por un pago.
revoke insert on public.orders, public.order_items from authenticated;
drop policy if exists "orders_insert_own" on public.orders;
drop policy if exists "order_items_insert_own" on public.order_items;

revoke execute on function public._cargar_items_orden(bigint, jsonb) from public, anon, authenticated;
revoke execute on function public._nuevo_codigo_orden() from public, anon, authenticated;
revoke execute on function public._cancelar_orden_pendiente(bigint) from public, anon, authenticated;
revoke execute on function public._liberar_reservas_vencidas() from public, anon, authenticated;

revoke execute on function public.crear_orden(jsonb) from public, anon;
revoke execute on function public.reservar_orden_mp(jsonb) from public, anon;
revoke execute on function public.cancelar_mi_orden_mp(bigint) from public, anon;
grant execute on function public.crear_orden(jsonb) to authenticated;
grant execute on function public.reservar_orden_mp(jsonb) to authenticated;
grant execute on function public.cancelar_mi_orden_mp(bigint) to authenticated;

revoke execute on function public.mp_confirmar_pago(bigint, text, text, numeric, text) from public, anon, authenticated;
grant execute on function public.mp_confirmar_pago(bigint, text, text, numeric, text) to service_role;
