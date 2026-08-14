package com.example.activiti_main.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {

    @Query("SELECT * FROM clasificacion ORDER BY posicion ASC")
    suspend fun getClasificacion(): List<PilotoRanking>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasificacion(items: List<PilotoRanking>)

    @Query("SELECT COUNT(*) FROM clasificacion")
    suspend fun countClasificacion(): Int

    @Query("SELECT * FROM noticias ORDER BY destacada DESC, id ASC")
    suspend fun getNoticias(): List<Noticia>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoticias(items: List<Noticia>)

    @Query("SELECT COUNT(*) FROM noticias")
    suspend fun countNoticias(): Int
}
