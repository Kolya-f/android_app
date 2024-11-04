package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.views.overlay.Marker



val LightBlue = Color(0xFFADD8E6)
val Beige = Color(0xFFF5F5DC)
val DarkBlue = Color(0xFF0057A0)
val Sand = Color(0xFFFFE4B5)

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = LightBlue // Фон всего экрана
                ) { innerPadding ->
                    AppContent(
                        modifier = Modifier.padding(innerPadding),
                        fusedLocationClient = fusedLocationClient // Передаем fusedLocationClient
                    )
                }
            }
        }
    }
}
@Composable
fun AppContent(modifier: Modifier = Modifier, fusedLocationClient: FusedLocationProviderClient) {
    var showMap by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val context = LocalContext.current

    // Запрос разрешения на геолокацию
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    userLocation = GeoPoint(location.latitude, location.longitude)
                }
                showMap = true
            }
        }
    }

    if (showMap) {
        MapScreen(modifier = modifier, userLocation = userLocation)
    } else {
        ButtonInterface(onButtonClick = {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        userLocation = GeoPoint(location.latitude, location.longitude)
                    }
                    showMap = true
                }
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }, modifier = modifier)
    }
}


@Composable
fun ButtonInterface(onButtonClick: () -> Unit, modifier: Modifier = Modifier) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)) // Светлый фон для контраста
    ) {
        val (button1, button2, button3) = createRefs()

        // Стиль для кнопок
        val buttonModifier = Modifier
            .padding(horizontal = 8.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(12.dp))

        // Кнопка "Начать" с градиентным фоном, слева
        Button(
            onClick = onButtonClick,
            modifier = buttonModifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF6A5ACD), Color(0xFF483D8B))
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .constrainAs(button1) {
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Начать", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // Кнопка "Опция 2" по центру
        Button(
            onClick = { /* Действие для кнопки 2 */ },
            modifier = buttonModifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF00BFFF), Color(0xFF1E90FF))
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .constrainAs(button2) {
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    start.linkTo(button1.end, margin = 16.dp)
                    end.linkTo(button3.start, margin = 16.dp)
                },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Опция 2", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // Кнопка "Опция 3" справа
        Button(
            onClick = { /* Действие для кнопки 3 */ },
            modifier = buttonModifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF32CD32), Color(0xFF228B22))
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .constrainAs(button3) {
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Опция 3", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
fun MapScreen(modifier: Modifier = Modifier, userLocation: GeoPoint?) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                controller.setZoom(15.0)
                setMultiTouchControls(true)
                userLocation?.let {
                    controller.setCenter(it)
                    // Добавляем маркер для обозначения текущего местоположения
                    val marker = Marker(this)
                    marker.position = it
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Вы здесь"
                    overlays.add(marker)
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
        AppContent(fusedLocationClient = fusedLocationClient)
    }
}