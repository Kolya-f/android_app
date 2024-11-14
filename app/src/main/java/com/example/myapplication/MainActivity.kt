package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import android.Manifest
import android.util.Log
import android.os.Looper
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.example.myapplication.ui.theme.MyApplicationTheme
import android.net.ConnectivityManager
import java.io.File
import android.content.Context
import android.net.Network
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import com.google.firebase.firestore.ktx.firestore
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.android.gms.location.LocationRequest
import com.google.firebase.FirebaseApp
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import android.content.SharedPreferences
import android.location.LocationManager


val LightBlue = Color(0xFFADD8E6)

// Глобальная переменная для маркера текущего пользователя
private var userMarker: Marker? = null

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private lateinit var mapView: MapView
    private val db: FirebaseFirestore = Firebase.firestore
    private var isInternetEnabled by mutableStateOf(false)
    private var isNetworkCallbackRegistered = false
    private lateinit var locationCallback: com.google.android.gms.location.LocationCallback
    private var userName: String = "Гость" // Начальное значение — "Гость"
    private var isFinishingManually = false  // Флаг для ручного завершения

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate вызван")

        // Инициализация connectivityManager перед использованием
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Инициализация networkCallback перед регистрацией
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkStatus(true)
                Log.d("NetworkCallback", "Сеть доступна")
            }

            override fun onLost(network: Network) {
                updateNetworkStatus(false)
                Log.d("NetworkCallback", "Сеть потеряна")
            }
        }

        // Регистрируем сетевой callback
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        isNetworkCallbackRegistered = true

        // Получаем новое имя пользователя из Intent или сохраняем "Гость"
        intent.getStringExtra("USER_NAME")?.let { inputName ->
            if (inputName.isNotBlank() && inputName != userName) {
                userName = inputName.trim() // Устанавливаем новое имя пользователя
                Log.d("MainActivity", "userName обновлен на: $userName")
            } else {
                Log.d("MainActivity", "userName остается: $userName")
            }
        }

        FirebaseApp.initializeApp(this) // Инициализация Firebase
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this) // Инициализация местоположения
        setupOSMDroid() // Настройка карты osmdroid

        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
        }

        // Проверка разрешений и начало обновлений местоположения
        if (checkLocationPermission()) {
            startLocationUpdates(userName)
        } else {
            requestLocationPermission()
        }

        loadAllLocationsFromFirestore() // Загрузка маркеров других пользователей

        // Установка интерфейса Compose
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = LightBlue
                ) { innerPadding ->
                    AppContent_backend(
                        modifier = Modifier.padding(innerPadding),
                        fusedLocationClient = fusedLocationClient,
                        isInternetEnabled = isInternetEnabled,
                        onUserNameChange = { newUserName ->
                            if (newUserName.isNotBlank() && newUserName != userName) {
                                userName = newUserName.trim()
                                Log.d("MainActivity", "userName обновлен на: $userName")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun setupOSMDroid() {
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }

    private fun setupNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkStatus(true)
            }

            override fun onLost(network: Network) {
                updateNetworkStatus(false)
                Log.d("NetworkCallback", "Сеть потеряна, удаляем данные пользователя $userName")
                removeUserData(userName)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        isNetworkCallbackRegistered = true
    }

    private fun updateNetworkStatus(isConnected: Boolean) {
        isInternetEnabled = isConnected
        runOnUiThread {
            Toast.makeText(this, if (isConnected) "Режим онлайн" else "Режим оффлайн", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
    }

    // Начало обновлений местоположения с userName
    fun startLocationUpdates(userName: String) {
        Log.d("StartLocationUpdates", "Вошли в startLocationUpdates с userName = $userName")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
            return
        }

        // Запрос местоположения с интервалом 1000 мс (1 секунда)
        val locationRequest = LocationRequest.create().apply {
            interval = 5000L // 5 секунд
            fastestInterval = 3000L // 3 секунды, чтобы не было слишком частых обновлений
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }


        locationCallback = createLocationCallback(userName)
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.d("StartLocationUpdates", "Запрос обновлений местоположения с интервалом 1 секунда отправлен")
    }

    // Функция обратного вызова для получения обновлений местоположения
    private fun createLocationCallback(userName: String): com.google.android.gms.location.LocationCallback {
        return object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationCallback", "Новое местоположение: ${location.latitude}, ${location.longitude}")

                    // Обновляем или создаем маркер пользователя на карте
                    updateMarkerOnMap(location.latitude, location.longitude, userName)

                    // Отправляем данные местоположения в Firestore
                    sendLocationToFirestore(location.latitude, location.longitude, userName)
                }
            }
        }
    }

    // Обновление маркера на карте
    private fun updateMarkerOnMap(latitude: Double, longitude: Double, userName: String) {
        val geoPoint = GeoPoint(latitude, longitude)

        // Если маркер пользователя уже существует, обновляем его положение
        if (userMarker != null) {
            userMarker?.position = geoPoint
        } else {
            // Если маркера еще нет, создаем новый маркер и добавляем на карту
            userMarker = Marker(mapView).apply {
                position = geoPoint
                title = userName
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(userMarker)
        }

        // Обновляем карту для отображения изменений
        mapView.invalidate()
    }

    // Функция отправки данных местоположения в Firestore
    private fun sendLocationToFirestore(latitude: Double, longitude: Double, userName: String) {
        if (userName.isBlank() || userName == "Гость") {
            Log.e("sendLocationToFirestore", "Некорректное имя пользователя. Данные местоположения не отправлены.")
            return
        }

        val locationData = hashMapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("locations").document(userName).set(locationData)
            .addOnSuccessListener {
                Log.d("sendLocationToFirestore", "Данные местоположения успешно отправлены для пользователя $userName")
            }
            .addOnFailureListener { e ->
                Log.e("sendLocationToFirestore", "Ошибка отправки данных местоположения", e)
            }
    }

    // Загрузка всех местоположений из Firestore и добавление маркеров на карту
    private fun loadAllLocationsFromFirestore() {
        db.collection("locations").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("Firestore", "Ошибка получения данных из Firestore", e)
                return@addSnapshotListener
            }

            mapView.overlays.clear()  // Очищаем карту

            // Добавляем маркер пользователя снова, если он уже создан
            userMarker?.let { mapView.overlays.add(it) }

            // Добавляем маркеры других пользователей
            snapshots?.forEach { document ->
                val latitude = document.getDouble("latitude")
                val longitude = document.getDouble("longitude")
                val deviceName = document.id

                if (latitude != null && longitude != null) {
                    addMarkerToMap(GeoPoint(latitude, longitude), deviceName)
                }
            }

            mapView.invalidate()  // Обновляем карту
        }
    }

    // Добавление маркера на карту для других пользователей
    private fun addMarkerToMap(geoPoint: GeoPoint, userName: String) {
        val marker = Marker(mapView).apply {
            position = geoPoint
            title = userName
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy вызван, удаляем данные пользователя $userName")

        // Удаляем данные пользователя из базы данных, если имя пользователя указано
        userName?.let {
            removeUserData(it) // Передаем userName в removeUserData
        } ?: Log.e("onDestroy", "Ошибка: userName отсутствует при onDestroy")

        // Проверяем, был ли зарегистрирован NetworkCallback, перед его отменой
        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                isNetworkCallbackRegistered = false
                Log.d("MainActivity", "NetworkCallback успешно отменен в onDestroy")
            } catch (e: IllegalArgumentException) {
                Log.e("MainActivity", "Ошибка при отмене NetworkCallback: он не был зарегистрирован", e)
            }
        }

        // Остановка обновлений местоположения
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("MainActivity", "Обновления местоположения успешно остановлены")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop вызван")

        // Проверка и удаление данных пользователя, если активность завершается не вручную
        if (!isFinishingManually) {
            userName?.let {
                removeUserData(it) // Передаем userName в removeUserData
            } ?: Log.e("onStop", "Ошибка: userName отсутствует при onStop")
        }
    }

    private fun removeUserData(userName: String) {
        db.collection("locations").document(userName)
            .delete()
            .addOnSuccessListener {
                Log.d("removeUserData", "Данные пользователя '$userName' успешно удалены из Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("removeUserData", "Ошибка при удалении данных пользователя '$userName': $e")
            }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}


// Проверка, включены ли службы местоположения на устройстве
fun isLocationServiceEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

// Функция для получения и сохранения местоположения
private fun getUserLocation(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context,
    isInternetEnabled: Boolean,
    onLocationReceived: (GeoPoint?) -> Unit)
{
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "Нет разрешения на доступ к местоположению", Toast.LENGTH_SHORT).show()
        onLocationReceived(null)
        return
    }

    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    if (isInternetEnabled) {
        // Получение последнего известного местоположения онлайн и его сохранение
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    saveLocationToPreferences(sharedPreferences, geoPoint) // Сохраняем местоположение
                    onLocationReceived(geoPoint)
                } else {
                    Toast.makeText(context, "Местоположение не получено", Toast.LENGTH_SHORT).show()
                    onLocationReceived(getSavedLocation(sharedPreferences)) // Используем сохраненное местоположение
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Ошибка получения местоположения", Toast.LENGTH_SHORT).show()
                onLocationReceived(getSavedLocation(sharedPreferences))
            }
    } else {
        // Офлайн-режим: загружаем последнее сохраненное местоположение или используем координаты по умолчанию
        onLocationReceived(getSavedLocation(sharedPreferences) ?: GeoPoint(48.4647, 35.0462)) // Днепр по умолчанию
    }
}

// Функция для сохранения местоположения в SharedPreferences
private fun saveLocationToPreferences(sharedPreferences: SharedPreferences, geoPoint: GeoPoint) {
    sharedPreferences.edit()
        .putFloat("last_latitude", geoPoint.latitude.toFloat())
        .putFloat("last_longitude", geoPoint.longitude.toFloat())
        .apply()
}

// Функция для загрузки последнего сохраненного местоположения
private fun getSavedLocation(sharedPreferences: SharedPreferences): GeoPoint? {
    val latitude = sharedPreferences.getFloat("last_latitude", Float.NaN)
    val longitude = sharedPreferences.getFloat("last_longitude", Float.NaN)
    return if (!latitude.isNaN() && !longitude.isNaN()) {
        GeoPoint(latitude.toDouble(), longitude.toDouble())
    } else {
        null
    }
}


// Экран карты с загрузкой маркеров всех устройств из Firestore
@Composable
fun MapScreen_Backend(
    modifier: Modifier = Modifier,
    userLocation: GeoPoint?,
    userName: String // Добавлен параметр для имени пользователя
) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                controller.setZoom(15.0)
                setMultiTouchControls(true)

                post {
                    // Центрируем карту на текущем местоположении пользователя
                    userLocation?.let { geoPoint ->
                        controller.setCenter(geoPoint)
                        val userMarker = Marker(this).apply {
                            position = geoPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = userName  // Отображение имени текущего пользователя
                        }
                        overlays.add(userMarker)
                    }

                    // Подгружаем все местоположения из Firestore и добавляем маркеры для других устройств
                    val db = Firebase.firestore
                    db.collection("locations")
                        .get()
                        .addOnSuccessListener { documents ->
                            for (document in documents) {
                                val latitude = document.getDouble("latitude")
                                val longitude = document.getDouble("longitude")
                                val deviceName = document.id  // Используем ID документа как имя устройства

                                // Если местоположение успешно получено, добавляем маркер
                                if (latitude != null && longitude != null) {
                                    val deviceMarker = Marker(this).apply {
                                        position = GeoPoint(latitude, longitude)
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        title = "Device: $deviceName"  // Устанавливаем ID устройства как заголовок
                                    }
                                    overlays.add(deviceMarker)
                                }
                            }
                            invalidate()  // Обновляем карту для отображения новых маркеров
                        }
                        .addOnFailureListener { e ->
                            Log.e("MapScreen_Backend", "Ошибка загрузки данных из Firestore: ${e.message}")
                        }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun NicknameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nickname by remember { mutableStateOf(currentName) } // Используем текущее имя как начальное значение

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = "Введите ваше имя") },
        text = {
            TextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Имя") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(nickname) }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = { onDismiss() }) {
                Text("Отмена")
            }
        }
    )
}


@Composable
fun AppContent_backend(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,
    isInternetEnabled: Boolean,
    onUserNameChange: (String) -> Unit // Добавляем параметр для передачи имени пользователя
) {
    var showMap by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val context = LocalContext.current

    // Получаем SharedPreferences для хранения имени пользователя
    val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var userName by remember { mutableStateOf(preferences.getString("user_name", "") ?: "") }
    var isDialogOpen by remember { mutableStateOf(userName.isBlank()) } // Открываем диалог, если имени нет

    // Функция для сохранения никнейма
    fun saveUserName(name: String) {
        preferences.edit().putString("user_name", name).apply()
        userName = name
        onUserNameChange(name)
    }

    // Состояние для отслеживания доступности местоположения
    var isLocationEnabled by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && isLocationServiceEnabled(context)
        )
    }

    // Обновление состояния местоположения при его изменении
    DisposableEffect(context) {
        val locationStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                isLocationEnabled = ActivityCompat.checkSelfPermission(
                    ctx ?: return, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && isLocationServiceEnabled(ctx)
            }
        }
        context.applicationContext.registerReceiver(
            locationStatusReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        )
        onDispose {
            context.applicationContext.unregisterReceiver(locationStatusReceiver)
        }
    }

    // Лаунчер для запроса разрешений
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getUserLocation(fusedLocationClient, context, isInternetEnabled) { location ->
                userLocation = location
                showMap = true
            }
        } else {
            Toast.makeText(context, "Доступ к местоположению отклонен", Toast.LENGTH_SHORT).show()
        }
    }

    // Обработка нажатия кнопки
    val onButtonClick: () -> Unit = {
        if (userName.isNotBlank()) {
            saveUserName(userName) // Сохраняем имя
            (context as MainActivity).startLocationUpdates(userName)
            getUserLocation(fusedLocationClient, context, isInternetEnabled) { location ->
                userLocation = location
                showMap = true
            }
        } else {
            Toast.makeText(context, "Пожалуйста, введите ваше имя", Toast.LENGTH_SHORT).show()
        }
    }

    // Отображение диалога для ввода никнейма при первом запуске
    if (isDialogOpen) {
        NicknameDialog(
            currentName = userName,
            onConfirm = { name ->
                saveUserName(name)
                isDialogOpen = false
            },
            onDismiss = { isDialogOpen = false }
        )
    }

    // Условие для отображения карты или начального экрана
    if (showMap && userLocation != null) {
        MapScreen_Backend(modifier, userLocation, userName)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Поле ввода имени пользователя
            TextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Введите ваше имя") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Кнопка для продолжения
            ButtonInterface(
                onButtonClick = onButtonClick,
                isLocationEnabled = isLocationEnabled,
                isInternetEnabled = isInternetEnabled,
                modifier = modifier
            )
        }
    }
}

@Composable
fun ButtonInterface(
    onButtonClick: () -> Unit,
    isLocationEnabled: Boolean,
    isInternetEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5)) // Светлый фон для более современного стиля
    ) {
        // Создаем ссылки на компоненты
        val (locationCard, internetCard, startButton) = createRefs()

        // Карточка для индикатора местоположения
        StatusCard(
            title = "Местоположение",
            isEnabled = isLocationEnabled,
            modifier = Modifier
                .constrainAs(locationCard) {
                    top.linkTo(parent.top, margin = 24.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                }
                .padding(vertical = 8.dp)
                .background(
                    color = if (isLocationEnabled) Color(0xFFE3FCEF) else Color(0xFFFDEAEA),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Карточка для индикатора интернета
        StatusCard(
            title = "Интернет",
            isEnabled = isInternetEnabled,
            modifier = Modifier
                .constrainAs(internetCard) {
                    top.linkTo(locationCard.bottom, margin = 16.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                }
                .padding(vertical = 8.dp)
                .background(
                    color = if (isInternetEnabled) Color(0xFFE3FCEF) else Color(0xFFFDEAEA),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Современная кнопка "Начать" с градиентом и увеличенными отступами
        Button(
            onClick = onButtonClick,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF4B6CB7), Color(0xFF182848))
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .constrainAs(startButton) { // Обратите внимание на использование имени startButton
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(pressedElevation = 12.dp, defaultElevation = 6.dp)
        ) {
            Text("Начать", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))


        }
    }
}

@Composable
fun StatusCard(
    title: String,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )

            Icon(
                imageVector = if (isEnabled) Icons.Filled.CheckCircle else Icons.Filled.Close ,
                contentDescription = if (isEnabled) "Включено" else "Выключено",
                tint = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFFF5722), // зелёный или красный цвет
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true)

@Composable
fun AppContentPreview() {
    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    MyApplicationTheme {
        AppContent_backend(
            fusedLocationClient = fusedLocationClient,
            isInternetEnabled = true, // Задаём значение для предварительного просмотра
            onUserNameChange = {} // Пустая функция для предварительного просмотра
        )
    }
}
