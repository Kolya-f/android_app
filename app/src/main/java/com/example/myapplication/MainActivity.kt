package com.example.myapplication


import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.ktx.firestore
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.google.firebase.firestore.QuerySnapshot


val LightBlue = Color(0xFFADD8E6)

// Глобальная переменная для хранения всех маркеров
private val userMarkers: MutableMap<String, Marker> = mutableMapOf()

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
    private var isFinishingManually = false // Флаг для ручного завершения
    private var userMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate вызван")

        // Инициализация всех компонентов
        setupOSMDroid()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkStatus(true)
            }

            override fun onLost(network: Network) {
                updateNetworkStatus(false)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        isNetworkCallbackRegistered = true

        // Запрашиваем разрешение, если необходимо, но не запускаем обновление местоположения
        if (!checkLocationPermission()) {
            requestLocationPermission()
        }

        // Загружаем маркеры из Firestore (без текущего пользователя, так как его маркер еще не создан)
        loadAllLocationsFromFirestore()

        // Устанавливаем интерфейс
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
                        },
                        onStartTracking = {
                            if (userName.isNotBlank() && checkLocationPermission()) {
                                startLocationUpdates(userName)
                            } else {
                                Log.e("MainActivity", "Имя пользователя не указано или отсутствуют разрешения")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun removeStaleMarkers() {
        val currentTime = System.currentTimeMillis()

        // Зчитуємо всі маркери з карти
        val mapMarkers = mapView.overlays.filterIsInstance<Marker>()
        Log.d("updateAndRemoveStaleMarkers", "Зчитано маркери з карти: ${mapMarkers.map { it.title }}")

        // Додаємо або оновлюємо маркери в базі даних
        mapMarkers.forEach { marker ->
            val id = marker.relatedObject?.toString() ?: return@forEach // Ідентифікатор маркера
            val markerData = hashMapOf(
                "latitude" to marker.position.latitude,
                "longitude" to marker.position.longitude,
                "timestamp" to currentTime,
                "id" to id,
                "name" to marker.title
            )

            // Додаємо або оновлюємо документ в Firestore
            db.collection("locations").document(id)
                .set(markerData)
                .addOnSuccessListener {
                    Log.d("updateAndRemoveStaleMarkers", "Оновлено маркер у базі: $id")
                }
                .addOnFailureListener { e ->
                    Log.e("updateAndRemoveStaleMarkers", "Помилка при оновленні маркера $id", e)
                }
        }

        // Отримуємо всі маркери з бази даних
        db.collection("locations").get().addOnSuccessListener { snapshots ->
            val staleMarkers = mutableListOf<String>()

            snapshots.forEach { document ->
                val id = document.getString("id") ?: return@forEach
                val timestamp = document.getLong("timestamp") ?: return@forEach

                if (currentTime - timestamp > 15_000) { // Якщо timestamp старший за 15 секунд
                    staleMarkers.add(id)
                }
            }

            // Видаляємо застарілі маркери
            staleMarkers.forEach { id ->
                val marker = userMarkers[id]
                if (marker != null) {
                    mapView.overlays.remove(marker) // Видаляємо маркер з карти
                    userMarkers.remove(id) // Видаляємо з локального списку
                    Log.d("updateAndRemoveStaleMarkers", "Видалено маркер з ID: $id")
                }

                // Видаляємо документ з бази даних
                db.collection("locations").document(id).delete()
                    .addOnSuccessListener {
                        Log.d("updateAndRemoveStaleMarkers", "Видалено документ з бази для ID: $id")
                    }
                    .addOnFailureListener { e ->
                        Log.e("updateAndRemoveStaleMarkers", "Помилка при видаленні документа для ID: $id", e)
                    }
            }

            // Логуємо активні маркери
            Log.d("updateAndRemoveStaleMarkers", "Активні маркери: ${userMarkers.keys}")

            // Оновлюємо карту
            mapView.invalidate()
        }.addOnFailureListener { e ->
            Log.e("updateAndRemoveStaleMarkers", "Помилка при отриманні маркерів з бази даних", e)
        }
    }

    private fun setupOSMDroid() {
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }

    private fun updateNetworkStatus(isConnected: Boolean) {
        isInternetEnabled = isConnected
        runOnUiThread {
            Toast.makeText(
                this,
                if (isConnected) "Режим онлайн" else "Режим оффлайн",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun startLocationUpdates(userName: String) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 1500L
            fastestInterval = 1000L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = createLocationCallback(userName)
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun createLocationCallback(userName: String): com.google.android.gms.location.LocationCallback {
        return object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d(
                        "LocationCallback",
                        "Новое местоположение: ${location.latitude}, ${location.longitude}"
                    )
                    updateMarkerOnMap(location.latitude, location.longitude, userName)
                    sendLocationToFirestore(location.latitude, location.longitude, userName)
                }
            }
        }
    }

    private fun updateMarkerOnMap(latitude: Double, longitude: Double, userName: String) {
        val geoPoint = GeoPoint(latitude, longitude)

        // Удаляем старый маркер текущего пользователя, если он существует
        userMarker?.let { mapView.overlays.remove(it) }

        // Создаем новый маркер для текущего пользователя
        userMarker = Marker(mapView).apply {
            position = geoPoint
            title = userName // Название маркера
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(userMarker) // Добавляем обновленный маркер на карту
        Log.d("updateMarkerOnMap", "Маркер текущего пользователя обновлен на координатах: $latitude, $longitude")

        mapView.invalidate() // Обновляем карту
    }

    private fun sendLocationToFirestore(latitude: Double, longitude: Double, userName: String) {
        if (userName.isBlank() || userName == "Гость") {
            Log.e(
                "sendLocationToFirestore",
                "Некорректное имя пользователя. Данные местоположения не отправлены."
            )
            return
        }

        val userId = userName.hashCode().toString() // Генерируем уникальный ID (или используйте Firebase Auth ID)
        val locationData = hashMapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "timestamp" to System.currentTimeMillis(),
            "id" to userId, // Добавляем ID пользователя
            "name" to userName // Добавляем имя пользователя
        )

        db.collection("locations").document(userId).set(locationData)
            .addOnSuccessListener {
                Log.d(
                    "sendLocationToFirestore",
                    "Данные местоположения успешно отправлены для пользователя $userName с ID $userId"
                )
            }
            .addOnFailureListener { e ->
                Log.e("sendLocationToFirestore", "Ошибка отправки данных местоположения", e)
            }
    }

    private fun loadAllLocationsFromFirestore() {
        db.collection("locations").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("Firestore", "Ошибка получения данных из Firestore: ${e.message}")
                return@addSnapshotListener
            }

            if (snapshots != null) {
                // Видаляємо застарілі маркери
                removeStaleMarkers()

                // Обробляємо нові маркери
                snapshots.forEach { document ->
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")
                    val identifier = document.getString("id") ?: "unknown"
                    val name = document.getString("name") ?: "Без имени"

                    if (identifier == userName.hashCode().toString()) {
                        Log.d("Firestore", "Пропускаем маркер текущего пользователя: $userName")
                        return@forEach
                    }

                    if (latitude != null && longitude != null) {
                        val geoPoint = GeoPoint(latitude, longitude)
                        val marker = userMarkers[identifier] ?: Marker(mapView).apply {
                            userMarkers[identifier] = this
                            mapView.overlays.add(this)
                        }

                        marker.apply {
                            position = geoPoint
                            title = "Device: $name"
                            relatedObject = identifier
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        Log.d("Firestore", "Добавлен или обновлен маркер для $identifier ($name) на координатах: $latitude, $longitude")
                    } else {
                        Log.w("Firestore", "Некорректные координаты для $identifier: $latitude, $longitude")
                    }
                }

                mapView.invalidate()
                Log.d("Firestore", "Карта обновлена после обработки всех маркеров.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy вызван")

        // Проверяем, инициализирован ли locationCallback, перед удалением
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("MainActivity", "Обновления местоположения остановлены.")
        } else {
            Log.w("MainActivity", "locationCallback не был инициализирован.")
        }

        // Проверяем маркер текущего пользователя
        userMarker?.let {
            mapView.overlays.remove(it)
            Log.d("MainActivity", "Маркер текущего пользователя удален с карты.")
        }

        if (userName.isNotBlank() && userName != "Гость") {
            removeUserData(userName)
        }

        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                isNetworkCallbackRegistered = false
                Log.d("MainActivity", "NetworkCallback успешно отменен.")
            } catch (e: IllegalArgumentException) {
                Log.e("MainActivity", "Ошибка при отмене NetworkCallback: ${e.message}")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishingManually) {
            userName.let {
                removeUserData(it)
            }
        }
    }

    private fun removeUserData(userName: String) {
        val userId = userName.hashCode().toString() // Генерируем ID
        db.collection("locations").document(userId)
            .delete()
            .addOnSuccessListener {
                Log.d(
                    "removeUserData",
                    "Данные пользователя '$userName' успешно удалены из Firestore"
                )
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
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

        // Функция для получения и сохранения местоположения
        private fun getUserLocation(
            fusedLocationClient: FusedLocationProviderClient,
            context: Context,
            isInternetEnabled: Boolean,
            onLocationReceived: (GeoPoint?) -> Unit
        ) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    context,
                    "Нет разрешения на доступ к местоположению",
                    Toast.LENGTH_SHORT
                ).show()
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
                            saveLocationToPreferences(
                                sharedPreferences,
                                geoPoint
                            ) // Сохраняем местоположение
                            onLocationReceived(geoPoint)
                        } else {
                            Toast.makeText(
                                context,
                                "Местоположение не получено",
                                Toast.LENGTH_SHORT
                            ).show()
                            onLocationReceived(getSavedLocation(sharedPreferences)) // Используем сохраненное местоположение
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            context,
                            "Ошибка получения местоположения",
                            Toast.LENGTH_SHORT
                        ).show()
                        onLocationReceived(getSavedLocation(sharedPreferences))
                    }
            } else {
                // Офлайн-режим: загружаем последнее сохраненное местоположение или используем координаты по умолчанию
                onLocationReceived(
                    getSavedLocation(sharedPreferences) ?: GeoPoint(
                        48.4647,
                        35.0462
                    )
                ) // Днепр по умолчанию
            }
        }

        // Функция для сохранения местоположения в SharedPreferences
        private fun saveLocationToPreferences(
            sharedPreferences: SharedPreferences,
            geoPoint: GeoPoint
        ) {
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
            userName: String
        ) {
            // Переменная для хранения единственного маркера пользователя
            var userMarker: Marker? by remember { mutableStateOf(null) }
            var isBlinking by remember { mutableStateOf(false) }

            // Анимация мигания
            val infiniteTransition = rememberInfiniteTransition(label = "BlinkingTransition")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BlinkAlpha"
            )

            // Включение мигания через три секунды
            LaunchedEffect(Unit) {
                delay(3000)
                isBlinking = true
            }

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
                                userMarker = addOrUpdateUserMarker(this, geoPoint, userName, alpha)
                            }

                            // Слушатель для загрузки и обновления маркеров других пользователей
                            val db = Firebase.firestore
                            db.collection("locations")
                                .addSnapshotListener { snapshots, error ->
                                    if (error != null) {
                                        Log.e(
                                            "MapScreen_Backend",
                                            "Ошибка загрузки данных из Firestore: ${error.message}"
                                        )
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
                                            val marker =
                                                addOrUpdateDeviceMarker(this, geoPoint, deviceName)
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
                    userLocation?.let { geoPoint ->
                        // Обновляем позицию маркера пользователя, если она изменилась
                        if (userMarker == null) {
                            userMarker = addOrUpdateUserMarker(mapView, geoPoint, userName, alpha)
                        } else {
                            userMarker?.position =
                                geoPoint  // Меняем координаты, не создавая новый маркер
                            mapView.invalidate()
                        }
                    }
                }
            )
        }

        // Функция для добавления или обновления маркера пользователя с эффектом мигания
        private fun addOrUpdateUserMarker(
            mapView: MapView,
            geoPoint: GeoPoint,
            userName: String,
            alpha: Float
        ): Marker {
            val userMarker = Marker(mapView).apply {
                position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = userName
                icon?.alpha = (alpha * 255).toInt()  // Применяем анимацию мигания
            }
            mapView.overlays.add(userMarker)
            return userMarker  // Возвращаем маркер для отслеживания
        }

        // Функция для добавления или обновления маркера устройства
        private fun addOrUpdateDeviceMarker(
            mapView: MapView,
            geoPoint: GeoPoint,
            deviceName: String
        ): Marker {
            val deviceMarker = Marker(mapView).apply {
                position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Device: $deviceName"
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
fun AppContent_backend(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,
    isInternetEnabled: Boolean,
    onUserNameChange: (String) -> Unit, // Для изменения имени пользователя
    onStartTracking: () -> Unit // Новый параметр для запуска отслеживания
) {
    var showMap by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val context = LocalContext.current

    // SharedPreferences для хранения имени пользователя
    val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var userName by remember {
        mutableStateOf(preferences.getString("user_name", "") ?: "")
    }
    var isDialogOpen by remember { mutableStateOf(userName.isBlank()) }

    fun saveUserName(name: String) {
        preferences.edit().putString("user_name", name).apply()
        userName = name
        onUserNameChange(name)
    }

    // Состояние доступности местоположения
    var isLocationEnabled by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && isLocationServiceEnabled(context)
        )
    }

    // Обновление состояния местоположения при изменении
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

    // Запрос разрешений
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

    // Обработка кнопки "Начать"
    val onButtonClick: () -> Unit = {
        if (userName.isNotBlank()) {
            saveUserName(userName)
            onStartTracking() // Вызываем `onStartTracking` при начале трекинга
            getUserLocation(fusedLocationClient, context, isInternetEnabled) { location ->
                userLocation = location
                showMap = true
            }
        } else {
            Toast.makeText(context, "Введите имя пользователя", Toast.LENGTH_SHORT).show()
        }
    }

    // Диалог для ввода имени пользователя
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
        MapScreen_Backend(modifier, userLocation, userName)
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
                label = { Text("Введите ваше имя") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

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
                        imageVector = if (isEnabled) Icons.Filled.CheckCircle else Icons.Filled.Close,
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
                    onUserNameChange = {}, // Пустая функция для предварительного просмотра
                    onStartTracking = {} // Пустая функция для предварительного просмотра
                )
            }
        }
