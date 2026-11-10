package com.romgold.geelyyoutube

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.content.Context

class MainActivity : Activity() {
    private lateinit var profileName: EditText
    private lateinit var arguments: EditText
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("dpi_profiles", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 20)
            setBackgroundColor(Color.rgb(18, 22, 29))
        }
        fun text(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
        }
        root.addView(text("GEELY  •  YOUTUBE", 25f))
        root.addView(text("Клиент YouTube с локальным профилем ByeDPI", 15f))
        profileName = EditText(this).apply {
            hint = "Название профиля"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setSingleLine(true); setText(prefs.getString("name", "Мой профиль"))
        }
        root.addView(profileName, LinearLayout.LayoutParams(-1, 64))
        arguments = EditText(this).apply {
            hint = "Аргументы ByeDPI"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setText(prefs.getString("args", ""))
            minLines = 3; gravity = Gravity.TOP
        }
        root.addView(arguments, LinearLayout.LayoutParams(-1, 120))
        val save = Button(this).apply {
            text = "СОХРАНИТЬ ПРОФИЛЬ"
            setOnClickListener {
                prefs.edit().putString("name", profileName.text.toString())
                    .putString("args", arguments.text.toString()).apply()
                status.text = "Профиль сохранён на устройстве"
            }
        }
        root.addView(save, LinearLayout.LayoutParams(-1, 64))
        val open = Button(this).apply {
            text = "ОТКРЫТЬ YOUTUBE"
            setOnClickListener {
                status.text = "Видеодвижок NewPipe и проксирование ByeDPI ещё подключаются."
            }
        }
        root.addView(open, LinearLayout.LayoutParams(-1, 64))
        status = text("Этап 1: каркас приложения и локальное хранение профиля.", 14f)
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
