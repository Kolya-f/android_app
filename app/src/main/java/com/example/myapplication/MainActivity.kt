package com.example.myapplication

import android.location.LocationManager
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
import androidx.compose.runtime.*
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
import com.google.firebase.firestore.ktx.firestore

private var isNetworkCallbackRegistered = false

val LightBlue = Color(0xFFADD8E6)


class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var isInternetEnabled by mutableStateOf(false)
    private val db: FirebaseFirestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Firebase
        FirebaseApp.initializeApp(this)

        // Проверка инициализации Firebase Firestore
        val db: FirebaseFirestore? = try {
            Firebase.firestore
        } catch (e: Exception) {
            null
        }

        if (db != null) {
            // Успешная инициализация Firestore
            Toast.makeText(this, "Firestore инициализирован", Toast.LENGTH_SHORT).show()
        } else {
            // Ошибка инициализации Firestore
            Toast.makeText(this, "Ошибка инициализации Firestore", Toast.LENGTH_LONG).show()
        }

        // Настройка osmdroid
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }

        // Инициализация клиента для получения местоположения
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Регистрация сетевого callback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkStatus(true)
            }

            override fun onLost(network: Network) {
                updateNetworkStatus(false)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        isNetworkCallbackRegistered = true // Обновляем флаг

        // Начало отслеживания местоположения, если разрешение уже предоставлено
        if (checkLocationPermission()) {
            startLocationUpdates()
        }

        // Установка Compose UI
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = LightBlue
                ) { innerPadding ->
                    AppContent(
                        modifier = Modifier.padding(innerPadding),
                        fusedLocationClient = fusedLocationClient,
                        isInternetEnabled = isInternetEnabled
                    )
                }
            }
        }
    }

    private var isNetworkCallbackRegistered = false

    // Оновлення статусу мережі
    private fun updateNetworkStatus(isConnected: Boolean) {
        isInternetEnabled = isConnected
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().isMapViewHardwareAccelerated = true

        // Показуємо повідомлення користувачу про зміну статусу мережі
        runOnUiThread {
            Toast.makeText(this, if (isConnected) "Режим онлайн" else "Режим оффлайн", Toast.LENGTH_SHORT).show()
        }
    }

    // Перевірка дозволу на доступ до місцезнаходження
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Початок оновлень місцезнаходження
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 6000L // Інтервал між оновленнями в мілісекундах
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (checkLocationPermission()) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    // Відправка місцезнаходження в Firestore
    private fun sendLocationToFirestore() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val locationData = hashMapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "timestamp" to System.currentTimeMillis()
                    )

                    // Відправка даних у Firestore
                    db.collection("locations")
                        .document("device_id") // Унікальний ідентифікатор пристрою або користувача
                        .set(locationData)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Дані про місцезнаходження успішно відправлені")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Помилка при відправці даних: ", e)
                        }
                }
            }
        }
    }

    // Callback для оновлення місцезнаходження
    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                Log.d("Location", "Оновлене місцезнаходження: $geoPoint")
                // Можна оновити стан карти або UI, передаючи нове місцезнаходження
            }
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

@Composable
fun AppContent(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,
    isInternetEnabled: Boolean
) {
    var showMap by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val context = LocalContext.current

    // Проверка разрешения и состояния служб местоположения
    var isLocationEnabled by remember {
        mutableStateOf(
            ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && isLocationServiceEnabled(context)
        )
    }

    // Обновляем isLocationEnabled, когда изменяется состояние служб местоположения
    LaunchedEffect(Unit) {
        // Этот код будет выполняться только при изменении служб местоположения
        while (true) {
            val currentLocationEnabled = isLocationServiceEnabled(context)
            if (isLocationEnabled != currentLocationEnabled) {
                isLocationEnabled = currentLocationEnabled
            }
            kotlinx.coroutines.delay(1000L) // Проверка каждые 1 секунду
        }
    }

    // Проверяем разрешение на доступ к местоположению и, если оно есть, получаем координаты
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getUserLocation(fusedLocationClient, context) { location ->
                userLocation = location
                showMap = true
            }
        } else {
            Toast.makeText(context, "Разрешение на доступ к местоположению отклонено", Toast.LENGTH_SHORT).show()
        }
    }

    // Функция, которая будет запускаться при нажатии на кнопку
    val onButtonClick: () -> Unit = {
        if (isLocationEnabled) {
            getUserLocation(fusedLocationClient, context) { location ->
                userLocation = location
                showMap = true
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (showMap) {
        MapScreen_Backend(modifier = modifier, userLocation = userLocation)
    } else {
        ButtonInterface(
            onButtonClick = onButtonClick,
            isLocationEnabled = isLocationEnabled,
            isInternetEnabled = isInternetEnabled,
            modifier = modifier
        )
    }
}

// Функция для получения местоположения
private fun getUserLocation(
    fusedLocationClient: FusedLocationProviderClient,
    context: Context,
    onLocationReceived: (GeoPoint?) -> Unit
) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(context, "Нет разрешения на доступ к местоположению", Toast.LENGTH_SHORT).show()
        onLocationReceived(null)
        return
    }

    fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(GeoPoint(location.latitude, location.longitude))
            } else {
                Toast.makeText(context, "Ошибка: не удалось получить местоположение", Toast.LENGTH_SHORT).show()
                onLocationReceived(null)
            }
        }
        .addOnFailureListener { exception ->
            Toast.makeText(context, "Ошибка получения местоположения: ${exception.message}", Toast.LENGTH_SHORT).show()
            onLocationReceived(null)
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


@Composable
fun MapScreen_Backend(modifier: Modifier = Modifier, userLocation: GeoPoint?) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                controller.setZoom(15.0)
                setMultiTouchControls(true)

                // Используем post, чтобы изменения применялись после полной инициализации
                post {
                    if (userLocation != null) {
                        // Проверяем и задаем центр на местоположение пользователя
                        controller.setCenter(userLocation)
                        val marker = Marker(this).apply {
                            position = userLocation
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Вы здесь"
                        }
                        overlays.add(marker)
                    } else {
                        // Координаты центра Днепра, Украина (широта и долгота)
                        val defaultLocation = GeoPoint(48.4647, 35.0462)
                        controller.setCenter(defaultLocation)

                        val marker = Marker(this).apply {
                            position = defaultLocation
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Днепр, Украина (по умолчанию)"
                        }
                        overlays.add(marker)
                    }

                    // Обновляем карту принудительно
                    invalidate()
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}


@Preview(showBackground = true)

@Composable
fun AppContentPreview() {
    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    MyApplicationTheme {
        AppContent(
            fusedLocationClient = fusedLocationClient,
            isInternetEnabled = true // Задаём значение для предварительного просмотра
        )
    }
}

