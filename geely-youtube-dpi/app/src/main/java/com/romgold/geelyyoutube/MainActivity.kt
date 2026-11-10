package com.romgold.geelyyoutube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.Executors
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.Context
import android.content.Intent
import android.util.Base64

class MainActivity : Activity() {
    private lateinit var profileName: EditText
    private lateinit var arguments: EditText
    private lateinit var status: TextView
    private lateinit var profilePicker: Spinner
    private lateinit var searchQuery: EditText
    private lateinit var searchResults: LinearLayout
    private lateinit var homeRecent: LinearLayout
    private lateinit var libraryList: LinearLayout
    private lateinit var pageHost: FrameLayout
    private val pages = LinkedHashMap<String, View>()
    private val navButtons = LinkedHashMap<String, Button>()
    private val extractorExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("dpi_profiles", Context.MODE_PRIVATE) }

    private val defaultArgs = "-Ku -a3 -An -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -Mh,d -As -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -d30+s -d35+s -S -Mh,d"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.rgb(29, 36, 47))
            cornerRadius = dp(18).toFloat()
        }
    }

    private fun label(value: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun actionButton(value: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = value
            isAllCaps = false
            textSize = 14f
            setOnClickListener { onClick() }
        }

    private fun addCard(parent: LinearLayout, child: View, top: Int = 10) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun scrollPage(): Pair<ScrollView, LinearLayout> {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (resources.configuration.screenWidthDp >= 600) 28 else 16), dp(14), dp(if (resources.configuration.screenWidthDp >= 600) 28 else 16), dp(24))
        }
        return ScrollView(this).apply { isFillViewport = true; addView(body) } to body
    }

    private fun addPage(key: String, page: View) {
        pages[key] = page
        pageHost.addView(page, FrameLayout.LayoutParams(-1, -1))
    }

    private fun showPage(key: String) {
        pages.forEach { (name, view) -> view.visibility = if (name == key) View.VISIBLE else View.GONE }
        navButtons.forEach { (name, button) -> button.alpha = if (name == key) 1f else 0.62f }
        if (key == "library") refreshHistory(libraryList)
        if (key == "home") refreshHistory(homeRecent, 4)
    }

    private fun buildHomePage(): View {
        val (scroll, body) = scrollPage()
        val hero = card()
        hero.addView(label("GEELY YOUTUBE", 13f, Color.rgb(255, 90, 70), true))
        hero.addView(label("Видео без лишнего", 27f, Color.WHITE, true))
        hero.addView(label("Поиск и воспроизведение через встроенный ByeDPI. Начни с поиска или вернись к недавно просмотренным видео.", 15f, Color.LTGRAY))
        hero.addView(actionButton("НАЙТИ ВИДЕО") { showPage("search"); searchQuery.requestFocus() })
        addCard(body, hero, 0)

        val proxyCard = card()
        proxyCard.addView(label("СОЕДИНЕНИЕ", 13f, Color.LTGRAY, true))
        proxyCard.addView(label("Локальный SOCKS5: 127.0.0.1:1080", 16f, Color.WHITE, true))
        proxyCard.addView(label("Состояние: " + if (ByeDpiService.isProxyRunning()) "активен" else "не запущен", 14f, Color.LTGRAY))
        proxyCard.addView(actionButton("НАСТРОИТЬ BYEDPI") { showPage("profile") })
        addCard(body, proxyCard)

        val recent = card()
        recent.addView(label("НЕДАВНО ПРОСМОТРЕНО", 13f, Color.LTGRAY, true))
        homeRecent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        recent.addView(homeRecent)
        recent.addView(actionButton("ВСЯ ИСТОРИЯ") { showPage("library") })
        addCard(body, recent)
        return scroll
    }

    private fun buildSearchPage(): View {
        val (scroll, body) = scrollPage()
        body.addView(label("Поиск", 27f, Color.WHITE, true))
        body.addView(label("Ищи видео на YouTube. Для сетевых запросов сначала запусти ByeDPI.", 14f, Color.LTGRAY))
        searchQuery = EditText(this).apply {
            hint = "Название видео или запрос"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            singleLine = true
            textSize = 16f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(35, 43, 55))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(74, 87, 105))
            }
        }
        addCard(body, searchQuery, 12)
        addCard(body, actionButton("ИСКАТЬ НА YOUTUBE") { performYoutubeSearch() }, 8)
        searchResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addCard(body, searchResults, 12)
        return scroll
    }

    private fun buildSubscriptionsPage(): View {
        val (scroll, body) = scrollPage()
        val c = card()
        c.addView(label("Подписки", 26f, Color.WHITE, true))
        c.addView(label("Раздел подготовлен, но подключение Google/YouTube-аккаунта ещё не реализовано.", 16f, Color.LTGRAY))
        c.addView(label("Не вводи пароль Google в это приложение. Без официального безопасного входа синхронизированные подписки недоступны.", 14f, Color.rgb(255, 190, 110)))
        addCard(body, c, 0)
        return scroll
    }

    private fun buildLibraryPage(): View {
        val (scroll, body) = scrollPage()
        body.addView(label("Библиотека", 27f, Color.WHITE, true))
        body.addView(label("Локальная история просмотров на этом устройстве.", 14f, Color.LTGRAY))
        libraryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addCard(body, libraryList, 8)
        addCard(body, actionButton("ОЧИСТИТЬ ИСТОРИЮ") {
            prefs.edit().remove("watch_history").apply()
            refreshHistory(libraryList)
            refreshHistory(homeRecent, 4)
        }, 8)
        return scroll
    }

    private fun buildProfilePage(): View {
        val (scroll, body) = scrollPage()
        body.addView(label("Профиль и соединение", 25f, Color.WHITE, true))
        val local = card()
        local.addView(label("ЛОКАЛЬНЫЕ ПРОФИЛИ BYEDPI", 13f, Color.LTGRAY, true))
        profilePicker = Spinner(this)
        local.addView(profilePicker, LinearLayout.LayoutParams(-1, dp(52)))
        profileName = EditText(this).apply {
            hint = "Название профиля"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); singleLine = true
        }
        local.addView(profileName, LinearLayout.LayoutParams(-1, dp(54)))
        arguments = EditText(this).apply {
            hint = "Аргументы ByeDPI"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            minLines = 4; gravity = Gravity.TOP; textSize = 13f
        }
        local.addView(arguments, LinearLayout.LayoutParams(-1, dp(124)))
        profilePicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val chosen = profilePicker.selectedItem?.toString() ?: return
                if (chosen != prefs.getString("active_profile", null)) loadProfile(chosen)
            }
        }
        local.addView(actionButton("СОХРАНИТЬ ПРОФИЛЬ") {
            val name = profileName.text.toString().trim()
            if (name.isEmpty()) status.text = "Укажи название профиля"
            else {
                val set = prefs.getStringSet("profile_names", emptySet())!!.toMutableSet()
                set.add(name)
                prefs.edit().putStringSet("profile_names", set)
                    .putString("profile:$name", arguments.text.toString())
                    .putString("active_profile", name).apply()
                refreshPicker(name)
                status.text = "Профиль «$name» сохранён"
            }
        })
        local.addView(actionButton("ЗАПУСТИТЬ BYEDPI") {
            val intent = Intent(this, ByeDpiService::class.java)
                .setAction(ByeDpiService.ACTION_START)
                .putExtra(ByeDpiService.EXTRA_ARGUMENTS, arguments.text.toString())
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            status.text = "Запрос на запуск ByeDPI отправлен"
        })
        local.addView(actionButton("ОСТАНОВИТЬ BYEDPI") {
            startService(Intent(this, ByeDpiService::class.java).setAction(ByeDpiService.ACTION_STOP))
            status.text = "Остановка ByeDPI запрошена"
        })
        addCard(body, local, 8)

        val account = card()
        account.addView(label("АККАУНТ YOUTUBE", 13f, Color.LTGRAY, true))
        account.addView(label("Официальный безопасный вход Google пока не подключён. Пароли приложение не запрашивает и не хранит.", 14f, Color.rgb(255, 190, 110)))
        addCard(body, account)
        return scroll
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

    private fun encode(value: String): String = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun decode(value: String): String = try { String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8) } catch (_: Exception) { "" }

    private fun recordHistory(title: String, url: String) {
        val existing = prefs.getString("watch_history", "").orEmpty().split("\n").filter { it.isNotBlank() }
        val item = encode(title) + "|" + encode(url)
        val updated = (listOf(item) + existing.filterNot { it == item }).take(100)
        prefs.edit().putString("watch_history", updated.joinToString("\n")).apply()
    }

    private fun refreshHistory(target: LinearLayout, limit: Int = 100) {
        target.removeAllViews()
        val entries = prefs.getString("watch_history", "").orEmpty().split("\n").filter { it.contains("|") }.take(limit)
        if (entries.isEmpty()) {
            target.addView(label("Здесь пока пусто. Запусти поиск и открой видео.", 14f, Color.LTGRAY))
            return
        }
        entries.forEachIndexed { index, raw ->
            val parts = raw.split("|", limit = 2)
            if (parts.size == 2) {
                val title = decode(parts[0])
                val url = decode(parts[1])
                val row = actionButton("${index + 1}. $title") { playYoutubeVideo(url, title) }
                row.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                target.addView(row, LinearLayout.LayoutParams(-1, dp(54)))
            }
        }
    }

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
                        val entry = actionButton("${index + 1}. ${item.name}") {
                            playYoutubeVideo(item.url, item.name)
                        }
                        entry.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        searchResults.addView(entry, LinearLayout.LayoutParams(-1, dp(62)).apply {
                            bottomMargin = dp(5)
                        })
                    }
                    status.text = "Найдено: ${info.relatedItems.size}"
                }
            } catch (error: Throwable) {
                runOnUiThread { status.text = "Ошибка поиска: ${error.message ?: error.javaClass.simpleName}" }
            }
        }
    }

    private fun playYoutubeVideo(url: String, title: String) {
        if (!ByeDpiService.isProxyRunning()) {
            status.text = "Сначала запусти ByeDPI. Видео загружается только через SOCKS5 127.0.0.1:1080."
            showPage("profile")
            return
        }
        status.text = "Получаем видеопоток: $title"
        extractorExecutor.execute {
            try {
                val info = StreamInfo.getInfo(url)
                val stream = info.videoStreams.firstOrNull { !it.isVideoOnly }
                val streamUrl = stream?.content
                if (streamUrl.isNullOrBlank()) {
                    throw IllegalStateException("Не найден видеопоток со звуком в одном файле")
                }
                recordHistory(title, url)
                runOnUiThread {
                    refreshHistory(homeRecent, 4)
                    refreshHistory(libraryList)
                    startActivity(Intent(this, VideoPlayerActivity::class.java)
                        .putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, streamUrl))
                }
            } catch (error: Throwable) {
                runOnUiThread { status.text = "Не удалось открыть видео: ${error.message ?: error.javaClass.simpleName}" }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(17, 22, 30))
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        header.addView(label("GEELY  •  YOUTUBE", 21f, Color.WHITE, true))
        header.addView(label("Лёгкий клиент с локальным ByeDPI", 12f, Color.LTGRAY))
        root.addView(header)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val navItems = listOf(
            "home" to "Главная",
            "search" to "Поиск",
            "subscriptions" to "Подписки",
            "library" to "Библиотека",
            "profile" to "Профиль"
        )
        navItems.forEach { (key, title) ->
            val b = actionButton(title) { showPage(key) }
            b.textSize = if (resources.configuration.screenWidthDp < 380) 10f else 12f
            b.setPadding(dp(2), 0, dp(2), 0)
            navButtons[key] = b
            nav.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(nav)

        pageHost = FrameLayout(this)
        root.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))

        status = label("Готово. Запусти ByeDPI в разделе «Профиль».", 12f, Color.LTGRAY)
        status.setPadding(dp(12), dp(5), dp(12), dp(5))
        root.addView(status)
        setContentView(root)

        addPage("home", buildHomePage())
        addPage("search", buildSearchPage())
        addPage("subscriptions", buildSubscriptionsPage())
        addPage("library", buildLibraryPage())
        addPage("profile", buildProfilePage())

        val initial = names()
        if (initial.isEmpty()) {
            val name = "Мой профиль"
            prefs.edit().putStringSet("profile_names", setOf(name))
                .putString("profile:$name", defaultArgs)
                .putString("active_profile", name).apply()
        }
        refreshPicker(prefs.getString("active_profile", "Мой профиль"))
        loadProfile(prefs.getString("active_profile", "Мой профиль")!!)
        showPage("home")
        NewPipe.init(ByeDpiExtractorDownloader())
    }

    override fun onDestroy() {
        extractorExecutor.shutdownNow()
        super.onDestroy()
    }
}
