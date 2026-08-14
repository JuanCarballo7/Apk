package com.example.activiti_main.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [PilotoRanking::class, Noticia::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "f1_app.db"
                ).build()

                INSTANCE = instance

                CoroutineScope(Dispatchers.IO).launch {
                    seed(instance.appDao())
                }

                instance
            }
        }
    }
}

suspend fun seed(dao: AppDao) {
    if (dao.countClasificacion() == 0) {
        dao.insertClasificacion(
            listOf(
                PilotoRanking(1, "ANTONELLI", "Mercedes", 219, "#00D2BE", "same"),
                PilotoRanking(2, "HAMILTON", "Ferrari", 169, "#E8002D", "same"),
                PilotoRanking(3, "RUSSELL", "Mercedes", 160, "#00D2BE", "same"),
                PilotoRanking(4, "LECLERC", "Ferrari", 138, "#E8002D", "same"),
                PilotoRanking(5, "NORRIS", "McLaren", 128, "#FF8000", "same"),
                PilotoRanking(6, "VERSTAPPEN", "Red Bull", 109, "#3671C6", "up"),
                PilotoRanking(7, "PIASTRI", "McLaren", 92, "#FF8000", "down"),
                PilotoRanking(8, "SAINZ", "Williams", 74, "#64C4FF", "same"),
                PilotoRanking(9, "ALONSO", "Aston Martin", 58, "#229971", "up"),
                PilotoRanking(10, "GASLY", "Alpine", 41, "#FF87BC", "down")
            )
        )
    }

    if (dao.countNoticias() == 0) {
        dao.insertNoticias(
            listOf(
                Noticia(
                    titulo = "Autódromo Oscar y Juan Gálvez: la Catedral del Automovilismo argentino",
                    etiqueta = "UNLOCKED",
                    destacada = true,
                    colorFondo = "#1B4332"
                ),
                Noticia(
                    titulo = "Inaugurado en 1952, fue sede de 20 Grandes Premios de F1",
                    etiqueta = null,
                    destacada = false,
                    colorFondo = "#2D6A4F"
                ),
                Noticia(
                    titulo = "MotoGP vuelve a Buenos Aires en 2027, tras 28 años de ausencia",
                    etiqueta = null,
                    destacada = false,
                    colorFondo = "#1D3557"
                ),
                Noticia(
                    titulo = "Remodelación de u\$s100M para volver a estándares FIA",
                    etiqueta = "UNLOCKED",
                    destacada = false,
                    colorFondo = "#40916C"
                ),
                Noticia(
                    titulo = "190 hectáreas en Villa Riachuelo, Buenos Aires",
                    etiqueta = null,
                    destacada = false,
                    colorFondo = "#52B788"
                )
            )
        )
    }
}
