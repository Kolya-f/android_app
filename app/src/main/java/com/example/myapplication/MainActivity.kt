package com.example.myapplication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.rotate
import android.view.MotionEvent
import kotlin.math.atan2
import android.view.ViewConfiguration
import kotlin.math.abs
import androidx.compose.animation.core.animateFloatAsState
//import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField

import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.random.Random

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState

import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

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
    private var keepTrackingEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate вызван")


        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        keepTrackingEnabled = prefs.getBoolean("keep_tracking", false)

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
            interval = 1500L // 5 секунд
            fastestInterval = 1000L // 3 секунды, чтобы не было слишком частых обновлений
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
        Log.d("Firestore", "Загрузка всех актуальных маркеров из Firestore")

        db.collection("locations").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("Firestore", "Ошибка получения данных из Firestore", e)
                return@addSnapshotListener
            }

            mapView.overlays.clear()  // Очищаем карту перед добавлением новых маркеров

            // Пересоздаем маркер текущего пользователя, если его местоположение известно
            deviceMarker?.let { mapView.overlays.add(it) }

            snapshots?.forEach { document ->
                val latitude = document.getDouble("latitude")
                val longitude = document.getDouble("longitude")
                val identifier = document.id // Используем ID документа как идентификатор пользователя или устройства

                if (latitude != null && longitude != null) {
                    val geoPoint = GeoPoint(latitude, longitude)
                    Log.d("Firestore", "Найдено местоположение для $identifier: $latitude, $longitude")
                    addMarkerToMap(geoPoint, identifier) // Передаем идентификатор и координаты
                }
            }

            mapView.invalidate()  // Обновляем карту для отображения новых маркеров
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
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("keep_tracking", false) && !isFinishing) {
            LocationTrackingService.startService(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Видаляємо дані при повному закритті
        removeUserData(userName)
        // Зупиняємо сервіс
        stopService(Intent(this, LocationTrackingService::class.java))
    }

    private fun removeUserData(userName: String) {
        if (userName.isNotBlank() && userName != "Гость") {
            Firebase.firestore.collection("locations").document(userName)
                .delete()
                .addOnSuccessListener {
                    Log.d("MainActivity", "Дані видалено")
                }
        }
    }
    override fun onBackPressed() {
        isFinishingManually = true
        super.onBackPressed()
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

// Функция для добавления или обновления маркера устройства
private fun addOrUpdateDeviceMarker(mapView: MapView, geoPoint: GeoPoint, deviceName: String): Marker {
    val deviceMarker = Marker(mapView).apply {
        position = geoPoint
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = deviceName
    }
    mapView.overlays.add(deviceMarker)
    Log.d("MapScreen_Backend", "Маркер для устройства $deviceName добавлен на карту.")
    return deviceMarker
}

@Composable
fun MapScreen_Backend(
    modifier: Modifier = Modifier,
    userLocation: GeoPoint?
) {
    val deviceMarkers = remember { mutableMapOf<String, Marker>() }
    var currentRotation by remember { mutableStateOf(0f) }
    var initialAngle by remember { mutableStateOf(0f) }
    var isRotating by remember { mutableStateOf(false) }
    var touchSlop by remember { mutableStateOf(0f) }

    AndroidView(
        factory = { context ->
            MapView(context).apply {
                controller.setZoom(15.0)
                setMultiTouchControls(true)
                isHorizontalMapRepetitionEnabled = true
                isVerticalMapRepetitionEnabled = true

                // Отримуємо системний поріг для визначення початку жесту
                touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            if (event.pointerCount == 2) {
                                initialAngle = getRotationAngle(event)
                                isRotating = false // Очікуємо достатнього зсуву
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (event.pointerCount == 2) {
                                val newAngle = getRotationAngle(event)
                                val delta = newAngle - initialAngle

                                // Перевіряємо, чи рух досить великий для початку обертання
                                if (!isRotating && abs(delta) > 3f) { // 3 градуси - поріг активації
                                    isRotating = true
                                }

                                if (isRotating) {
                                    // Точні параметри як у Google Maps
                                    currentRotation = (currentRotation + delta * 0.6f) % 360f
                                    initialAngle = newAngle
                                    mapOrientation = currentRotation
                                    invalidate()
                                }
                            }
                        }
                        MotionEvent.ACTION_POINTER_UP,
                        MotionEvent.ACTION_UP -> {
                            isRotating = false
                        }
                    }
                    false
                }

                post {
                    userLocation?.let { geoPoint ->
                        controller.setCenter(geoPoint)
                    }

                    Firebase.firestore.collection("locations")
                        .addSnapshotListener { snapshots, error ->
                            if (error != null) return@addSnapshotListener

                            overlays.removeAll { it is Marker }
                            deviceMarkers.clear()

                            snapshots?.forEach { document ->
                                document.getDouble("latitude")?.let { lat ->
                                    document.getDouble("longitude")?.let { lon ->
                                        val marker = Marker(this).apply {
                                            position = GeoPoint(lat, lon)
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = document.id
                                        }
                                        overlays.add(marker)
                                        deviceMarkers[document.id] = marker
                                    }
                                }
                            }
                            invalidate()
                        }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { mapView ->
            mapView.mapOrientation = currentRotation
            userLocation?.let {
                mapView.invalidate()
            }
        }
    )
}

private fun getRotationAngle(event: MotionEvent): Float {
    val x1 = event.getX(0)
    val y1 = event.getY(0)
    val x2 = event.getX(1)
    val y2 = event.getY(1)
    return Math.toDegrees(atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())).toFloat()
}


@Composable
private fun RotationControls(
    currentRotation: Float,
    onRotationChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showReset by remember { mutableStateOf(currentRotation != 0f) }

    Column(
        modifier = modifier.width(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Кнопка повороту за годинниковою стрілкою
        IconButton(
            onClick = {
                onRotationChange((currentRotation + 30f) % 360f) // Поворот на 30°
                showReset = true
            },
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {

        }

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопка скидання (з'являється тільки при обертанні)
        AnimatedVisibility(visible = showReset) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        onRotationChange(0f)
                        showReset = false
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Кнопка повороту проти годинникової стрілки
        IconButton(
            onClick = {
                onRotationChange((currentRotation - 30f + 360f) % 360f) // Поворот на 30°
                showReset = true
            },
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {

        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NicknameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nickname by remember { mutableStateOf(currentName) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isError = remember { mutableStateOf(false) }

    // Анімація тіні (пульсація)
    val infiniteTransition = rememberInfiniteTransition()
    val shadowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Динамічний градієнт (змінюється при введенні тексту)
    val dynamicGradient = remember(nickname.length) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF6A11CB).copy(alpha = 0.8f),
                Color(0xFF2575FC).copy(alpha = 0.8f),
                if (nickname.isNotEmpty()) Color(0xFF00C9FF) else Color(0xFFF27121)
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    }

    // Випадковий акцентний колір (опціонально)
    val randomAccentColor = remember {
        Color(
            red = Random.nextFloat(),
            green = Random.nextFloat(),
            blue = Random.nextFloat()
        ).copy(alpha = 0.7f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color(0xFF2575FC).copy(alpha = shadowAlpha)
                )
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures { focusManager.clearFocus() }
                },
            color = Color(0xFF121212).copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E1E1E),
                                Color(0xFF2A2A2A)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Text(
                    text = "Введіть ваше ім'я",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .drawWithCache {
                            val gradient = Brush.horizontalGradient(
                                colors = listOf(
                                    if (isError.value) Color(0xFFFF6B6B) else Color.Transparent,
                                    if (nickname.isNotEmpty()) Color(0xFF00C9FF) else Color(0xFFF27121)
                                )
                            )
                            onDrawBehind {
                                drawRect(gradient)
                            }
                        }
                        .border(
                            width = 1.dp,
                            brush = dynamicGradient,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = nickname,
                        onValueChange = {
                            nickname = it
                            isError.value = it.isEmpty()
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (nickname.isNotEmpty()) {
                                    onConfirm(nickname)
                                } else {
                                    isError.value = true
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .focusRequester(focusRequester)
                    )
                }

                if (isError.value) {
                    Text(
                        text = "Ім'я не може бути пустим",
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Кнопка "Скасувати" (з ефектом хвилі)
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color(0xFFFF5555)
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF5555).copy(alpha = 0.3f),
                                        Color(0xFFFF5555).copy(alpha = 0.7f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text("Скасувати")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Кнопка "Підтвердити" (динамічна підсвітка)
                    Button(
                        onClick = {
                            if (nickname.isNotEmpty()) {
                                focusManager.clearFocus()
                                onConfirm(nickname)
                            } else {
                                isError.value = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = if (nickname.isNotEmpty()) Color(0xFF00E676) else randomAccentColor
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .border(
                                width = 1.dp,
                                brush = if (nickname.isNotEmpty()) {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF00E676).copy(alpha = 0.3f),
                                            Color(0xFF00C853).copy(alpha = 0.7f)
                                        )
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            randomAccentColor.copy(alpha = 0.3f),
                                            randomAccentColor.copy(alpha = 0.7f)
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Text("Підтвердити")
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
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

    // Анімаційні ефекти
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Динамічні кольори
    val dynamicColors = remember {
        listOf(
            Color(0xFF6A11CB),
            Color(0xFF2575FC),
            Color(0xFF00C9FF)
        )
    }

    val preferences = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    var userName by remember { mutableStateOf(preferences.getString("user_name", "") ?: "") }
    var isDialogOpen by remember { mutableStateOf(userName.isBlank()) }
    var keepTracking by remember { mutableStateOf(preferences.getBoolean("keep_tracking", false)) }

    var isLocationEnabled by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && isLocationServiceEnabled(context)
        )
    }

    // Фоновий градієнт
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF121212),
            Color(0xFF1E1E1E),
            Color(0xFF2A2A2A)
        )
    )

    DisposableEffect(Unit) {
        val locationStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                isLocationEnabled = ActivityCompat.checkSelfPermission(
                    ctx ?: return, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && isLocationServiceEnabled(ctx)
            }
        }
        context.registerReceiver(locationStatusReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        onDispose {
            context.unregisterReceiver(locationStatusReceiver)
        }
    }

    fun saveUserName(name: String) {
        preferences.edit().putString("user_name", name).apply()
        userName = name
        onUserNameChange(name)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        if (showMap && userLocation != null) {
            MapScreen_Backend(Modifier.fillMaxSize(), userLocation)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Анімований заголовок
                Text(
                    text = "Локаційний трекер",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Поле введення з ефектом
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = dynamicColors[1].copy(alpha = pulseAlpha)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(dynamicColors),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .background(Color(0x20FFFFFF))
                ) {
                    TextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = {
                            Text(
                                "Ваше ім'я",
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        },
                        textStyle = TextStyle(color = Color.White),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Перемикач з анімацією
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x15FFFFFF))
                        .padding(12.dp)
                ) {
                    Switch(
                        checked = keepTracking,
                        onCheckedChange = { keepTracking = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = dynamicColors[1],
                            checkedTrackColor = dynamicColors[1].copy(alpha = 0.5f),
                            uncheckedThumbColor = Color(0xFFB0B0B0),
                            uncheckedTrackColor = Color(0xFF505050)
                        ),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Продовжувати оновлювати моє місцезнаходження",
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Кнопка з ефектом хвилі
                Button(
                    onClick = onButtonClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = dynamicColors[1].copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(dynamicColors),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "ПОЧАТИ ВІДСТЕЖУВАННЯ",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }

                // Індикатори стану
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatusIndicator(
                        isActive = isLocationEnabled,
                        text = "Місцезнаходження",
                        activeColor = dynamicColors[1]
                    )
                    StatusIndicator(
                        isActive = isInternetEnabled,
                        text = "Інтернет",
                        activeColor = dynamicColors[2]
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    isActive: Boolean,
    text: String,
    activeColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else Color(0xFFFF6B6B))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
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