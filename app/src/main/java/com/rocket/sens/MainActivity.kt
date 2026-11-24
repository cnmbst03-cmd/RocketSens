package com.rocket.sens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var hasPermission by mutableStateOf(false)
    private var statusText by mutableStateOf("Đang kiểm tra Shizuku...")

    private val validKeys = setOf(
        "ROCKET_3746317213", "ROCKET_9697354961", "ROCKET_2181241943", "ROCKET_1958682846", "ROCKET_4163119785",
        "ROCKET_9963334018", "ROCKET_2812140441", "ROCKET_1127978094", "ROCKET_1939042955", "ROCKET_9703905715",
        "ROCKET_7635473142", "ROCKET_6241752544", "ROCKET_7825844140", "ROCKET_4460967357", "ROCKET_8293453178",
        "ROCKET_6756332150", "ROCKET_1667779376", "ROCKET_2445662585", "ROCKET_5693307665", "ROCKET_5710360983",
        "ROCKET_8934927891", "ROCKET_7887950851", "ROCKET_4466589567", "ROCKET_8429141456", "ROCKET_3303082117",
        "ROCKET_2625792787", "ROCKET_7665963761", "ROCKET_8995970241", "ROCKET_3479708607", "ROCKET_4026113008",
        "ROCKET_9786748825", "ROCKET_5728765136", "ROCKET_6488854841", "ROCKET_2566942273", "ROCKET_6884882440",
        "ROCKET_3783290795", "ROCKET_4131575764", "ROCKET_5996775663", "ROCKET_6924716023", "ROCKET_3392080953",
        "ROCKET_8235363119", "ROCKET_4332894265", "ROCKET_6649827836", "ROCKET_2149938334", "ROCKET_2351531223",
        "ROCKET_8110054933", "ROCKET_2970753705", "ROCKET_2137651678", "ROCKET_7805745017", "ROCKET_7010379415"
    )

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        hasPermission = grantResult == PackageManager.PERMISSION_GRANTED
        statusText = if (hasPermission) "Đã kết nối Shizuku" else "Bị từ chối quyền Shizuku"
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizuku()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        checkShizuku()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFB388FF))) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.app_bg),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    AppRoot(
                        validKeys = validKeys,
                        hasPermission = hasPermission,
                        statusText = statusText,
                        onRequestPermission = { requestShizukuPermission() },
                        onRunCommand = { cmd -> runShizukuCommand(cmd) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
    }

    private fun checkShizuku() {
        if (!Shizuku.pingBinder()) {
            statusText = "Shizuku chưa bật! Hãy mở app Shizuku khởi động trước."
            hasPermission = false
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            statusText = "Đã sẵn sàng (Shizuku đang hoạt động)"
        } else {
            hasPermission = false
            statusText = "Chưa cấp quyền Shizuku cho Rocket Sens"
        }
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            statusText = "Lỗi: Shizuku chưa chạy trên máy!"
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(100)
        }
    }

    private fun runShizukuCommand(cmd: String): String {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText().trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Lỗi: ${e.message}"
        }
    }
}

@Composable
fun AppRoot(
    validKeys: Set<String>,
    hasPermission: Boolean,
    statusText: String,
    onRequestPermission: () -> Unit,
    onRunCommand: (String) -> String
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rocket_prefs", Context.MODE_PRIVATE) }
    var isUnlocked by remember { mutableStateOf(prefs.getBoolean("is_unlocked", false)) }

    if (!isUnlocked) {
        KeyActivationScreen(
            validKeys = validKeys,
            onUnlocked = {
                prefs.edit().putBoolean("is_unlocked", true).apply()
                isUnlocked = true
            }
        )
    } else {
        ControlPanelScreen(
            hasPermission = hasPermission,
            statusText = statusText,
            onRequestPermission = onRequestPermission,
            onRunCommand = onRunCommand
        )
    }
}

@Composable
fun KeyActivationScreen(
    validKeys: Set<String>,
    onUnlocked: () -> Unit
) {
    var inputKey by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xCC1A102B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "ROCKET SENS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD1C4E9)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Nhập Key kích hoạt để mở khóa hệ thống", color = Color.LightGray)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = {
                        inputKey = it
                        errorMsg = ""
                    },
                    label = { Text("Nhập Key (VD: ROCKET_...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg, color = Color(0xFFFF8A80))
                }

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (validKeys.contains(inputKey.trim())) {
                            onUnlocked()
                        } else {
                            errorMsg = "Key không hợp lệ hoặc sai định dạng!"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                ) {
                    Text("Kích Hoạt Ngay", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ControlPanelScreen(
    hasPermission: Boolean,
    statusText: String,
    onRequestPermission: () -> Unit,
    onRunCommand: (String) -> String
) {
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var currentSpeed by remember { mutableStateOf("0") }
    var fastAnim by remember { mutableStateOf(false) }
    var fastTouch by remember { mutableStateOf(false) }
    var highPerf by remember { mutableStateOf(false) }
    var lastLog by remember { mutableStateOf("Sẵn sàng nhận lệnh") }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            val spd = onRunCommand("settings get system pointer_speed")
            if (spd.toFloatOrNull() != null) {
                currentSpeed = spd
                sliderValue = spd.toFloat().coerceIn(-7f, 7f)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
      
