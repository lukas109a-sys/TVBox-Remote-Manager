package com.lucas.tvmanager

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
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
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.Executors

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

        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        } catch (_: Throwable) {
        }

        setupUi()
    }

    private fun setupUi() {
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
            text = "Pronto! Digite o IP da TV Box e toque em Conectar."
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
        statusText.text = "● Conectando em $ip:5555... (Olhe a TV e permita a depuração)"
        statusText.setTextColor(Color.parseColor("#D97706"))
        connectBtn.isEnabled = false

        executor.execute {
            try {
                adbManager?.disconnect()
                val manager = SimpleAdbManager()
                val connected = manager.connect(ip, 5555)
                adbManager = manager
                isConnected = connected

                runOnUiThread {
                    connectBtn.isEnabled = true
                    if (connected) {
                        statusText.text = "● Conectado em $ip:5555"
                        statusText.setTextColor(Color.parseColor("#15803D"))
                        updateLog("Conectado à TV Box! Escolha uma opção acima.")
                    } else {
                        statusText.text = "● Falha ao conectar"
                        statusText.setTextColor(Color.parseColor("#DC2626"))
                        updateLog("Não conectou em $ip:5555. Confirme se a Depuração USB/Rede está ligada na TV Box.")
                    }
                }
            } catch (e: Throwable) {
                isConnected = false
                runOnUiThread {
                    connectBtn.isEnabled = true
                    statusText.text = "● Erro na conexão"
                    statusText.setTextColor(Color.parseColor("#DC2626"))
                    updateLog("Detalhe: ${e.javaClass.simpleName}:${e.message}")
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
            } catch (e: Throwable) {
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
                Toast.makeText(this, "Launcher ativ
