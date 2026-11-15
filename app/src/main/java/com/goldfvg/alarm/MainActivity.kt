package com.goldfvg.alarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GoldScheme = darkColorScheme(
    primary = Color(0xFFD4AF37),
    onPrimary = Color(0xFF0E1117),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFEAECEF),
    surface = Color(0xFF161B26),
    onSurface = Color(0xFFEAECEF),
    secondary = Color(0xFF22C55E),
    tertiary = Color(0xFFEF4444)
)

private fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = GoldScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    val store = remember { FvgStore(context) }
    var token by remember { androidx.compose.runtime.mutableStateOf(store.apiToken) }
    var showSettings by remember { androidx.compose.runtime.mutableStateOf(store.apiToken.isBlank()) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startMonitor() {
        ContextCompat.startForegroundService(context, Intent(context, AlertService::class.java))
    }
    fun sendAction(action: String) {
        context.startService(Intent(context, AlertService::class.java).setAction(action))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            "هشدار FVG طلا — تایم ۱۵ دقیقه",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("قیمت: ${fmt(AppState.lastPrice.value)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(AppState.statusText.value, color = Color(0xFF9AA3B2), fontSize = 12.sp)
        }
        Text(
            "آخرین به‌روزرسانی: ${AppState.lastUpdate.value}",
            color = Color(0xFF6B7280), fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { startMonitor() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text(if (AppState.running.value) "پایش فعال است" else "شروع پایش") }
            OutlinedButton(onClick = { sendAction(AlertService.ACTION_STOP) }) { Text("توقف") }
            if (AppState.ringing.value) {
                Button(
                    onClick = { sendAction(AlertService.ACTION_STOP_ALARM) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) { Text("قطع صدا") }
            }
        }
        if (showSettings) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("توکن API حساب دمو OANDA") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(
                    onClick = { store.apiToken = token; showSettings = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("ذخیره توکن") }
            }
            Text(
                "توکن را از myaccount.oanda.com بخش Manage API Access بگیر (Practice).",
                color = Color(0xFF6B7280), fontSize = 11.sp
            )
        } else {
            TextButton(onClick = { showSettings = true }) {
                Text("تغییر توکن OANDA", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        PriceChart(
            candles = AppState.candles,
            fvgs = AppState.fvgs,
            modifier = Modifier.fillMaxWidth().weight(1.2f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "نواحی FVG فعال: ${AppState.fvgs.size}",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(AppState.fvgs.sortedByDescending { it.createdAt }) { f -> FvgRow(f) }
        }
    }

    if (AppState.ringing.value) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("هشدار FVG طلا") },
            text = { Text("قیمت به ناحیه FVG رسید! تا قطع نکنی، صدا ادامه دارد.") },
            confirmButton = {
                TextButton(onClick = { sendAction(AlertService.ACTION_STOP_ALARM) }) {
                    Text("قطع صدا", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun FvgRow(f: Fvg) {
    val bullish = f.type == "bullish"
    val zoneColor = if (bullish) Color(0xFF22C55E) else Color(0xFFEF4444)
    val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(f.createdAt))
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(12.dp).background(zoneColor, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (bullish) "FVG صعودی" else "FVG نزولی",
                    color = zoneColor, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Text(
                    "بازه: ${fmt(f.bottom)} — ${fmt(f.top)}",
                    color = Color(0xFF9AA3B2), fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(timeStr, color = Color(0xFF6B7280), fontSize = 11.sp)
        }
    }
}
