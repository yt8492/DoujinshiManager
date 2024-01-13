package com.yt8492.doujinshimanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DoujinshiManagerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val (increment, num) = rememberHogeHoge()
                    Text(text = "num: $num")
                    Button(onClick = { increment() }) {
                        Text(text = "+")
                    }
                }
            }
        }
    }
}

data class Hoge(
    val increment: () -> Unit,
    val value: Int
)

@Composable
fun rememberHogeHoge(): Hoge {
    var state by remember {
        mutableIntStateOf(0)
    }
    return Hoge(
        increment = {
            state++
        },
        value = state,
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DoujinshiManagerTheme {
        Greeting("Android")
    }
}