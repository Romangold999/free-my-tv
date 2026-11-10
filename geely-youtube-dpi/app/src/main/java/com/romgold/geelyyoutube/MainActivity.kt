package com.romgold.geelyyoutube

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.content.Context

class MainActivity : Activity() {
    private lateinit var profileName: EditText
    private lateinit var arguments: EditText
    private lateinit var status: TextView
    private lateinit var profilePicker: Spinner
    private val prefs by lazy { getSharedPreferences("dpi_profiles", Context.MODE_PRIVATE) }

    private val defaultArgs = "-Ku -a3 -An -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -Mh,d -As -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -d30+s -d35+s -S -Mh,d"

    private fun names(): MutableList<String> =
        prefs.getStringSet("profile_names", emptySet())!!.toMutableList().sorted().toMutableList()

    private fun refreshPicker(select: String? = null) {
        val list = names()
        profilePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, list)
        if (select != null) {
            val index = list.indexOf(select)
            if (index >= 0) profilePicker.setSelection(index)
        }
    }

    private fun loadProfile(name: String) {
        profileName.setText(name)
        arguments.setText(prefs.getString("profile:$name", defaultArgs))
        prefs.edit().putString("active_profile", name).apply()
    }

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
        root.addView(text("Локальные профили ByeDPI", 15f))

        profilePicker = Spinner(this)
        root.addView(profilePicker, LinearLayout.LayoutParams(-1, 56))
        profileName = EditText(this).apply {
            hint = "Название профиля"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
        }
        root.addView(profileName, LinearLayout.LayoutParams(-1, 60))
        arguments = EditText(this).apply {
            hint = "Аргументы ByeDPI"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            minLines = 3; gravity = Gravity.TOP
        }
        root.addView(arguments, LinearLayout.LayoutParams(-1, 120))

        profilePicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val chosen = profilePicker.selectedItem?.toString() ?: return
                if (chosen != prefs.getString("active_profile", null)) loadProfile(chosen)
            }
        }

        val save = Button(this).apply {
            text = "СОХРАНИТЬ / ОБНОВИТЬ ПРОФИЛЬ"
            setOnClickListener {
                val name = profileName.text.toString().trim()
                if (name.isEmpty()) {
                    status.text = "Укажи название профиля"
                } else {
                    val set = prefs.getStringSet("profile_names", emptySet())!!.toMutableSet()
                    set.add(name)
                    prefs.edit().putStringSet("profile_names", set)
                        .putString("profile:$name", arguments.text.toString())
                        .putString("active_profile", name).apply()
                    refreshPicker(name)
                    status.text = "Профиль «$name» сохранён"
                }
            }
        }
        root.addView(save, LinearLayout.LayoutParams(-1, 64))

        val open = Button(this).apply {
            text = "ОТКРЫТЬ YOUTUBE"
            setOnClickListener {
                status.text = "YouTube-движок ещё не подключён. Эта кнопка пока не запускает видео."
            }
        }
        root.addView(open, LinearLayout.LayoutParams(-1, 64))
        status = text("Профили сохраняются отдельно на устройстве.", 14f)
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })

        val initial = names()
        if (initial.isEmpty()) {
            val name = "Мой профиль"
            prefs.edit().putStringSet("profile_names", setOf(name))
                .putString("profile:$name", defaultArgs)
                .putString("active_profile", name).apply()
        }
        refreshPicker(prefs.getString("active_profile", "Мой профиль"))
        loadProfile(prefs.getString("active_profile", "Мой профиль")!!)
    }
}
