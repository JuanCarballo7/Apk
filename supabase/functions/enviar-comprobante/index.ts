// Envía por mail el comprobante de una orden (con su QR) al email de la cuenta que la compró.
// La app la llama después de confirmar la compra: POST { "orden_id": 123 } con el JWT del usuario.
//
// Secrets necesarios (Dashboard > Edge Functions > Secrets):
//   GMAIL_USER          cuenta de Gmail que envía, ej. entradas.gp@gmail.com
//   GMAIL_APP_PASSWORD  contraseña de aplicación de esa cuenta (16 letras, requiere verificación en 2 pasos)
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";
import nodemailer from "npm:nodemailer@6.9.16";
import QRCode from "npm:qrcode@1.5.4";

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const ars = (n: number) =>
  "$ " + Math.round(n).toLocaleString("es-AR").replace(/,/g, ".");

const escapar = (s: string) =>
  s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Usá POST" });

  const gmailUser = Deno.env.get("GMAIL_USER");
  const gmailPass = Deno.env.get("GMAIL_APP_PASSWORD");
  if (!gmailUser || !gmailPass) {
    return json(500, { error: "Faltan los secrets GMAIL_USER / GMAIL_APP_PASSWORD" });
  }

  // Cliente con el JWT del usuario: la RLS solo le deja leer sus propias órdenes.
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } },
  );

  const { data: { user } } = await supabase.auth.getUser();
  if (!user?.email) return json(401, { error: "Sesión inválida" });

  const { orden_id } = await req.json().catch(() => ({}));
  if (!orden_id) return json(400, { error: "Falta orden_id" });

  const { data: orden, error } = await supabase
    .from("orders")
    .select("id, codigo, total, created_at, order_items ( nombre, cantidad, precio_unitario )")
    .eq("id", orden_id)
    .single();
  if (error || !orden) return json(404, { error: "Orden no encontrada" });

  const { data: perfil } = await supabase
    .from("profiles")
    .select("first_name")
    .eq("id", user.id)
    .maybeSingle();

  // Mismo contenido que QrHelper.contenidoOrden en la app, así lo lee el control de acceso.
  const qr = await QRCode.toBuffer(`JA-GP26|${orden.codigo}|${orden.id}`, {
    errorCorrectionLevel: "M",
    margin: 1,
    width: 320,
  });

  const filas = orden.order_items.map((i: { nombre: string; cantidad: number; precio_unitario: number }) => `
      <tr>
        <td style="padding:8px 0;color:#fff">${i.cantidad} × ${escapar(i.nombre)}</td>
        <td style="padding:8px 0;color:#fff;text-align:right">${ars(i.cantidad * i.precio_unitario)}</td>
      </tr>`).join("");
  const fecha = new Date(orden.created_at).toLocaleString("es-AR", {
    timeZone: "America/Argentina/Buenos_Aires",
    dateStyle: "long",
    timeStyle: "short",
  });
  const nombre = perfil?.first_name ? `, ${escapar(perfil.first_name)}` : "";

  const html = `
  <div style="background:#0B0B10;padding:24px;font-family:Arial,sans-serif">
    <div style="max-width:480px;margin:0 auto;background:#16161D;border-radius:14px;padding:24px;border-top:4px solid #E10600">
      <h1 style="color:#fff;font-size:22px;margin:0 0 6px">¡Gracias por tu compra${nombre}!</h1>
      <p style="color:#A0A0A0;margin:0 0 20px">Gran Premio de Argentina · Autódromo Oscar y Juan Gálvez · 06 - 08 NOV</p>
      <p style="color:#A0A0A0;margin:0">Código de reserva</p>
      <p style="color:#fff;font-size:24px;font-weight:bold;margin:4px 0 16px;letter-spacing:1px">${escapar(orden.codigo)}</p>
      <table style="width:100%;border-collapse:collapse;border-top:1px solid #2A2A33">${filas}
        <tr>
          <td style="padding:12px 0;color:#fff;font-weight:bold;border-top:1px solid #2A2A33">Total</td>
          <td style="padding:12px 0;color:#E10600;font-weight:bold;text-align:right;border-top:1px solid #2A2A33">${ars(orden.total)}</td>
        </tr>
      </table>
      <div style="text-align:center;margin:20px 0 8px">
        <img src="cid:qr-entrada" width="200" height="200" alt="QR de ingreso" style="background:#fff;padding:8px;border-radius:8px" />
      </div>
      <p style="color:#A0A0A0;font-size:13px;text-align:center;margin:0">Mostrá este QR en el ingreso al autódromo.</p>
      <p style="color:#777;font-size:12px;text-align:center;margin:20px 0 0">Compra realizada el ${fecha}</p>
    </div>
  </div>`;

  try {
    // Puerto 465 (SSL): las Edge Functions no permiten conexiones salientes al 25 ni al 587.
    const transporte = nodemailer.createTransport({
      host: "smtp.gmail.com",
      port: 465,
      secure: true,
      auth: { user: gmailUser, pass: gmailPass },
    });
    await transporte.sendMail({
      from: `"GP Argentina - Entradas" <${gmailUser}>`,
      to: user.email,
      subject: `Tus entradas para el GP de Argentina · ${orden.codigo}`,
      html,
      attachments: [{ filename: `entrada-${orden.codigo}.png`, content: qr, cid: "qr-entrada" }],
    });
  } catch (e) {
    console.error("Error enviando mail", e);
    return json(502, { error: "No se pudo enviar el mail" });
  }

  return json(200, { enviado: true, email: user.email });
});
