package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.compose.material3.Button as Button

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    ConstraintLayout(modifier = modifier.fillMaxSize()) {
        // Создаем ссылки для кнопок
        val (button1, button2, button3, button4) = createRefs()

        Button(
            onClick = { /* Действие для кнопки 1 */ },
            modifier = Modifier.constrainAs(button1) {
                top.linkTo(parent.top, margin = 8.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        ) {
            Text("Кнопка 1")
        }

        Button(
            onClick = { /* Действие для кнопки 2 */ },
            modifier = Modifier.constrainAs(button2) {
                top.linkTo(button1.bottom, margin = 8.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        ) {
            Text("Кнопка 2")
        }

        Button(
            onClick = { /* Действие для кнопки 3 */ },
            modifier = Modifier.constrainAs(button3) {
                top.linkTo(button2.bottom, margin = 8.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        ) {
            Text("Кнопка 3")
        }

        Button(
            onClick = { /* Действие для кнопки 4 */ },
            modifier = Modifier.constrainAs(button4) {
                top.linkTo(button3.bottom, margin = 8.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom, margin = 8.dp)
            }
        ) {
            Text("Кнопка 4")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyApplicationTheme {
        MainScreen()
    }
}
