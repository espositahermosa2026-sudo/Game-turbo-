package com.tuusuario.gameturbo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class GameApp(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

object GameLibrary {

    fun getInstalledGames(context: Context): List<GameApp> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        return apps.filter { app ->
            val isGame = if (android.os.Build.VERSION.SDK_INT >= 26) {
                app.category == ApplicationInfo.CATEGORY_GAME
            } else {
                (app.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
            val hasLaunchIntent = pm.getLaunchIntentForPackage(app.packageName) != null
            isGame && hasLaunchIntent
        }.map { app ->
            GameApp(
                name = pm.getApplicationLabel(app).toString(),
                packageName = app.packageName,
                icon = pm.getApplicationIcon(app)
            )
        }.sortedBy { it.name }
    }
}
