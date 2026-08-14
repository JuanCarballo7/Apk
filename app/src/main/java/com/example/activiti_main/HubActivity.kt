package com.example.activiti_main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var tabVideos: TextView
    private lateinit var tabTablas: TextView
    private lateinit var tabNosotros: TextView

    private lateinit var panelNews: View
    private lateinit var panelTablas: View
    private lateinit var panelPlaceholder: View
    private lateinit var txtPlaceholder: TextView

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
        tabVideos = findViewById(R.id.tabVideos)
        tabTablas = findViewById(R.id.tabTablas)
        tabNosotros = findViewById(R.id.tabNosotros)

        panelNews = findViewById(R.id.panelNews)
        panelTablas = findViewById(R.id.panelTablas)
        panelPlaceholder = findViewById(R.id.panelPlaceholder)
        txtPlaceholder = findViewById(R.id.txtPlaceholder)

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

        newsAdapter = NewsAdapter()
        findViewById<RecyclerView>(R.id.recyclerNews).apply {
            layoutManager = GridLayoutManager(this@HubActivity, 2)
            adapter = newsAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<TextView>(R.id.txtTituloClasificacion).setOnClickListener {
            startActivity(Intent(this, PilotosActivity::class.java))
        }
        findViewById<View>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        tabNews.setOnClickListener { mostrarTab("news") }
        tabVideos.setOnClickListener { mostrarTab("videos") }
        tabTablas.setOnClickListener { mostrarTab("tablas") }
        tabNosotros.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        val tabInicial = intent.getStringExtra(EXTRA_TAB) ?: "news"
        mostrarTab(tabInicial)
        cargarDesdeApi()
    }

    private fun cargarDesdeApi() {
        lifecycleScope.launch {
            try {
                val standings = F1Repository.getStandings()
                clasificacionAdapter.submitList(
                    if (standings.isNotEmpty()) standings else rankingFallback()
                )
            } catch (e: Exception) {
                clasificacionAdapter.submitList(rankingFallback())
                Toast.makeText(
                    this@HubActivity,
                    "Clasificación local (API no disponible)",
                    Toast.LENGTH_SHORT
                ).show()
            }

            try {
                val news = F1Repository.getNews()
                if (news.isNotEmpty()) {
                    bindNews(news)
                } else {
                    bindNews(newsFallback())
                }
            } catch (_: Exception) {
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
        NewsApi(1, "Autódromo Oscar y Juan Gálvez: la Catedral del Automovilismo argentino", badge = "UNLOCKED", featured = true, imageUrl = "news_featured"),
        NewsApi(2, "Inaugurado en 1952, fue sede de 20 Grandes Premios de F1", badge = "UNLOCKED", imageUrl = "news_galvez"),
        NewsApi(3, "MotoGP vuelve a Buenos Aires en 2027, tras 28 años de ausencia", imageUrl = "news_motogp"),
        NewsApi(4, "Remodelación de u\$s100M para volver a estándares FIA", imageUrl = "news_featured"),
        NewsApi(5, "190 hectáreas en Villa Riachuelo, Buenos Aires", imageUrl = "news_galvez")
    )

    private fun mostrarTab(tab: String) {
        resetTabs()
        when (tab) {
            "news" -> {
                tabNews.setBackgroundResource(R.drawable.bg_tab_active)
                panelNews.visibility = View.VISIBLE
            }
            "videos" -> {
                tabVideos.setBackgroundResource(R.drawable.bg_tab_active)
                panelPlaceholder.visibility = View.VISIBLE
                txtPlaceholder.text = "Videos próximamente"
            }
            else -> {
                tabTablas.setBackgroundResource(R.drawable.bg_tab_active)
                panelTablas.visibility = View.VISIBLE
            }
        }
    }

    private fun resetTabs() {
        tabNews.setBackgroundResource(R.drawable.bg_tab_inactive)
        tabVideos.setBackgroundResource(R.drawable.bg_tab_inactive)
        tabTablas.setBackgroundResource(R.drawable.bg_tab_inactive)
        tabNosotros.setBackgroundResource(R.drawable.bg_tab_inactive)
        panelNews.visibility = View.GONE
        panelTablas.visibility = View.GONE
        panelPlaceholder.visibility = View.GONE
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
