package com.romgold.geelyyoutube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.Executors
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.content.Context
import android.content.Intent
import android.util.Base64
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.InetSocketAddress
import java.net.URL

class MainActivity : Activity() {
    private lateinit var profileName: EditText
    private lateinit var arguments: EditText
    private lateinit var status: TextView
    private lateinit var profilePicker: Spinner
    private lateinit var searchQuery: EditText
    private lateinit var searchResults: LinearLayout
    private lateinit var homeRecent: LinearLayout
    private lateinit var homeFeed: LinearLayout
    private lateinit var libraryList: LinearLayout
    private lateinit var subscriptionsList: LinearLayout
    private lateinit var channelTitle: TextView
    private lateinit var channelDescription: TextView
    private lateinit var channelVideos: LinearLayout
    private lateinit var channelSubscribeButton: Button
    private var currentChannelUrl: String = ""
    private var currentChannelName: String = ""
    private lateinit var pageHost: FrameLayout
    private val pages = LinkedHashMap<String, View>()
    private val navButtons = LinkedHashMap<String, Button>()
    private val extractorExecutor = Executors.newSingleThreadExecutor()
    private val thumbnailExecutor = Executors.newFixedThreadPool(3)
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
        if (key == "subscriptions") refreshSubscriptions()
        if (key == "home") {
            refreshHistory(homeRecent, 4)
            if (ByeDpiService.isProxyRunning()) loadHomeFeed()
        }
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

        val feed = card()
        feed.addView(label("ПОПУЛЯРНОЕ НА YOUTUBE", 13f, Color.LTGRAY, true))
        feed.addView(actionButton("ОБНОВИТЬ ЛЕНТУ") { loadHomeFeed() })
        homeFeed = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        feed.addView(homeFeed)
        addCard(body, feed)

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
            setSingleLine(true)
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
        c.addView(label("Подписки здесь хранятся локально. Вход Google и синхронизация аккаунта пока не подключены.", 14f, Color.rgb(255, 190, 110)))
        subscriptionsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(subscriptionsList)
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
            hint = "Название профиля"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY); setSingleLine(true)
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

    private fun buildChannelPage(): View {
        val (scroll, body) = scrollPage()
        channelTitle = label("Канал", 25f, Color.WHITE, true)
        channelDescription = label("", 13f, Color.LTGRAY)
        body.addView(channelTitle)
        body.addView(channelDescription)
        channelSubscribeButton = actionButton("ПОДПИСАТЬСЯ") {
            toggleSubscription(currentChannelName, currentChannelUrl)
        }
        body.addView(channelSubscribeButton)
        body.addView(label("Видео канала", 17f, Color.WHITE, true))
        channelVideos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(channelVideos)
        return scroll
    }

    private fun subscriptionEntries(): List<Pair<String, String>> {
        return prefs.getStringSet("local_subscriptions", emptySet()).orEmpty().mapNotNull { raw ->
            val parts = raw.split("|", limit = 2)
            if (parts.size != 2) null else decode(parts[0]) to decode(parts[1])
        }
    }

    private fun toggleSubscription(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) return
        val entries = prefs.getStringSet("local_subscriptions", emptySet())!!.toMutableSet()
        val encoded = encode(name) + "|" + encode(url)
        if (entries.contains(encoded)) {
            entries.remove(encoded)
            status.text = "Канал удалён из подписок"
        } else {
            entries.add(encoded)
            status.text = "Канал добавлен в подписки"
        }
        prefs.edit().putStringSet("local_subscriptions", entries).apply()
        updateChannelSubscriptionButton()
        refreshSubscriptions()
    }

    private fun updateChannelSubscriptionButton() {
        val subscribed = subscriptionEntries().any { it.second == currentChannelUrl }
        channelSubscribeButton.text = if (subscribed) "ОТПИСАТЬСЯ" else "ПОДПИСАТЬСЯ"
    }

    private fun refreshSubscriptions() {
        subscriptionsList.removeAllViews()
        val entries = subscriptionEntries()
        if (entries.isEmpty()) {
            subscriptionsList.addView(label("Пока нет сохранённых каналов. Открой канал из результатов поиска и нажми «Подписаться».", 14f, Color.LTGRAY))
            return
        }
        entries.forEach { (name, url) ->
            subscriptionsList.addView(actionButton(name) { openChannel(url) },
                LinearLayout.LayoutParams(-1, dp(52)))
        }
    }

    private fun openChannel(url: String) {
        status.text = "Загружаем канал…"
        showPage("channel")
        channelTitle.text = "Загрузка канала…"
        channelDescription.text = ""
        channelVideos.removeAllViews()
        extractorExecutor.execute {
            try {
                val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
                val videosTab = info.tabs.firstOrNull { it.contentFilters.contains("videos") }
                    ?: info.tabs.firstOrNull()
                    ?: throw IllegalStateException("У канала нет доступной вкладки с видео")
                val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, videosTab)
                runOnUiThread {
                    currentChannelName = info.name
                    currentChannelUrl = info.url
                    channelTitle.text = info.name
                    channelDescription.text = info.description.orEmpty()
                    updateChannelSubscriptionButton()
                    channelVideos.removeAllViews()
                    tabInfo.relatedItems.forEachIndexed { index, item ->
                        val stream = item as? StreamInfoItem
                        if (stream != null) addStreamCard(channelVideos, index + 1, stream.name, stream.url,
                            stream.thumbnails.firstOrNull()?.url.orEmpty())
                    }
                    status.text = "Канал: ${info.name}"
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    channelTitle.text = "Не удалось открыть канал"
                    channelDescription.text = error.message ?: error.javaClass.simpleName
                    status.text = "Ошибка загрузки канала"
                }
            }
        }
    }

    private fun addChannelCard(parent: LinearLayout, number: Int, title: String, url: String, thumbnailUrl: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(35, 43, 55))
                cornerRadius = dp(12).toFloat()
            }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(55, 65, 80))
        }
        val channelImageSize = if (resources.configuration.screenWidthDp >= 720) dp(92) else dp(76)
        row.addView(image, LinearLayout.LayoutParams(channelImageSize, channelImageSize))
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        column.addView(label("${number}. ${title}", 14f, Color.WHITE, true))
        column.addView(label("Канал YouTube", 11f, Color.LTGRAY))
        column.addView(actionButton("ОТКРЫТЬ КАНАЛ") { openChannel(url) })
        row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { openChannel(url) }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        if (thumbnailUrl.isNotBlank()) loadThumbnail(thumbnailUrl, image)
    }

    private fun addStreamCard(parent: LinearLayout, number: Int, title: String, url: String, thumbnailUrl: String,
                              channelName: String = "", channelUrl: String = "") {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable().apply {
                setColor(Color.rgb(35, 43, 55))
                cornerRadius = dp(12).toFloat()
            }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(55, 65, 80))
        }
        val screenWidth = resources.configuration.screenWidthDp
        val thumbWidth = when {
            screenWidth >= 1000 -> dp(220)
            screenWidth >= 600 -> dp(176)
            screenWidth < 360 -> dp(104)
            else -> dp(128)
        }
        row.addView(image, LinearLayout.LayoutParams(thumbWidth, (thumbWidth * 9 / 16)))
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        textColumn.addView(label("${number}. ${title}", 14f, Color.WHITE, true))
        textColumn.addView(label("YouTube · нажми для просмотра", 11f, Color.LTGRAY))
        if (channelUrl.isNotBlank()) {
            textColumn.addView(actionButton("Канал: " + channelName) { openChannel(channelUrl) })
        }
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { playYoutubeVideo(url, title) }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        if (thumbnailUrl.isNotBlank()) loadThumbnail(thumbnailUrl, image)
    }

    private fun loadThumbnail(imageUrl: String, target: ImageView) {
        target.tag = imageUrl
        thumbnailExecutor.execute {
            try {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
                val connection = URL(imageUrl).openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                connection.disconnect()
                if (bitmap != null) runOnUiThread {
                    if (target.tag == imageUrl) target.setImageBitmap(bitmap)
                }
            } catch (_: Throwable) {
                // Thumbnail failure must not block search or playback.
            }
        }
    }

    private fun loadHomeFeed() {
        if (!ByeDpiService.isProxyRunning()) {
            homeFeed.removeAllViews()
            homeFeed.addView(label("Запусти ByeDPI, чтобы загрузить рекомендации.", 13f, Color.LTGRAY))
            return
        }
        homeFeed.removeAllViews()
        homeFeed.addView(label("Загружаем рекомендации…", 13f, Color.LTGRAY))
        extractorExecutor.execute {
            try {
                val info = KioskInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/feed/trending")
                runOnUiThread {
                    homeFeed.removeAllViews()
                    info.relatedItems.forEachIndexed { index, item ->
                        addStreamCard(homeFeed, index + 1, item.name, item.url,
                            item.thumbnails.firstOrNull()?.url.orEmpty(),
                            item.uploaderName.orEmpty(), item.uploaderUrl.orEmpty())
                    }
                    if (info.relatedItems.isEmpty()) homeFeed.addView(label("Лента пока не вернула видео.", 13f, Color.LTGRAY))
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    homeFeed.removeAllViews()
                    homeFeed.addView(label("Не удалось загрузить ленту: ${error.message ?: error.javaClass.simpleName}", 13f, Color.LTGRAY))
                }
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
                        when (item) {
                            is StreamInfoItem -> addStreamCard(searchResults, index + 1, item.name, item.url,
                                item.thumbnails.firstOrNull()?.url.orEmpty(),
                                item.uploaderName.orEmpty(), item.uploaderUrl.orEmpty())
                            is ChannelInfoItem -> addChannelCard(searchResults, index + 1, item.name, item.url,
                                item.thumbnails.firstOrNull()?.url.orEmpty())
                        }
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
                val muxedStream = info.videoStreams.firstOrNull { !it.isVideoOnly }
                val videoStream = muxedStream ?: info.videoStreams.firstOrNull { it.isVideoOnly }
                val audioStream = if (muxedStream == null) info.audioStreams.firstOrNull() else null
                val streamUrl = videoStream?.content
                if (streamUrl.isNullOrBlank() || (muxedStream == null && audioStream?.content.isNullOrBlank())) {
                    throw IllegalStateException("Не удалось получить совместимую пару видеопотока и аудио")
                }
                val audioUrl = audioStream?.content
                recordHistory(title, url)
                runOnUiThread {
                    refreshHistory(homeRecent, 4)
                    refreshHistory(libraryList)
                    val playerIntent = Intent(this, VideoPlayerActivity::class.java)
                        .putExtra(VideoPlayerActivity.EXTRA_STREAM_URL, streamUrl)
                    if (!audioUrl.isNullOrBlank()) playerIntent.putExtra(VideoPlayerActivity.EXTRA_AUDIO_URL, audioUrl)
                    startActivity(playerIntent)
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

        val compactNavigation = resources.configuration.screenWidthDp < 480
        val navScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val navItems = if (compactNavigation) listOf(
            "home" to "Домой",
            "search" to "Поиск",
            "subscriptions" to "Подписки",
            "library" to "История",
            "profile" to "Профиль"
        ) else listOf(
            "home" to "Главная",
            "search" to "Поиск",
            "subscriptions" to "Подписки",
            "library" to "Библиотека",
            "profile" to "Профиль"
        )
        navItems.forEach { (key, title) ->
            val b = actionButton(title) { showPage(key) }
            b.textSize = if (compactNavigation) 10f else 12f
            b.minWidth = if (compactNavigation) dp(76) else dp(88)
            b.minimumHeight = dp(48)
            b.setPadding(dp(4), 0, dp(4), 0)
            navButtons[key] = b
            val params = if (compactNavigation) LinearLayout.LayoutParams(dp(78), dp(50))
                else LinearLayout.LayoutParams(0, dp(50), 1f)
            nav.addView(b, params)
        }
        navScroller.addView(nav, ViewGroup.LayoutParams(
            if (compactNavigation) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.addView(navScroller, LinearLayout.LayoutParams(-1, dp(52)))

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
        addPage("channel", buildChannelPage())

        val initial = names()
        if (initial.isEmpty()) {
            val name = "Мой профиль"
            prefs.edit().putStringSet("profile_names", setOf(name))
                .putString("profile:$name", defaultArgs)
                .putString("active_profile", name).apply()
        }
        refreshPicker(prefs.getString("active_profile", "Мой профиль"))
        loadProfile(prefs.getString("active_profile", "Мой профиль")!!)
        NewPipe.init(ByeDpiExtractorDownloader())
        showPage("home")
    }

    override fun onDestroy() {
        extractorExecutor.shutdownNow()
        thumbnailExecutor.shutdownNow()
        super.onDestroy()
    }
}
