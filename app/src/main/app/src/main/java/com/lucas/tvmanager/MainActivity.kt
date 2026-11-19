package com.lucas.tvmanager

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.Executors
import javax.security.auth.x500.X500Principal

class MainActivity : AppCompatActivity() {

    private lateinit var ipInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var statusText: TextView
    private lateinit var logText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private var adbManager: SimpleAdbManager? = null
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F6F9"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 36)
        }

        val headerTitle = TextView(this).apply {
            text = "TV Box Manager"
            textSize = 24f
            setTextColor(Color.parseColor("#111827"))
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(headerTitle)

        val subTitle = TextView(this).apply {
            text = "Conecte via IP (Depuração USB/Rede ativada na TV)"
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, 4, 0, 20)
        }
        container.addView(subTitle)

        val connCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardBg("#FFFFFF", 20f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val savedIp = getSharedPreferences("tv_prefs", MODE_PRIVATE).getString("last_ip", "192.168.1.")

        val ipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        ipInput = EditText(this).apply {
            setText(savedIp)
            hint = "Ex: 192.168.1.105"
            textSize = 16f
            setTextColor(Color.parseColor("#111827"))
            setHintTextColor(Color.parseColor("#9CA3AF"))
            background = createCardBg("#F3F4F6", 12f)
            setPadding(20, 16, 20, 16)
            singleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 12, 0)
            }
        }

        connectBtn = Button(this).apply {
            text = "Conectar"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = createCardBg("#2563EB", 12f)
            setPadding(24, 16, 24, 16)
            setOnClickListener {
                val ip = ipInput.text.toString().trim()
                if (ip.isNotEmpty()) {
                    connectToTv(ip)
                } else {
                    Toast.makeText(this@MainActivity, "Digite o IP da TV Box", Toast.LENGTH_SHORT).show()
                }
            }
        }

        ipRow.addView(ipInput)
        ipRow.addView(connectBtn)
        connCard.addView(ipRow)

        statusText = TextView(this).apply {
            text = "● Desconectado"
            textSize = 13f
            setTextColor(Color.parseColor("#DC2626"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 14, 0, 0)
        }
        connCard.addView(statusText)
        container.addView(connCard)

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        grid.addView(createActionTile("🧹", "Tidy up\n(Limpar RAM)", "#2563EB") {
            runCleanRam()
        })

        grid.addView(createActionTile("🏠", "Home screen\n(Mudar Launcher)", "#15803D") {
            openLauncherSelector()
        })

        grid.addView(createActionTile("📦", "Free up space\n(Limpar Cache)", "#0F766E") {
            runCleanCache()
        })

        grid.addView(createActionTile("📱", "Apps\n(Gerenciar TV)", "#1D4ED8") {
            openRemoteAppsManager()
        })

        grid.addView(createActionTile("🎮", "Remote\n(Controle Remoto)", "#7C3AED") {
            openRemoteControllerDialog()
        })

        grid.addView(createActionTile("✅", "Check my TV\n(Info do Sistema)", "#D97706") {
            checkTvInfo()
        })

        container.addView(grid)

        logText = TextView(this).apply {
            text = "Pronto para conectar na TV Box (porta 5555)."
            textSize = 12f
            setTextColor(Color.parseColor("#374151"))
            background = createCardBg("#E5E7EB", 14f)
            setPadding(20, 18, 20, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 20, 0, 0) }
        }
        container.addView(logText)

        root.addView(container)
        setContentView(root)
    }

    private fun createActionTile(icon: String, label: String, colorHex: String, onClick: () -> Unit): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = createCardBg(colorHex, 24f)
            setPadding(20, 32, 20, 32)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (!isConnected) {
                    Toast.makeText(this@MainActivity, "Conecte primeiro no IP da TV Box!", Toast.LENGTH_SHORT).show()
                } else {
                    onClick()
                }
            }
        }

        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(10, 10, 10, 10)
        }
        tile.layoutParams = params

        val iconView = TextView(this).apply {
            text = icon
            textSize = 30f
            gravity = Gravity.CENTER
        }

        val titleView = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }

        tile.addView(iconView)
        tile.addView(titleView)
        return tile
    }

    private fun createCardBg(colorHex: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = radius
        }
    }

    private fun updateLog(msg: String) {
        runOnUiThread {
            logText.text = msg
        }
    }

    private fun connectToTv(ip: String) {
        getSharedPreferences("tv_prefs", MODE_PRIVATE).edit().putString("last_ip", ip).apply()
        statusText.text = "● Conectando em $ip:5555... (Olhe a tela da TV e clique em Permitir!)"
        statusText.setTextColor(Color.parseColor("#D97706"))
        connectBtn.isEnabled = false

        executor.execute {
            try {
                adbManager?.disconnect()
                val manager = SimpleAdbManager(applicationContext)
                val connected = manager.connect(ip, 5555)
                adbManager = manager
                isConnected = connected

                runOnUiThread {
                    connectBtn.isEnabled = true
                    if (connected) {
                        statusText.text = "● Conectado em $ip:5555"
                        statusText.setTextColor(Color.parseColor("#15803D"))
                        updateLog("Conexão estabelecida com sucesso! Escolha uma função acima.")
                    } else {
                        statusText.text = "● Falha ao conectar"
                        statusText.setTextColor(Color.parseColor("#DC2626"))
                        updateLog("Não foi possível conectar em $ip:5555. Verifique se a Depuração USB/Rede está ativa na TV Box.")
                    }
                }
            } catch (e: Exception) {
                isConnected = false
                runOnUiThread {
                    connectBtn.isEnabled = true
                    statusText.text = "● Erro na conexão"
                    statusText.setTextColor(Color.parseColor("#DC2626"))
                    updateLog("Erro: ${e.message ?: "Verifique o IP e a autorização na TV"}")
                }
            }
        }
    }

    private fun executeAdbShell(command: String, onResult: (String) -> Unit) {
        executor.execute {
            try {
                val manager = adbManager ?: throw IllegalStateException("Não conectado")
                val stream: AdbStream = manager.openStream("shell:$command")
                val reader = BufferedReader(InputStreamReader(stream.openInputStream(), StandardCharsets.UTF_8))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                stream.close()
                onResult(output.toString().trim())
            } catch (e: Exception) {
                onResult("Erro ao executar comando: ${e.message}")
            }
        }
    }

    private fun runCleanRam() {
        updateLog("Limpando memória RAM e fechando apps em segundo plano na TV...")
        executeAdbShell("am kill-all && sync && echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; free -m") { res ->
            updateLog("Limpeza de RAM concluída!\n$res")
            runOnUiThread {
                Toast.makeText(this, "TV Box otimizada!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runCleanCache() {
        updateLog("Liberando espaço de cache na TV Box...")
        executeAdbShell("pm trim-caches 999999999999 && df -h /data") { res ->
            updateLog("Cache limpo com sucesso!\n$res")
            runOnUiThread {
                Toast.makeText(this, "Cache da TV Box limpo!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openLauncherSelector() {
        updateLog("Buscando launchers instalados na TV Box...")
        val cmd = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        executeAdbShell(cmd) { output ->
            val lines = output.lines()
                .map { it.trim() }
                .filter { it.contains("/") && !it.startsWith("priority") }
                .distinct()

            runOnUiThread {
                if (lines.isEmpty()) {
                    updateLog("Nenhum launcher alternativo encontrado. Saída: $output")
                    return@runOnUiThread
                }

                val items = lines.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Escolha o Launcher da TV Box")
                    .setItems(items) { _, which ->
                        val chosenComponent = items[which]
                        setAndLaunchHome(chosenComponent)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun setAndLaunchHome(componentName: String) {
        updateLog("Definindo $componentName como launcher padrão...")
        val cmd = "cmd package set-home-activity $componentName && am start -a android.intent.action.MAIN -c android.intent.category.HOME"
        executeAdbShell(cmd) { res ->
            updateLog("Launcher alterado para: $componentName\n$res")
            runOnUiThread {
                Toast.makeText(this, "Launcher ativado na TV!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openRemoteAppsManager() {
        updateLog("Carregando lista de apps instalados na TV Box...")
        executeAdbShell("pm list packages -3") { output ->
            val pkgs = output.lines()
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .sorted()

            runOnUiThread {
                if (pkgs.isEmpty()) {
                    updateLog("Nenhum app de usuário encontrado.")
                    return@runOnUiThread
                }
                val items = pkgs.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Apps na TV Box (${items.size})")
                    .setItems(items) { _, which ->
                        val selectedPkg = items[which]
                        showAppActionsDialog(selectedPkg)
                    }
                    .setNegativeButton("Fechar", null)
                    .show()
            }
        }
    }

    private fun showAppActionsDialog(pkg: String) {
        val options = arrayOf("▶ Abrir na TV", "🛑 Forçar Parada (Limpar RAM)", "🗑️ Limpar Dados/Cache do App")
        AlertDialog.Builder(this)
            .setTitle(pkg)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> executeAdbShell("monkey -p $pkg -c android.intent.category.LAUNCHER 1") {
                        updateLog("Abrindo $pkg na TV...")
                    }
                    1 -> executeAdbShell("am force-stop $pkg") {
                        updateLog("App $pkg encerrado na TV!")
                    }
                    2 -> executeAdbShell("pm clear $pkg") { res ->
                        updateLog("Dados de $pkg limpos: $res")
                    }
                }
            }
            .show()
    }

    private fun openRemoteControllerDialog() {
        val keys = arrayOf(
            "⬆️ Cima (KEYCODE_DPAD_UP)" to 19,
            "⬇️ Baixo (KEYCODE_DPAD_DOWN)" to 20,
            "⬅️ Esquerda (KEYCODE_DPAD_LEFT)" to 21,
            "➡️ Direita (KEYCODE_DPAD_RIGHT)" to 22,
            "🆗 OK / Enter (KEYCODE_DPAD_CENTER)" to 23,
            "🔙 Voltar (KEYCODE_BACK)" to 4,
            "🏠 Tela Inicial / Home (KEYCODE_HOME)" to 3,
            "🔊 Aumentar Volume" to 24,
            "🔉 Diminuir Volume" to 25
        )
        val labels = keys.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Controle Remoto TV Box")
            .setItems(labels) { _, which ->
                val code = keys[which].second
                executeAdbShell("input keyevent $code") {
                    updateLog("Comando enviado: ${keys[which].first}")
                }
            }
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun checkTvInfo() {
        updateLog("Verificando sistema da TV Box...")
        val cmd = "echo 'Modelo:' \$(getprop ro.product.model) && echo 'Android:' \$(getprop ro.build.version.release) && free -h"
        executeAdbShell(cmd) { res ->
            updateLog(res)
        }
    }

    private class SimpleAdbManager(context: android.content.Context) : AbsAdbConnectionManager() {
        private val privateKey: PrivateKey
        private val certificate: Certificate

        init {
            api = Build.VERSION.SDK_INT
            val keyFile = File(context.filesDir, "adb_key.pk8")
            val certFile = File(context.filesDir, "adb_cert.der")

            if (keyFile.exists() && certFile.exists()) {
                val keyBytes = FileInputStream(keyFile).use { it.readBytes() }
                val kf = KeyFactory.getInstance("RSA")
                privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(keyBytes))

                val cf = CertificateFactory.getInstance("X.509")
                certificate = FileInputStream(certFile).use { cf.generateCertificate(it) }
            } else {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(2048)
                val kp = kpg.generateKeyPair()
                privateKey = kp.private

                val certGen = SelfSignedCertHelper.generate(kp)
                certificate = certGen

                FileOutputStream(keyFile).use { it.write(privateKey.encoded) }
                FileOutputStream(certFile).use { it.write(certificate.encoded) }
            }
        }

        override fun getPrivateKey(): PrivateKey = privateKey
        override fun getCertificate(): Certificate = certificate
        override fun getDeviceName(): String = "TVBoxManagerMobile"
    }

    private object SelfSignedCertHelper {
        fun generate(keyPair: java.security.KeyPair): Certificate {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val alias = "adb_temp_cert_${System.currentTimeMillis()}"
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setCertificateSubject(X500Principal("CN=TVBoxManager"))
                .setCertificateSerialNumber(java.math.BigInteger.ONE)
                .setCertificateNotBefore(Date(System.currentTimeMillis() - 86400000L))
                .setCertificateNotAfter(Date(System.currentTimeMillis() + 315360000000L))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val cert = ks.getCertificate(alias)
            ks.deleteEntry(alias)
            return cert
        }
    }
}
