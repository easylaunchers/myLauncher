package com.my.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class AppItem(val label: String, val intent: Intent, val icon: Drawable)

class MainActivity : Activity() {
    private lateinit var bg: ImageView
    private lateinit var list: RecyclerView
    private val adapter = AppAdapter()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = loadApps()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        bg = findViewById(R.id.bg)
        list = findViewById(R.id.list)
        list.layoutManager = GridLayoutManager(this, 6)
        list.adapter = adapter
        findViewById<View>(R.id.btnWall).setOnClickListener { pickWallpaper() }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            File(filesDir, "wallpaper.jpg").delete(); applyWallpaper()
        }
        findViewById<View>(R.id.btnScreen).setOnClickListener { openScreenSettings() }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
        }
        registerReceiver(receiver, f)
        applyWallpaper()
        loadApps()
    }

    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
    override fun onBackPressed() {}
    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); list.scrollToPosition(0) }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val seen = HashSet<String>()
            val res = ArrayList<AppItem>()
            for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(q, 0)) {
                    val p = ri.activityInfo.packageName
                    if (p == packageName || !seen.add(p)) continue
                    val li = Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(p, ri.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    res.add(AppItem(ri.loadLabel(pm).toString(), li, ri.loadIcon(pm)))
                }
            }
            res.sortBy { it.label.lowercase() }
            runOnUiThread {
                adapter.items = res
                adapter.notifyDataSetChanged()
                list.post { list.getChildAt(0)?.requestFocus() }
            }
        }.start()
    }

    fun launch(v: View, a: AppItem) {
        try {
            val o = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
            startActivity(a.intent, o.toBundle())
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openScreenSettings() {
        for (a in listOf(Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS)) {
            try { startActivity(Intent(a)); return } catch (_: Exception) {}
        }
        Toast.makeText(this, "Настройки не найдены", Toast.LENGTH_SHORT).show()
    }

    private fun pickWallpaper() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 1)
        } catch (e: Exception) {
            Toast.makeText(this, "Нет выбора файлов", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req != 1 || res != RESULT_OK) return
        Thread {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (o.outWidth / (s * 2) >= 1920) s *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return@Thread
                File(filesDir, "wallpaper.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                runOnUiThread { applyWallpaper() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Не удалось загрузить картинку", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun applyWallpaper() {
        val f = File(filesDir, "wallpaper.jpg")
        if (!f.exists()) { bg.setImageResource(R.drawable.wallpaper_mountains); return }
        Thread {
            val bmp = BitmapFactory.decodeFile(f.path)
            runOnUiThread { if (bmp != null) bg.setImageBitmap(bmp) }
        }.start()
    }

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.VH>() {
        var items: List<AppItem> = emptyList()
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val label: TextView = v.findViewById(R.id.label)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val a = items[pos]
            h.icon.setImageDrawable(a.icon)
            h.label.text = a.label
            h.itemView.setOnClickListener { launch(it, a) }
            h.itemView.setOnFocusChangeListener { v, focused ->
                v.animate().scaleX(if (focused) 1.1f else 1f).scaleY(if (focused) 1.1f else 1f).setDuration(120).start()
            }
        }
    }
}
