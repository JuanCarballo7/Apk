// Crea el pago de Mercado Pago (Checkout Pro) para el carrito del usuario logueado.
// La app la llama con el JWT del usuario:
//   POST { "items": [{ "ticket_id": "campo-3d", "cantidad": 2 }] }      -> reserva y devuelve el link de pago
//   POST { "accion": "verificar", "orden_id": 123 }                     -> consulta a Mercado Pago si ya se pagó
//   POST { "accion": "cancelar", "orden_id": 123 }                      -> abandona el pago y libera el stock
//
// Secret necesario (Dashboard > Edge Functions > Secrets):
//   MP_ACCESS_TOKEN   Access Token de Mercado Pago (de prueba "TEST-..." o productivo "APP_USR-...")
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

// Deep link que abre la app al volver de Mercado Pago (AndroidManifest: PagoMpActivity).
const RETORNO_APP = "jagp26://pago";
const MINUTOS_PARA_PAGAR = 30;
const MP_API = "https://api.mercadopago.com";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Usá POST" });

  const token = Deno.env.get("MP_ACCESS_TOKEN");
  if (!token) return json(500, { error: "MP_NO_CONFIGURADO" });

  const url = Deno.env.get("SUPABASE_URL")!;
  // Cliente con el JWT del usuario: las funciones SQL usan auth.uid() y la RLS limita a sus órdenes.
  const usuario = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
  });
  // Cliente de servicio: solo para guardar datos del pago y confirmar (mp_confirmar_pago).
  const servicio = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  const { data: { user } } = await usuario.auth.getUser();
  if (!user) return json(401, { error: "NO_AUTENTICADO" });

  const body = await req.json().catch(() => ({}));

  if (body.accion === "verificar") return verificar(usuario, servicio, token, Number(body.orden_id));
  if (body.accion === "cancelar") {
    const { error } = await usuario.rpc("cancelar_mi_orden_mp", { p_order_id: Number(body.orden_id) });
    return error ? json(400, { error: error.message }) : json(200, { cancelada: true });
  }

  // 1. Reserva el stock en una orden pendiente (precios tomados de la base, nunca de la app).
  const { data: orden, error } = await usuario.rpc("reservar_orden_mp", { p_items: body.items });
  if (error) return json(400, { error: error.message });

  const { data: lineas } = await servicio
    .from("order_items")
    .select("ticket_id, nombre, cantidad, precio_unitario")
    .eq("order_id", orden.id);

  // 2. Crea la preferencia de Checkout Pro.
  const vence = new Date(Date.now() + MINUTOS_PARA_PAGAR * 60 * 1000).toISOString();
  const respuesta = await fetch(`${MP_API}/checkout/preferences`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-Idempotency-Key": `orden-${orden.id}`,
    },
    body: JSON.stringify({
      items: (lineas ?? []).map((l) => ({
        id: l.ticket_id,
        title: `GP Argentina · ${l.nombre}`,
        quantity: l.cantidad,
        unit_price: Number(l.precio_unitario),
        currency_id: "ARS",
      })),
      payer: { email: user.email },
      external_reference: String(orden.id),
      notification_url: `${url}/functions/v1/mp-webhook`,
      back_urls: { success: RETORNO_APP, failure: RETORNO_APP, pending: RETORNO_APP },
      auto_return: "approved",
      expires: true,
      expiration_date_to: vence,
      statement_descriptor: "GP ARGENTINA",
    }),
  });

  if (!respuesta.ok) {
    console.error("Mercado Pago rechazó la preferencia", respuesta.status, await respuesta.text());
    await usuario.rpc("cancelar_mi_orden_mp", { p_order_id: orden.id });
    return json(502, { error: "MP_NO_DISPONIBLE" });
  }

  const preferencia = await respuesta.json();
  await servicio.from("orders").update({ mp_preference_id: preferencia.id }).eq("id", orden.id);

  return json(200, {
    orden_id: orden.id,
    codigo: orden.codigo,
    total: orden.total,
    init_point: preferencia.init_point,
  });
});

// Busca en Mercado Pago los pagos de la orden y aplica el más reciente.
// Sirve de respaldo si el webhook todavía no llegó cuando el usuario vuelve a la app.
async function verificar(
  // deno-lint-ignore no-explicit-any
  usuario: any,
  // deno-lint-ignore no-explicit-any
  servicio: any,
  token: string,
  ordenId: number,
) {
  if (!ordenId) return json(400, { error: "Falta orden_id" });

  // Con el cliente del usuario: si la orden no es suya, la RLS no la devuelve.
  const { data: orden } = await usuario
    .from("orders")
    .select("id, estado, metodo_pago")
    .eq("id", ordenId)
    .maybeSingle();
  if (!orden) return json(404, { error: "Orden no encontrada" });
  if (orden.metodo_pago !== "mercadopago" || orden.estado !== "pendiente") {
    return json(200, { estado: orden.estado });
  }

  const busqueda = await fetch(
    `${MP_API}/v1/payments/search?external_reference=${ordenId}&sort=date_created&criteria=desc`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!busqueda.ok) {
    console.error("No se pudo buscar el pago", busqueda.status, await busqueda.text());
    return json(200, { estado: orden.estado });
  }

  const pagos = (await busqueda.json()).results ?? [];
  const pago = pagos.find((p: { status: string }) => p.status === "approved") ?? pagos[0];
  if (!pago) return json(200, { estado: orden.estado });

  const { data, error } = await servicio.rpc("mp_confirmar_pago", {
    p_order_id: ordenId,
    p_payment_id: String(pago.id),
    p_status: pago.status,
    p_monto: pago.transaction_amount,
    p_moneda: pago.currency_id,
  });
  if (error) {
    console.error("mp_confirmar_pago falló", error);
    return json(200, { estado: orden.estado });
  }
  return json(200, { estado: data?.estado ?? orden.estado });
}
