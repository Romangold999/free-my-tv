package com.romgold.geelyyoutube

import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.Executors

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
    private lateinit var searchQuery: EditText
    private lateinit var searchResults: LinearLayout
    private val extractorExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("dpi_profiles", Context.MODE_PRIVATE) }

    private val defaultArgs = "-Ku -a3 -An -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -Mh,d -As -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -d30+s -d35+s -S -Mh,d"

    private fun performYoutubeSearch() {
        val query = searchQuery.text.toString().trim()
        if (query.isEmpty()) {
            status.text = "Введи запрос для поиска"
            return
        }
        if (!ByeDpiService.isProxyRunning()) {
            status.text = "Сначала запусти ByeDPI: поиск идёт через SOCKS5 127.0.0.1:1080"
            return
        }
        status.text = "Ищем на YouTube: $query"
        searchResults.removeAllViews()
        extractorExecutor.execute {
            try {
                val searchHandler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
                val info = SearchInfo.getInfo(ServiceList.YouTube, searchHandler)
                runOnUiThread {
                    searchResults.removeAllViews()
                    info.relatedItems.forEachIndexed { index, item ->
                        val entry = Button(this).apply {
                            text = "${index + 1}. ${item.name}"
                            isAllCaps = false
                            setOnClickListener {
                                playYoutubeVideo(item.url, item.name)
                            }
                        }
                        searchResults.addView(entry, LinearLayout.LayoutParams(-1, 68))
                    }
                    status.text = "Результатов: ${info.relatedItems.size}. Нажми на видео для просмотра ссылки."
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    status.text = "Ошибка поиска: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    private fun playYoutubeVideo(url: String, title: String) {
        if (!ByeDpiService.isProxyRunning()) {
            status.text = "Сначала запусти ByeDPI. Видео будет загружаться только через SOCKS5 127.0.0.1:1080."
            return
        }
        status.text = "Получаем видеопоток: $title"
        extractorExecutor.execute {
            try {
                val info = StreamInfo.getInfo(url)
                val stream = info.videoStreams.firstOrNull { !it.isVideoOnly }
                val streamUrl = stream?.content
                if (streamUrl.isNullOrBlank()) {
                    throw IllegalStateException("Не найден видеопоток со звуком в одном файле. Раздельные DASH-потоки пока не поддерживаются.")
                }
                runOnUiThread {
                    status.text = "Открываем плеер: $title"
                    startActivity(android.content.Intent(this, VideoPlayerActivity::class.java)
                        .putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, streamUrl))
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    status.text = "Не удалось открыть видео: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

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

        val startProxy = Button(this).apply {
            text = "ЗАПУСТИТЬ BYEDPI"
            setOnClickListener {
                try {
                    val intent = android.content.Intent(this, ByeDpiService::class.java)
                        .setAction(ByeDpiService.ACTION_START)
                        .putExtra(ByeDpiService.EXTRA_ARGUMENTS, arguments.text.toString())
                    if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                    status.text = "Запрос на запуск ByeDPI отправлен. Проверяем состояние…"
                } catch (error: Exception) {
                    status.text = "Ошибка ByeDPI: ${error.message ?: "неизвестная ошибка"}"
                }
            }
        }
        root.addView(startProxy, LinearLayout.LayoutParams(-1, 64))

        val stopProxy = Button(this).apply {
            text = "ОСТАНОВИТЬ BYEDPI"
            setOnClickListener {
                val intent = android.content.Intent(this, ByeDpiService::class.java).setAction(ByeDpiService.ACTION_STOP)
                startService(intent)
                status.text = "Остановка ByeDPI запрошена"
            }
        }
        root.addView(stopProxy, LinearLayout.LayoutParams(-1, 64))

        val open = Button(this).apply {
            text = "ОТКРЫТЬ YOUTUBE"
            setOnClickListener {
                status.text = "YouTube-движок ещё не подключён. Эта кнопка пока не запускает видео."
            }
        }
        root.addView(open, LinearLayout.LayoutParams(-1, 64))

        searchQuery = EditText(this).apply {
            hint = "Поиск видео на YouTube"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setSingleLine(true)
        }
        root.addView(searchQuery, LinearLayout.LayoutParams(-1, 60))
        val searchButton = Button(this).apply {
            text = "ИСКАТЬ В YOUTUBE"
            setOnClickListener { performYoutubeSearch() }
        }
        root.addView(searchButton, LinearLayout.LayoutParams(-1, 64))
        searchResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(searchResults, LinearLayout.LayoutParams(-1, -2))
        status = text("Профили сохраняются отдельно на устройстве.", 14f)
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
        NewPipe.init(ByeDpiExtractorDownloader())

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
