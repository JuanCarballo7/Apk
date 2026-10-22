// Recibe las notificaciones de Mercado Pago (webhook) y confirma o cancela la orden.
// Se publica con "Verify JWT" DESACTIVADO: Mercado Pago no manda un token de Supabase.
//
// No confía en el contenido de la notificación: con el id que llega consulta el pago
// a la API de Mercado Pago con nuestro Access Token, y solo eso decide el estado.
// Por eso una notificación falsa no puede confirmar una orden.
//
// Secret necesario: MP_ACCESS_TOKEN (el mismo que usa mp-crear-pago).
// URL que usa Mercado Pago: https://<proyecto>.supabase.co/functions/v1/mp-webhook
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const MP_API = "https://api.mercadopago.com";
const ok = () => new Response("ok", { status: 200 });

Deno.serve(async (req) => {
  const token = Deno.env.get("MP_ACCESS_TOKEN");
  if (!token) {
    console.error("Falta el secret MP_ACCESS_TOKEN");
    return new Response("sin configurar", { status: 500 });
  }

  // Mercado Pago manda el tipo y el id en la URL (?type=payment&data.id=123) y/o en el cuerpo.
  const url = new URL(req.url);
  let tipo = url.searchParams.get("type") ?? url.searchParams.get("topic");
  let pagoId = url.searchParams.get("data.id") ?? url.searchParams.get("id");
  const body = await req.json().catch(() => null);
  if (body) {
    tipo = body.type ?? body.topic ?? tipo;
    pagoId = body.data?.id ?? pagoId;
  }

  // Solo interesan los pagos (las merchant_order y otros avisos se ignoran).
  if (tipo !== "payment" || !pagoId) return ok();

  const respuesta = await fetch(`${MP_API}/v1/payments/${encodeURIComponent(String(pagoId))}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (respuesta.status === 404) return ok();
  if (!respuesta.ok) {
    // 500 hace que Mercado Pago reintente más tarde.
    console.error("No se pudo consultar el pago", pagoId, respuesta.status, await respuesta.text());
    return new Response("reintentar", { status: 500 });
  }

  const pago = await respuesta.json();
  const ordenId = Number(pago.external_reference);
  if (!ordenId) return ok();

  const servicio = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data, error } = await servicio.rpc("mp_confirmar_pago", {
    p_order_id: ordenId,
    p_payment_id: String(pago.id),
    p_status: pago.status,
    p_monto: pago.transaction_amount,
    p_moneda: pago.currency_id,
  });
  if (error) {
    console.error("mp_confirmar_pago falló", ordenId, error);
    return new Response("reintentar", { status: 500 });
  }

  console.log(`Orden ${ordenId} · pago ${pago.id} · ${pago.status} ->`, data);
  return ok();
});
