package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import com.example.myapplication.ui.theme.MyApplicationTheme

val LightBlue = Color(0xFFADD8E6)
val Beige = Color(0xFFF5F5DC)
val DarkBlue = Color(0xFF0057A0)
val Sand = Color(0xFFFFE4B5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = LightBlue // Фон всего экрана
                ) { innerPadding ->
                    AppContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppContent(modifier: Modifier = Modifier) {
    var showMap by remember { mutableStateOf(false) }

    if (showMap) {
        MapScreen(modifier = modifier)
    } else {
        ButtonInterface(onButtonClick = { showMap = true }, modifier = modifier)
    }
}

@Composable
fun ButtonInterface(onButtonClick: () -> Unit, modifier: Modifier = Modifier) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(Beige)
    ) {
        val (button1, button2, button3, button4) = createRefs()

        Button(
            onClick = onButtonClick,
            modifier = Modifier
                .background(Sand)
                .constrainAs(button1) {
                    bottom.linkTo(button2.top, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        ) {
            Text("Кнопка 1", color = DarkBlue)
        }

        Button(
            onClick = { /* Действие для кнопки 2 */ },
            modifier = Modifier
                .background(Sand)
                .constrainAs(button2) {
                    bottom.linkTo(button3.top, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        ) {
            Text("Кнопка 2", color = DarkBlue)
        }

        Button(
            onClick = { /* Действие для кнопки 3 */ },
            modifier = Modifier
                .background(Sand)
                .constrainAs(button3) {
                    bottom.linkTo(button4.top, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        ) {
            Text("Кнопка 3", color = DarkBlue)
        }

        Button(
            onClick = { /* Действие для кнопки 4 */ },
            modifier = Modifier
                .background(Sand)
                .constrainAs(button4) {
                    bottom.linkTo(parent.bottom, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        ) {
            Text("Кнопка 4", color = DarkBlue)
        }
    }
}

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(48.659167, 35.045556))
                setMultiTouchControls(true)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
fun AppContentPreview() {
    MyApplicationTheme {
        AppContent()
    }
}
