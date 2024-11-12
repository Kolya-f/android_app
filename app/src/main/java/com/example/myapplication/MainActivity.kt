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
import android.annotation.SuppressLint
import com.google.firebase.firestore.ktx.firestore
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.android.gms.location.LocationRequest
import com.google.firebase.FirebaseApp
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import android.content.SharedPreferences
import android.location.LocationManager


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
    private var userName: String? = null // Переменная для хранения имени пользователя
    private var isFinishingManually = false  // Флаг, указывающий на завершение активности вручную

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate вызван")

        // Получаем введенное имя пользователя
        userName = intent.getStringExtra("USER_NAME") // Передаем имя через Intent или задаем в AppContent

        // Инициализация Firebase
        FirebaseApp.initializeApp(this)

        // Инициализация клиента местоположения
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Настройка карты osmdroid
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        // Инициализация карты
        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
        }

        // Настройка сетевого подключения
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

        // Проверка разрешений и начало обновлений местоположения
        if (checkLocationPermission()) {
            startLocationUpdates(userName.orEmpty())
        } else {
            requestLocationPermission()
        }

        // Загрузка маркеров других пользователей при запуске
        loadAllLocationsFromFirestore()

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
                        isInternetEnabled = isInternetEnabled
                    )
                }
            }
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

        val locationRequest = LocationRequest.create().apply {
            interval = 6000L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = createLocationCallback(userName)
        Log.d("StartLocationUpdates", "Начинаем запрос обновлений местоположения")

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.d("StartLocationUpdates", "Запрос обновлений местоположения отправлен")
    }

    private fun createLocationCallback(userName: String): com.google.android.gms.location.LocationCallback {
        return object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                Log.d("LocationCallback", "onLocationResult вызван для $userName")
                locationResult.lastLocation?.let {
                    Log.d("LocationCallback", "Новое местоположение: ${it.latitude}, ${it.longitude}")
                    sendLocationToFirestore(it.latitude, it.longitude, userName)
                }
            }
        }
    }


    // Отправка местоположения текущего пользователя в Firestore
    private fun sendLocationToFirestore(latitude: Double, longitude: Double, userName: String) {
        Log.d("sendLocationToFirestore", "Попытка отправки данных для пользователя: $userName")

        if (userName.isBlank()) {
            Log.e("sendLocationToFirestore", "Имя пользователя пустое. Данные местоположения не отправлены.")
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


    // Загрузка всех местоположений других пользователей из Firestore
    private fun loadAllLocationsFromFirestore() {
        db.collection("locations").addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("Firestore", "Ошибка получения данных из Firestore", e)
                return@addSnapshotListener
            }

            mapView.overlays.clear() // Очищаем карту перед добавлением новых маркеров

            for (document in snapshots!!) {
                val latitude = document.getDouble("latitude")
                val longitude = document.getDouble("longitude")
                val deviceName = document.id

                if (latitude != null && longitude != null) {
                    val geoPoint = GeoPoint(latitude, longitude)
                    addMarkerToMap(geoPoint, deviceName)
                }
            }
            mapView.invalidate()
        }
    }

    // Функция для добавления маркера на карту
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

        userName?.let { name ->
            db.collection("locations").document(name)
                .delete()
                .addOnSuccessListener {
                    Log.d("MainActivity", "Данные пользователя '$name' успешно удалены при выходе")
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Ошибка при удалении данных пользователя '$name': $e")
                }
        }

        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
                isNetworkCallbackRegistered = false
                Log.d("MainActivity", "NetworkCallback успешно отменен в onDestroy")
            } catch (e: IllegalArgumentException) {
                Log.e("MainActivity", "Ошибка при отмене NetworkCallback: NetworkCallback не был зарегистрирован", e)
            }
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("MainActivity", "Обновления местоположения успешно остановлены")
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart вызван")
        isFinishingManually = false  // Сбрасываем флаг при запуске
    }

    override fun onStop() {
        super.onStop()
        Log.d("MainActivity", "onStop вызван")

        // Проверка: если onDestroy не будет вызван, удаляем данные пользователя
        if (!isFinishingManually) {
            removeUserData()
        }
    }

    // Функция удаления данных пользователя
    private fun removeUserData() {
        userName?.let { name ->
            db.collection("locations").document(name)
                .delete()
                .addOnSuccessListener {
                    Log.d("MainActivity", "Данные пользователя '$name' успешно удалены из Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Ошибка при удалении данных пользователя '$name': $e")
                }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}


@Composable
fun AppContent_backend(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,
    isInternetEnabled: Boolean
) {
    var showMap by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var userName by remember { mutableStateOf("") } // Поле для хранения имени пользователя
    val context = LocalContext.current

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

        // Регистрация BroadcastReceiver для прослушивания изменений состояния службы местоположения
        context.applicationContext.registerReceiver(
            locationStatusReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        )

        // Отмена регистрации при выходе из эффекта
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
                showMap = true  // Переход к отображению карты
            }
        } else {
            Toast.makeText(context, "Доступ к местоположению отклонен", Toast.LENGTH_SHORT).show()
        }
    }

    // Обработка нажатия кнопки с передачей userName в startLocationUpdates
    val onButtonClick: () -> Unit = {
        if (userName.isNotBlank()) { // Проверяем, введено ли имя пользователя
            // Вызываем startLocationUpdates с userName для обновлений местоположения
            (context as MainActivity).startLocationUpdates(userName)
            getUserLocation(fusedLocationClient, context, isInternetEnabled) { location ->
                userLocation = location
                showMap = true  // Переход к карте после получения местоположения
            }
        } else {
            Toast.makeText(context, "Пожалуйста, введите ваше имя", Toast.LENGTH_SHORT).show()
        }
    }

    // Условие для отображения карты или начального экрана
    if (showMap && userLocation != null) {
        MapScreen_Backend(modifier, userLocation, userName) // Передаем userName для отображения на карте
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
fun ButtonInterface(
    onButtonClick: () -> Unit,
    isLocationEnabled: Boolean,
    isInternetEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        val (locationCard, internetCard, button) = createRefs()

        // Карточка для индикатора местоположения
        StatusCard(
            title = "Местоположение",
            isEnabled = isLocationEnabled,
            modifier = Modifier
                .constrainAs(locationCard) {
                    top.linkTo(parent.top, margin = 32.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                }
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
        )

        // Кнопка "Начать"
        Button(
            onClick = onButtonClick,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF6A5ACD), Color(0xFF483D8B))
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .constrainAs(button) {
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp)
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
fun AppContentPreview()
{
    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    MyApplicationTheme {
        AppContent_backend(
            fusedLocationClient = fusedLocationClient,
            isInternetEnabled = true // Задаём значение для предварительного просмотра
        )
    }
}