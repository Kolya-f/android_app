package com.example.myapplication


import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult

import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.ktx.firestore
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.ktx.firestore
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
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
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.android.gms.location.LocationRequest
import com.google.firebase.FirebaseApp
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import android.content.SharedPreferences
import android.location.LocationManager
import android.location.Location
import androidx.localbroadcastmanager.content.LocalBroadcastManager




val LightBlue = Color(0xFFADD8E6)

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
    private var deviceMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate вызван")

        createNotificationChannel()

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location_channel",
                "Location Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "This channel is used for location update notifications."
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }


    private fun manageLocationService(context: Context, isChecked: Boolean) {
        val serviceIntent = Intent(context, LocationUpdateService::class.java)
        if (isChecked) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.stopService(serviceIntent)
        }
    }

    fun startLocationUpdates(userName: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
            return
        }

        // Отримуємо налаштування чекбокса
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val showLocationAfterExit = sharedPreferences.getBoolean("show_location_after_exit", false)

        if (showLocationAfterExit) {
            // Якщо увімкнений чекбокс, запускаємо сервіс
            manageLocationService(this, true)
            return
        }

        // Звичайні оновлення місцезнаходження
        val locationRequest = LocationRequest.create().apply {
            interval = 1500L
            fastestInterval = 1000L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = createLocationCallback(userName)
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }


    // Функция обратного вызова для получения обновлений местоположения
    private fun createLocationCallback(userName: String): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (location.latitude.isFinite() && location.longitude.isFinite()) {
                        Log.d("LocationCallback", "Нові координати: ${location.latitude}, ${location.longitude}")

                        updateMarkerOnMap(location.latitude, location.longitude, userName)
                        sendLocationToFirestore(location.latitude, location.longitude, userName)
                    } else {
                        Log.e("LocationCallback", "Некоректні координати: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        }
    }

    // Обновление маркера на карте
    private fun updateMarkerOnMap(latitude: Double, longitude: Double, userName: String) {
        val geoPoint = GeoPoint(latitude, longitude)

        // Если маркер пользователя уже существует, обновляем его положение
        if (deviceMarker != null) {
            deviceMarker?.position = geoPoint
        } else {
            // Если маркера еще нет, создаем новый маркер и добавляем на карту
            deviceMarker = Marker(mapView).apply {
                position = geoPoint
                title = userName
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(deviceMarker)
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

    private fun loadAllLocationsFromFirestore() {
        db.collection("locations").get()
            .addOnSuccessListener { snapshots ->
                mapView.overlays.clear()

                snapshots.forEach { document ->
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")
                    val identifier = document.id

                    if (latitude != null && longitude != null) {
                        val geoPoint = GeoPoint(latitude, longitude)

                        if (identifier == userName) {
                            // Прив’язуємо існуючий маркер до нового імені
                            updateMarkerOnMap(latitude, longitude, userName)
                        } else {
                            addMarkerToMap(geoPoint, identifier)
                        }
                    }
                }

                mapView.invalidate()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Помилка завантаження даних: $e")
            }
    }



    // Функция добавления маркера на карту для конкретного пользователя
    private fun addMarkerToMap(geoPoint: GeoPoint, identifier: String) {
        Log.d("addMarkerToMap", "Добавление маркера для: $identifier с координатами: $geoPoint")

        val marker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = identifier // Устанавливаем в качестве заголовка маркера имя пользователя или устройства
        }
        mapView.overlays.add(marker)
        Log.d("Map", "Маркер добавлен на карту для: $identifier на координатах: ${geoPoint.latitude}, ${geoPoint.longitude}")
    }


    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop вызван")

        // Проверка состояния чекбокса "Показывать местоположение после выхода"
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val showLocationAfterExit = sharedPreferences.getBoolean("show_location_after_exit", false)

        // Удаляем данные только если чекбокс выключен и активность завершается не вручную
        if (!isFinishingManually && !showLocationAfterExit) {
            userName?.let {
                removeUserData(it)
            }
        } else {
            Log.d("MainActivity", "Данные пользователя не удалены: showLocationAfterExit = $showLocationAfterExit")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy вызван")

        // Проверка состояния чекбокса "Показывать местоположение после выхода"
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val showLocationAfterExit = sharedPreferences.getBoolean("show_location_after_exit", false)

        // Удаляем данные только если чекбокс выключен
        if (!showLocationAfterExit) {
            userName?.let {
                removeUserData(it)
            }
        } else {
            Log.d("MainActivity", "Данные пользователя не удалены: showLocationAfterExit = $showLocationAfterExit")
        }

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

    private fun removeUserData(userName: String) {
        if (userName.isBlank() || userName == "Гость") {
            Log.w("removeUserData", "Некоректне ім'я користувача. Видалення пропущено.")
            return
        }

        db.collection("locations").document(userName)
            .delete()
            .addOnSuccessListener {
                Log.d("removeUserData", "Дані користувача '$userName' видалено з Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("removeUserData", "Помилка видалення даних '$userName': $e")
            }
    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}

class LocationUpdateService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Ініціалізація locationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    broadcastLocation(location) // Виклик broadcastLocation для передачі координат
                }
            }
        }
    }


    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // Інтервал оновлення (10 секунд)
            fastestInterval = 5000 // Найшвидший інтервал оновлення
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Запит дозволу та старт оновлень
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun broadcastLocation(location: Location) {
        val intent = Intent("LOCATION_UPDATED").apply {
            putExtra("latitude", location.latitude)
            putExtra("longitude", location.longitude)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startLocationUpdates() // Початок оновлень місця розташування
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates() // Зупинка оновлень при завершенні сервісу
    }

    override fun onBind(intent: Intent?): IBinder? = null
}



// Проверка, включены ли службы местоположения на устройстве
fun isLocationServiceEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun getUserLocation(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context,
    isInternetEnabled: Boolean,
    onLocationReceived: (GeoPoint?) -> Unit
) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "Нет разрешения на доступ к местоположению", Toast.LENGTH_SHORT).show()
        onLocationReceived(null)
        return
    }

    val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val showLocationAfterExit = sharedPreferences.getBoolean("show_location_after_exit", false)

    if (isInternetEnabled) {
        // Получение последнего известного местоположения онлайн и его сохранение
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    if (showLocationAfterExit) {
                        saveLocationToPreferences(sharedPreferences, geoPoint) // Сохраняем местоположение
                    } else {
                        clearSavedLocation(sharedPreferences) // Удаляем сохраненное местоположение
                    }
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
        val savedLocation = getSavedLocation(sharedPreferences)
        if (savedLocation != null) {
            onLocationReceived(savedLocation)
        } else {
            Toast.makeText(context, "Нет сохраненного местоположения, используется по умолчанию", Toast.LENGTH_SHORT).show()
            onLocationReceived(GeoPoint(48.4647, 35.0462)) // Днепр по умолчанию
        }
    }
}

// Функция для очистки сохраненного местоположения
private fun clearSavedLocation(sharedPreferences: SharedPreferences) {
    sharedPreferences.edit()
        .remove("last_latitude")
        .remove("last_longitude")
        .apply()
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

@Composable
fun MapScreen_Backend(
    modifier: Modifier = Modifier,
    userLocation: GeoPoint?,

    ) {

    // Управление маркерами других устройств
    val deviceMarkers = mutableMapOf<String, Marker>()

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                controller.setZoom(15.0)
                setMultiTouchControls(true)

                post {
                    // Инициализируем маркер пользователя
                    userLocation?.let { geoPoint ->
                        controller.setCenter(geoPoint)

                    }

                    // Слушатель для загрузки и обновления маркеров других пользователей
                    val db = Firebase.firestore
                    db.collection("locations")
                        .addSnapshotListener { snapshots, error ->
                            if (error != null) {
                                Log.e("MapScreen_Backend", "Ошибка загрузки данных из Firestore: ${error.message}")
                                return@addSnapshotListener
                            }

                            // Удаляем старые маркеры устройств и добавляем новые
                            deviceMarkers.values.forEach { marker -> overlays.remove(marker) }
                            deviceMarkers.clear()

                            snapshots?.forEach { document ->
                                val latitude = document.getDouble("latitude")
                                val longitude = document.getDouble("longitude")
                                val deviceName = document.id

                                if (latitude != null && longitude != null) {
                                    val geoPoint = GeoPoint(latitude, longitude)
                                    val marker = addOrUpdateDeviceMarker(this, geoPoint, deviceName)
                                    deviceMarkers[deviceName] = marker
                                }
                            }
                            invalidate() // Обновляем карту
                        }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { mapView ->
            userLocation?.let {
                mapView.invalidate()
            }
        }
    )
}

// Функция для добавления или обновления маркера устройства
private fun addOrUpdateDeviceMarker(mapView: MapView, geoPoint: GeoPoint, deviceName: String): Marker {
    val deviceMarker = Marker(mapView).apply {
        position = geoPoint
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = "Device:addOrUpdateDeviceMarker $deviceName"
    }
    mapView.overlays.add(deviceMarker)
    Log.d("MapScreen_Backend", "Маркер для устройства $deviceName добавлен на карту.")
    return deviceMarker
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
fun ObserveLocationUpdates(
    context: Context,
    onLocationUpdated: (GeoPoint) -> Unit
) {
    DisposableEffect(context) {
        val locationReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    val latitude = it.getDoubleExtra("latitude", Double.NaN)
                    val longitude = it.getDoubleExtra("longitude", Double.NaN)
                    if (!latitude.isNaN() && !longitude.isNaN()) {
                        onLocationUpdated(GeoPoint(latitude, longitude))
                    }
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            locationReceiver,
            IntentFilter("LOCATION_UPDATED")
        )

        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(locationReceiver)
        }
    }
}



@Composable
fun AppContent_backend(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,
    isInternetEnabled: Boolean,
    onUserNameChange: (String) -> Unit
) {
    var showMap by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val context = LocalContext.current

    val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var userName by remember { mutableStateOf(preferences.getString("user_name", "") ?: "") }
    var isDialogOpen by remember { mutableStateOf(userName.isBlank()) }

    fun saveUserName(name: String) {
        preferences.edit().putString("user_name", name).apply()
        userName = name
        onUserNameChange(name)
    }

    // Додаємо стан для чекбокса
    var showLocationAfterExit by remember {
        mutableStateOf(preferences.getBoolean("show_location_after_exit", false))
    }

    // Функція управління сервісом
    fun manageLocationService(context: Context, isChecked: Boolean) {
        val serviceIntent = Intent(context, LocationUpdateService::class.java)
        if (isChecked) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.stopService(serviceIntent)
        }
    }


    fun saveShowLocationAfterExitState(
        preferences: SharedPreferences,
        context: Context,
        fusedLocationClient: FusedLocationProviderClient,
        isChecked: Boolean,
        isInternetEnabled: Boolean,
        onLocationUpdated: (GeoPoint) -> Unit
    ) {
        preferences.edit().putBoolean("show_location_after_exit", isChecked).apply()
        manageLocationService(context, isChecked)

        if (isChecked) {
            // Отримуємо поточне місцезнаходження
            getUserLocation(fusedLocationClient, context, isInternetEnabled) { location ->
                if (location != null) {
                    onLocationUpdated(location) // Передаем только non-null значение
                } else {
                    // Обработка случая, когда location равен null
                    Log.e("saveShowLocationAfterExitState", "Местоположение не определено")
                }
            }
        }
    }




    var isLocationEnabled by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && isLocationServiceEnabled(context)
        )
    }

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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getUserLocation(fusedLocationClient, context, isInternetEnabled) { location ->
                userLocation = location
                showMap = true
            }
        } else {
            Toast.makeText(context, "Доступ до місцезнаходження відхилено", Toast.LENGTH_SHORT).show()
        }
    }

    val onButtonClick: () -> Unit = {
        if (userName.isNotBlank()) {
            saveUserName(userName)
            (context as MainActivity).startLocationUpdates(userName)
            getUserLocation(fusedLocationClient, context, isInternetEnabled) { location ->
                userLocation = location
                showMap = true
            }
        } else {
            Toast.makeText(context, "Будь ласка, введіть ваше ім'я", Toast.LENGTH_SHORT).show()
        }
    }

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

    if (showMap && userLocation != null) {
        MapScreen_Backend(modifier, userLocation)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Введіть ваше ім'я") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Додаємо чекбокс
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                ObserveLocationUpdates(context) { location ->
                    userLocation = location
                    showMap = true
                }

                Checkbox(
                    checked = showLocationAfterExit,
                    onCheckedChange = { isChecked ->
                        saveShowLocationAfterExitState(
                            preferences,
                            context,
                            fusedLocationClient,
                            isChecked,
                            isInternetEnabled
                        ) { location ->
                            userLocation = location
                            showMap = true
                        }
                    }
                )
                Text("Показувати місцезнаходження після виходу")
            }

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