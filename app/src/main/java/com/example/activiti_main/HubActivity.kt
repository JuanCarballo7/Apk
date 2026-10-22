package com.example.activiti_main

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.activiti_main.data.ClasificacionItem
import com.example.activiti_main.data.F1Repository
import com.example.activiti_main.data.NewsApi
import kotlinx.coroutines.launch

class HubActivity : AppCompatActivity() {

    private lateinit var tabNews: TextView
    private lateinit var tabEntradas: TextView
    private lateinit var tabReaccion: TextView
    private lateinit var tabTablas: TextView
    private lateinit var tabNosotros: TextView

    private lateinit var panelNews: View
    private lateinit var panelEntradas: View
    private lateinit var panelReaccion: View
    private lateinit var panelTablas: View
    private lateinit var entradasUi: EntradasUi
    private lateinit var reaccionUi: ReaccionUi
    private var tabActual = "news"
    private lateinit var txtSubtituloClasificacion: TextView
    private lateinit var txtActualizacionClasificacion: TextView

    private lateinit var imgFeatured: ImageView
    private lateinit var txtFeaturedBadge: TextView
    private lateinit var txtFeaturedTitle: TextView
    private lateinit var cardFeatured: View

    private lateinit var clasificacionAdapter: ClasificacionAdapter
    private lateinit var newsAdapter: NewsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        findViewById<View>(R.id.headerBar).applyTopSystemBarPadding()

        tabNews = findViewById(R.id.tabNews)
        tabEntradas = findViewById(R.id.tabEntradas)
        tabReaccion = findViewById(R.id.tabReaccion)
        tabTablas = findViewById(R.id.tabTablas)
        tabNosotros = findViewById(R.id.tabNosotros)

        panelNews = findViewById(R.id.panelNews)
        panelEntradas = findViewById(R.id.panelEntradas)
        panelReaccion = findViewById(R.id.panelReaccion)
        panelTablas = findViewById(R.id.panelTablas)
        entradasUi = EntradasUi(this, findViewById(R.id.entradasPanelRoot))
        entradasUi.bind()
        reaccionUi = ReaccionUi(this, findViewById(R.id.reaccionPanelRoot))
        txtSubtituloClasificacion = findViewById(R.id.txtSubtituloClasificacion)
        txtActualizacionClasificacion = findViewById(R.id.txtActualizacionClasificacion)

        imgFeatured = findViewById(R.id.imgFeaturedNews)
        txtFeaturedBadge = findViewById(R.id.txtFeaturedBadge)
        txtFeaturedTitle = findViewById(R.id.txtFeaturedTitle)
        cardFeatured = findViewById(R.id.cardFeaturedNews)

        clasificacionAdapter = ClasificacionAdapter(onClick = ::abrirDetalleDesdeRanking)
        findViewById<RecyclerView>(R.id.recyclerClasificacion).apply {
            layoutManager = LinearLayoutManager(this@HubActivity)
            adapter = clasificacionAdapter
            setHasFixedSize(true)
        }

        newsAdapter = NewsAdapter(onClick = ::abrirNoticia)
        findViewById<RecyclerView>(R.id.recyclerNews).apply {
            layoutManager = GridLayoutManager(this@HubActivity, 2)
            adapter = newsAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<TextView>(R.id.txtTituloClasificacion).setOnClickListener {
            startActivity(Intent(this, PilotosActivity::class.java))
        }
        val irEntradas = View.OnClickListener { mostrarTab("entradas") }
        findViewById<View>(R.id.bloqueEvento).setOnClickListener(irEntradas)
        findViewById<View>(R.id.btnEntradas).setOnClickListener(irEntradas)
        findViewById<View>(R.id.btnProfile).setOnClickListener {
            MenuLateral.mostrar(this, tabActual) { seccion ->
                if (seccion == "nosotros") abrirNosotros() else mostrarTab(seccion)
            }
        }

        tabNews.setOnClickListener { mostrarTab("news") }
        tabEntradas.setOnClickListener { mostrarTab("entradas") }
        tabReaccion.setOnClickListener { mostrarTab("reaccion") }
        tabTablas.setOnClickListener { mostrarTab("tablas") }
        tabNosotros.setOnClickListener { abrirNosotros() }

        val tabInicial = intent.getStringExtra(EXTRA_TAB) ?: "news"
        mostrarTab(tabInicial)
        cargarDesdeApi()
    }

    /** Vuelta desde Mercado Pago con el Hub ya abierto (PagoMpActivity usa SINGLE_TOP). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TAB)?.let(::mostrarTab)
    }

    override fun onResume() {
        super.onResume()
        if (::entradasUi.isInitialized) entradasUi.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::reaccionUi.isInitialized) reaccionUi.cancelar()
    }

    private fun cargarDesdeApi() {
        lifecycleScope.launch {
            try {
                val tabla = F1Repository.getTabla()
                clasificacionAdapter.submitList(
                    if (tabla.items.isNotEmpty()) tabla.items else rankingFallback()
                )
                txtSubtituloClasificacion.text =
                    "Temporada ${tabla.temporada} · Tocá un piloto para ver su ficha"
                txtActualizacionClasificacion.text = when {
                    tabla.ultimaCarrera != null ->
                        "Actualizado tras el ${tabla.ultimaCarrera} ${tabla.temporada} · Fecha ${tabla.ronda}"
                    tabla.ronda > 0 -> "Actualizado tras la fecha ${tabla.ronda} de ${tabla.temporada}"
                    else -> "Temporada ${tabla.temporada}"
                }
            } catch (error: Exception) {
                error.registrar("Cargar clasificación (se muestran datos de referencia)")
                clasificacionAdapter.submitList(rankingFallback())
                txtSubtituloClasificacion.text =
                    "No pudimos actualizar la tabla · mostrando la última disponible"
                txtActualizacionClasificacion.text = "Datos de referencia sin conexión"
            }

            try {
                val news = F1Repository.getNews()
                if (news.isNotEmpty()) {
                    bindNews(news)
                } else {
                    bindNews(newsFallback())
                }
            } catch (error: Exception) {
                error.registrar("Cargar noticias (se muestran noticias de referencia)")
                bindNews(newsFallback())
            }
        }
    }

    private fun bindNews(news: List<NewsApi>) {
        val featured = news.firstOrNull { it.featured } ?: news.firstOrNull()
        val rest = if (featured != null) {
            news.filter { it.id != featured.id }
        } else {
            news
        }

        if (featured != null) {
            cardFeatured.visibility = View.VISIBLE
            cardFeatured.setOnClickListener { abrirNoticia(featured) }
            txtFeaturedTitle.text = featured.title
            if (featured.badge.isNullOrBlank()) {
                txtFeaturedBadge.visibility = View.GONE
            } else {
                txtFeaturedBadge.visibility = View.VISIBLE
                txtFeaturedBadge.text = featured.badge
            }
            MediaHelper.loadFlexible(
                imgFeatured,
                featured.imageUrl,
                R.drawable.news_featured
            )
        } else {
            cardFeatured.visibility = View.GONE
        }

        newsAdapter.submitList(rest)
    }

    /** Detalle de una noticia: imagen, categoría y resumen en el modal de la app. */
    private fun abrirNoticia(noticia: NewsApi) {
        val density = resources.displayMetrics.density
        val contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                ImageView(this@HubActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = noticia.title
                    MediaHelper.loadFlexible(this, noticia.imageUrl, R.drawable.news_featured)
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt())
            )
            addView(TextView(this@HubActivity).apply {
                text = listOfNotNull(noticia.category, noticia.badge?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                    .uppercase()
                setTextColor(Color.parseColor("#E10600"))
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, (12 * density).toInt(), 0, (6 * density).toInt())
            })
            addView(TextView(this@HubActivity).apply {
                text = noticia.summary?.takeIf { it.isNotBlank() }
                    ?: "Todavía no hay más detalles de esta noticia. Pronto vas a poder leer la nota completa."
                setTextColor(Color.parseColor("#D0D0D0"))
                textSize = 14f
                setLineSpacing(0f, 1.15f)
            })
        }
        AppDialog.mostrar(
            this,
            AppDialog.Tipo.INFO,
            noticia.title,
            botonPrincipal = "CERRAR",
            contenido = contenido
        )
    }

    private fun abrirDetalleDesdeRanking(item: ClasificacionItem) {
        val intent = Intent(this, DetallePilotoActivity::class.java).apply {
            putExtra(DetallePilotoActivity.EXTRA_EQUIPO, item.equipo.uppercase())
            putExtra(
                DetallePilotoActivity.EXTRA_NOMBRE,
                "${item.firstName.uppercase()}\n${item.lastName.uppercase()}"
            )
            putExtra(
                DetallePilotoActivity.EXTRA_INFO,
                "#${item.driverNumber} · ${item.country}"
            )
            putExtra(DetallePilotoActivity.EXTRA_CAMPEONATOS, item.championships.toString())
            putExtra(DetallePilotoActivity.EXTRA_VICTORIAS, item.wins.toString())
            putExtra(DetallePilotoActivity.EXTRA_PODIOS, item.podiums.toString())
            putExtra(
                DetallePilotoActivity.EXTRA_RECORD,
                "${item.equipo} · ${item.puntos} pts · P${item.posicion}"
            )
            putExtra(DetallePilotoActivity.EXTRA_FOTO, MediaHelper.driverPhotoRes(item.code))
            putExtra(DetallePilotoActivity.EXTRA_FOTO_URL, item.photoUrl)
            putExtra(DetallePilotoActivity.EXTRA_CODE, item.code)
        }
        startActivity(intent)
    }

    private fun rankingFallback(): List<ClasificacionItem> = listOf(
        ClasificacionItem(1, "ANTONELLI", "Mercedes", 219, "#00D2BE", "same", firstName = "Kimi", lastName = "Antonelli", driverNumber = 12, country = "Italia", code = "ANT"),
        ClasificacionItem(2, "HAMILTON", "Ferrari", 169, "#E8002D", "same", firstName = "Lewis", lastName = "Hamilton", driverNumber = 44, country = "Reino Unido", code = "HAM", championships = 7, wins = 105, podiums = 202),
        ClasificacionItem(3, "RUSSELL", "Mercedes", 160, "#00D2BE", "same", firstName = "George", lastName = "Russell", driverNumber = 63, country = "Reino Unido", code = "RUS", wins = 3, podiums = 15),
        ClasificacionItem(4, "LECLERC", "Ferrari", 138, "#E8002D", "same", firstName = "Charles", lastName = "Leclerc", driverNumber = 16, country = "Mónaco", code = "LEC", wins = 8, podiums = 43),
        ClasificacionItem(5, "NORRIS", "McLaren", 128, "#FF8000", "same", firstName = "Lando", lastName = "Norris", driverNumber = 4, country = "Reino Unido", code = "NOR", wins = 5, podiums = 30),
        ClasificacionItem(6, "VERSTAPPEN", "Red Bull", 109, "#3671C6", "up", firstName = "Max", lastName = "Verstappen", driverNumber = 1, country = "Países Bajos", code = "VER", championships = 4, wins = 62, podiums = 109),
        ClasificacionItem(7, "PIASTRI", "McLaren", 92, "#FF8000", "down", firstName = "Oscar", lastName = "Piastri", driverNumber = 81, country = "Australia", code = "PIA", wins = 4, podiums = 13),
        ClasificacionItem(8, "SAINZ", "Williams", 74, "#64C4FF", "same", firstName = "Carlos", lastName = "Sainz", driverNumber = 55, country = "España", code = "SAI", wins = 4, podiums = 27),
        ClasificacionItem(9, "ALONSO", "Aston Martin", 58, "#229971", "up", firstName = "Fernando", lastName = "Alonso", driverNumber = 14, country = "España", code = "ALO", championships = 2, wins = 32, podiums = 106),
        ClasificacionItem(10, "GASLY", "Alpine", 41, "#FF87BC", "down", firstName = "Pierre", lastName = "Gasly", driverNumber = 10, country = "Francia", code = "GAS", wins = 1, podiums = 5)
    )

    private fun newsFallback(): List<NewsApi> = listOf(
        NewsApi(1, "Autódromo Oscar y Juan Gálvez: la Catedral del Automovilismo argentino", badge = "UNLOCKED", featured = true, imageUrl = "news_featured",
            summary = "El autódromo porteño es el templo del automovilismo nacional. Por su asfalto pasaron Fangio, Reutemann y las principales categorías del país, y se prepara para volver a recibir competencias internacionales."),
        NewsApi(2, "Inaugurado en 1952, fue sede de 20 Grandes Premios de F1", badge = "UNLOCKED", imageUrl = "news_galvez",
            summary = "Desde su inauguración en 1952, el Gálvez recibió 20 Grandes Premios de Fórmula 1 entre 1953 y 1998. Juan Manuel Fangio ganó cuatro veces en casa, la marca más alta de un piloto local."),
        NewsApi(3, "MotoGP vuelve a Buenos Aires en 2027, tras 28 años de ausencia", imageUrl = "news_motogp", category = "MotoGP",
            summary = "El Mundial de motociclismo regresa a la Ciudad de Buenos Aires. La última vez que MotoGP corrió en el Gálvez fue en 1999, y la fecha de 2027 ya está confirmada en el calendario."),
        NewsApi(4, "Remodelación de u\$s100M para volver a estándares FIA", imageUrl = "news_featured",
            summary = "La obra incluye repavimentación completa, nuevas zonas de escape, boxes modernos y una torre de control renovada para cumplir con la homologación que exige la FIA para categorías internacionales."),
        NewsApi(5, "190 hectáreas en Villa Riachuelo, Buenos Aires", imageUrl = "news_galvez",
            summary = "El predio ocupa unas 190 hectáreas en el sur de la Ciudad. Además del circuito principal tiene varias configuraciones de trazado, lagos y espacios verdes que se usan para eventos durante todo el año.")
    )

    private fun abrirNosotros() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun mostrarTab(tab: String) {
        tabActual = tab
        resetTabs()
        when (tab) {
            "news" -> {
                tabNews.setBackgroundResource(R.drawable.bg_tab_active)
                panelNews.visibility = View.VISIBLE
            }
            "entradas" -> {
                tabEntradas.setBackgroundResource(R.drawable.bg_tab_active)
                panelEntradas.visibility = View.VISIBLE
            }
            "reaccion" -> {
                tabReaccion.setBackgroundResource(R.drawable.bg_tab_active)
                panelReaccion.visibility = View.VISIBLE
            }
            else -> {
                tabTablas.setBackgroundResource(R.drawable.bg_tab_active)
                panelTablas.visibility = View.VISIBLE
            }
        }
    }

    private fun resetTabs() {
        if (::reaccionUi.isInitialized) reaccionUi.cancelar()
        tabNews.setBackgroundResource(R.drawable.bg_tab_inactive)
        tabEntradas.setBackgroundResource(R.drawable.bg_tab_inactive)
        tabReaccion.setBackgroundResource(R.drawable.bg_tab_inactive)
        tabTablas.setBackgroundResource(R.drawable.bg_tab_inactive)
        tabNosotros.setBackgroundResource(R.drawable.bg_tab_inactive)
        panelNews.visibility = View.GONE
        panelEntradas.visibility = View.GONE
        panelReaccion.visibility = View.GONE
        panelTablas.visibility = View.GONE
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
