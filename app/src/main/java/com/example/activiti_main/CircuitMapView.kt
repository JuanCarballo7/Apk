package com.example.activiti_main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.activiti_main.data.EntradasCatalogo
import com.example.activiti_main.data.ZonaCircuito
import kotlin.math.hypot

/**
 * Mapa en blanco y negro del Autódromo Oscar y Juan Gálvez (trazado de 15 curvas),
 * dividido en 3 sectores. Coordenadas calcadas del plano del circuito (lienzo 547x365).
 * Se puede tocar una tribuna o un sector, hacer pinch-zoom, arrastrar y usar los botones.
 */
class CircuitMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Se dispara al tocar una tribuna (zonaId) o un sector de pista (sector); ambos null = vacío. */
    var onSeleccion: ((zonaId: String?, sector: Int?) -> Unit)? = null

    var zonaSeleccionada: String? = null
        set(value) {
            field = value
            if (value != null) sectorSeleccionado = null
            invalidate()
        }

    var sectorSeleccionado: Int? = null
        set(value) {
            field = value
            if (value != null) zonaSeleccionada = null
            invalidate()
        }

    /**
     * Línea central en sentido de carrera desde la línea de largada.
     * Sector 1: curvas 1-4 · Sector 2: curvas 5-10 · Sector 3: curvas 11-15 y recta principal.
     */
    private val sectores = listOf(
        listOf(
            280f to 128f, 300f to 125f, 345f to 116f, 360f to 115f, 368f to 123f, 371f to 136f,
            380f to 145f, 392f to 138f, 405f to 115f, 415f to 92f, 430f to 72f, 450f to 62f,
            480f to 57f, 505f to 60f, 522f to 70f, 527f to 82f, 520f to 95f, 492f to 100f
        ),
        listOf(
            492f to 100f, 470f to 105f, 458f to 112f, 447f to 125f, 435f to 145f, 420f to 165f,
            405f to 180f, 380f to 190f, 345f to 196f, 310f to 190f, 285f to 186f, 268f to 191f,
            255f to 197f, 240f to 189f, 207f to 182f, 192f to 184f, 187f to 190f, 192f to 201f,
            200f to 217f, 227f to 237f
        ),
        listOf(
            227f to 237f, 260f to 243f, 300f to 247f, 320f to 250f, 335f to 258f, 342f to 272f,
            336f to 287f, 320f to 293f, 295f to 296f, 275f to 295f, 262f to 287f, 227f to 257f,
            187f to 223f, 143f to 190f, 110f to 160f, 67f to 127f, 45f to 108f, 35f to 97f,
            38f to 89f, 48f to 88f, 62f to 97f, 77f to 110f, 110f to 127f, 140f to 137f,
            187f to 140f, 233f to 135f, 280f to 128f
        )
    )

    /** Tonos de cada sector: blanco, gris claro y gris oscuro. */
    private val tonosSector = listOf("#FFFFFF", "#A6A6A6", "#5C5C5C")

    /** Dónde va la etiqueta S1/S2/S3 de cada sector. */
    private val etiquetasSector = listOf(380f to 80f, 400f to 212f, 100f to 198f)

    /** Número de curva y posición de su etiqueta, como en el plano oficial. */
    private val curvas = listOf(
        Triple(1, 365f, 102f), Triple(2, 379f, 133f), Triple(3, 422f, 74f),
        Triple(4, 538f, 72f), Triple(5, 456f, 99f), Triple(6, 422f, 182f),
        Triple(7, 330f, 209f), Triple(8, 280f, 172f), Triple(9, 253f, 210f),
        Triple(10, 176f, 180f), Triple(11, 248f, 233f), Triple(12, 352f, 280f),
        Triple(13, 280f, 307f), Triple(14, 22f, 82f), Triple(15, 153f, 155f)
    )

    private class ZonaDibujo(val zona: ZonaCircuito, val path: Path, val bounds: RectF)

    private val zonas = EntradasCatalogo.zonas.map { zona ->
        val path = Path().apply {
            moveTo(zona.puntos[0].x, zona.puntos[0].y)
            zona.puntos.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        val bounds = RectF().also { path.computeBounds(it, true) }
        ZonaDibujo(zona, path, bounds)
    }

    private val pathsSector = sectores.map { puntos ->
        Path().apply {
            moveTo(puntos[0].first, puntos[0].second)
            puntos.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
    }

    private val curvaEffect = CornerPathEffect(8f)
    private fun trazo(colorHex: String, ancho: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ancho
        color = Color.parseColor(colorHex)
        pathEffect = curvaEffect
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val bordePaint = trazo("#FFFFFF", 14f)
    private val sectorPaints = tonosSector.map { trazo(it, 11f) }
    private val lineaCentralPaint = trazo("#000000", 1.4f)

    private val tribunaPaint = relleno("#8C8C8C")
    private val paddockPaint = relleno("#2E2E2E")
    private val paddockBordePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.parseColor("#8C8C8C")
    }
    private val campoPaint = relleno("#2A2A2A")
    private val seleccionPaint = relleno("#FFFFFF")
    private val seleccionBordePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.BLACK
    }
    private val blancoPaint = relleno("#FFFFFF")
    private val negroPaint = relleno("#000000")
    private val circuloCurvaPaint = relleno("#E8E8E8")
    private val numeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        textSize = 8f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val etiquetaFondoPaint = relleno("#000000")
    private val etiquetaBordePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.WHITE
    }
    private val etiquetaTextoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        textSize = 12f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }
    private val lineaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    private fun relleno(colorHex: String) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex) }

    private val ajuste = Matrix()
    private val usuario = Matrix()
    private val total = Matrix()
    private val inversa = Matrix()
    private val tmp = FloatArray(2)

    private val gestos = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val (px, py) = aLienzo(e.x, e.y)
            val zona = zonaEn(px, py)
            val sector = if (zona == null) sectorEn(px, py) else null
            zonaSeleccionada = zona
            sectorSeleccionado = sector
            onSeleccion?.invoke(zona, sector)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (escalaUsuario() <= 1f) return false
            usuario.postTranslate(-dx, -dy)
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoom(1.6f, e.x, e.y)
            return true
        }
    })

    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    fun zoomIn() = zoom(1.4f, width / 2f, height / 2f)

    fun zoomOut() = zoom(1 / 1.4f, width / 2f, height / 2f)

    fun recentrar() {
        usuario.reset()
        invalidate()
    }

    private fun zoom(factor: Float, fx: Float, fy: Float) {
        val actual = escalaUsuario()
        val nueva = (actual * factor).coerceIn(1f, 3f)
        if (nueva == 1f) {
            usuario.reset()
        } else {
            usuario.postScale(nueva / actual, nueva / actual, fx, fy)
        }
        invalidate()
    }

    private fun escalaUsuario(): Float {
        val v = FloatArray(9)
        usuario.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val padding = 10f * resources.displayMetrics.density
        val escala = minOf((w - 2 * padding) / CONTENIDO.width(), (h - 2 * padding) / CONTENIDO.height())
        ajuste.reset()
        ajuste.postTranslate(-CONTENIDO.left, -CONTENIDO.top)
        ajuste.postScale(escala, escala)
        ajuste.postTranslate(
            (w - CONTENIDO.width() * escala) / 2f,
            (h - CONTENIDO.height() * escala) / 2f
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        if (!pinch.isInProgress) gestos.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return true
    }

    private fun aLienzo(x: Float, y: Float): Pair<Float, Float> {
        total.invert(inversa)
        tmp[0] = x
        tmp[1] = y
        inversa.mapPoints(tmp)
        return tmp[0] to tmp[1]
    }

    /** Las tribunas son finas: se acepta el toque cerca y gana la más próxima. */
    private fun zonaEn(px: Float, py: Float): String? =
        zonas
            .filter { RectF(it.bounds).apply { inset(-8f, -8f) }.contains(px, py) }
            .minByOrNull { hypot(it.bounds.centerX() - px, it.bounds.centerY() - py) }
            ?.zona?.id

    /** Sector (1..3) cuya pista quede más cerca del toque, si está a menos de 12 unidades. */
    private fun sectorEn(px: Float, py: Float): Int? {
        var mejor: Int? = null
        var mejorDist = 12f
        sectores.forEachIndexed { i, puntos ->
            puntos.zipWithNext().forEach { (a, b) ->
                val d = distanciaASegmento(px, py, a.first, a.second, b.first, b.second)
                if (d < mejorDist) {
                    mejorDist = d
                    mejor = i + 1
                }
            }
        }
        return mejor
    }

    private fun distanciaASegmento(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val largo2 = dx * dx + dy * dy
        val t = if (largo2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / largo2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        total.set(ajuste)
        total.postConcat(usuario)

        canvas.save()
        canvas.concat(total)

        zonas.forEach { z ->
            val seleccionada = z.zona.id == zonaSeleccionada
            val paint = when {
                seleccionada -> seleccionPaint
                z.zona.id == EntradasCatalogo.ZONA_PADDOCK -> paddockPaint
                z.zona.id == EntradasCatalogo.ZONA_CAMPO -> campoPaint
                else -> tribunaPaint
            }
            canvas.drawPath(z.path, paint)
            if (seleccionada) {
                canvas.drawPath(z.path, seleccionBordePaint)
            } else if (z.zona.id == EntradasCatalogo.ZONA_PADDOCK) {
                canvas.drawPath(z.path, paddockBordePaint)
            }
        }

        pathsSector.forEachIndexed { i, path ->
            val alpha = if (sectorSeleccionado == null || sectorSeleccionado == i + 1) 255 else 45
            bordePaint.alpha = alpha
            sectorPaints[i].alpha = alpha
            lineaCentralPaint.alpha = alpha
            canvas.drawPath(path, bordePaint)
            canvas.drawPath(path, sectorPaints[i])
            canvas.drawPath(path, lineaCentralPaint)
        }
        dibujarLimitesDeSector(canvas)
        dibujarLargada(canvas)
        dibujarFlecha(canvas)
        dibujarNumerosDeCurva(canvas)
        dibujarEtiquetasDeSector(canvas)

        canvas.restore()

        val zona = zonaSeleccionada?.let { id -> zonas.firstOrNull { it.zona.id == id } }
        val sector = sectorSeleccionado
        when {
            zona != null -> dibujarPopup(
                canvas, zona.zona.nombre.uppercase(), zona.bounds.centerX(), zona.bounds.centerY()
            )
            sector != null -> {
                val (x, y) = etiquetasSector[sector - 1]
                dibujarPopup(canvas, "SECTOR $sector · ${EntradasCatalogo.curvasDelSector(sector)}", x, y)
            }
        }
    }

    /** Marca negra transversal donde empieza cada sector. */
    private fun dibujarLimitesDeSector(canvas: Canvas) {
        sectores.drop(1).forEach { puntos ->
            val (x, y) = puntos[0]
            val (nx, ny) = puntos[1]
            val largo = hypot(nx - x, ny - y)
            val perpX = -(ny - y) / largo * 8f
            val perpY = (nx - x) / largo * 8f
            canvas.drawLine(x - perpX, y - perpY, x + perpX, y + perpY, seleccionBordePaint)
        }
    }

    private fun dibujarEtiquetasDeSector(canvas: Canvas) {
        val baseTexto = -(etiquetaTextoPaint.descent() + etiquetaTextoPaint.ascent()) / 2
        etiquetasSector.forEachIndexed { i, (x, y) ->
            val caja = RectF(x - 11f, y - 7f, x + 11f, y + 7f)
            canvas.drawRoundRect(caja, 3f, 3f, etiquetaFondoPaint)
            canvas.drawRoundRect(caja, 3f, 3f, etiquetaBordePaint)
            canvas.drawText("S${i + 1}", x, y + baseTexto, etiquetaTextoPaint)
        }
    }

    /** Línea de largada a cuadros sobre la recta principal. */
    private fun dibujarLargada(canvas: Canvas) {
        val lado = 2.5f
        for (col in 0 until 2) for (fila in 0 until 6) {
            val x = 278f + col * lado
            val y = 120.5f + fila * lado
            canvas.drawRect(x, y, x + lado, y + lado, if ((col + fila) % 2 == 0) blancoPaint else negroPaint)
        }
    }

    /** Sentido de carrera sobre la recta principal. */
    private fun dibujarFlecha(canvas: Canvas) {
        canvas.save()
        canvas.translate(322f, 121f)
        canvas.rotate(-11f)
        val flecha = Path().apply {
            moveTo(-4f, -3f)
            lineTo(4f, 0f)
            lineTo(-4f, 3f)
            close()
        }
        canvas.drawPath(flecha, negroPaint)
        canvas.restore()
    }

    private fun dibujarNumerosDeCurva(canvas: Canvas) {
        val baseTexto = -(numeroPaint.descent() + numeroPaint.ascent()) / 2
        curvas.forEach { (n, x, y) ->
            canvas.drawCircle(x, y, 6.5f, circuloCurvaPaint)
            canvas.drawText(n.toString(), x, y + baseTexto, numeroPaint)
        }
    }

    private fun dibujarPopup(canvas: Canvas, texto: String, lx: Float, ly: Float) {
        tmp[0] = lx
        tmp[1] = ly
        total.mapPoints(tmp)
        val ancla = PointF(tmp[0], tmp[1])

        val d = resources.displayMetrics.density
        val padH = 12 * d
        val alto = 30 * d
        val separacion = 18 * d
        val ancho = popupTextPaint.measureText(texto) + 2 * padH

        val arriba = ancla.y - separacion - alto >= 0
        val top = if (arriba) ancla.y - separacion - alto else ancla.y + separacion
        val left = (ancla.x - ancho / 2).coerceIn(4 * d, (width - ancho - 4 * d).coerceAtLeast(4 * d))
        val caja = RectF(left, top, left + ancho, top + alto)

        canvas.drawLine(ancla.x, ancla.y, ancla.x, if (arriba) caja.bottom else caja.top, lineaPaint)
        canvas.drawCircle(ancla.x, ancla.y, 4 * d, blancoPaint)
        canvas.drawRoundRect(caja, 3 * d, 3 * d, blancoPaint)
        val baseTexto = caja.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2
        canvas.drawText(texto, caja.left + padH, baseTexto, popupTextPaint)
    }

    companion object {
        /** Zona del plano que se muestra completa al recentrar. */
        private val CONTENIDO = RectF(0f, 36f, 547f, 316f)
    }
}
