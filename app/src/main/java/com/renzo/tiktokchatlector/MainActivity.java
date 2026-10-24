package com.renzo.tiktokchatlector;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private EditText username;
    private TextView status, log;
    private TextToSpeech testTts;
    private boolean testTtsReady = false;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (LiveReaderService.ACTION_STATUS.equals(intent.getAction())) {
                status.setText(intent.getStringExtra(LiveReaderService.EXTRA_STATUS));
            } else if (LiveReaderService.ACTION_COMMENT.equals(intent.getAction())) {
                appendLog(intent.getStringExtra(LiveReaderService.EXTRA_COMMENT));
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        testTts = new TextToSpeech(this, this);
        buildUi();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(LiveReaderService.ACTION_STATUS);
        filter.addAction(LiveReaderService.ACTION_COMMENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    @Override protected void onStop() {
        try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
        super.onStop();
    }

    private void buildUi() {
        int bg = Color.rgb(15, 18, 28);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(22, 28, 22, 22);
        outer.setBackgroundColor(bg);

        TextView title = new TextView(this);
        title.setText("TikTok Chat Lector"); title.setTextColor(Color.WHITE); title.setTextSize(25); title.setTypeface(null, Typeface.BOLD);
        outer.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Lee comentarios de tu LIVE en voz alta"); subtitle.setTextColor(Color.LTGRAY); subtitle.setTextSize(15);
        outer.addView(subtitle);

        username = new EditText(this);
        username.setSingleLine(true); username.setHint("@tu_usuario_de_TikTok"); username.setHintTextColor(Color.GRAY); username.setTextColor(Color.WHITE); username.setPadding(12, 8, 12, 8);
        outer.addView(username);

        Button connect = button("INICIAR LECTURA");
        connect.setOnClickListener(v -> startReader()); outer.addView(connect);
        Button stop = button("DETENER LECTURA");
        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, LiveReaderService.class).setAction(LiveReaderService.ACTION_STOP);
            startService(i);
        }); outer.addView(stop);
        Button test = button("PROBAR VOZ EN ESPAÑOL");
        test.setOnClickListener(v -> {
            if (testTtsReady) testTts.speak("Hola. Esta es una prueba de voz en español.", TextToSpeech.QUEUE_FLUSH, null, "test-voice");
            else appendLog("El motor de voz todavía no está listo.");
        }); outer.addView(test);
        Button ttsSettings = button("CONFIGURAR MOTOR DE VOZ");
        ttsSettings.setOnClickListener(v -> {
            try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        }); outer.addView(ttsSettings);
        Button battery = button("CONFIGURAR BATERÍA");
        battery.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try { Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS); i.setData(Uri.parse("package:" + getPackageName())); startActivity(i); }
                catch (Exception ignored) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            }
        }); outer.addView(battery);

        status = new TextView(this); status.setText("Estado: desconectado"); status.setTextColor(Color.rgb(120, 220, 160)); status.setTextSize(15); status.setPadding(0, 14, 0, 8); outer.addView(status);
        TextView note = new TextView(this);
        note.setText("La conexión usa una biblioteca comunitaria no oficial. Mantén visible la notificación para la lectura en segundo plano."); note.setTextColor(Color.LTGRAY); note.setTextSize(12); note.setPadding(0, 0, 0, 10); outer.addView(note);
        log = new TextView(this); log.setText("Aquí aparecerán los comentarios recibidos."); log.setTextColor(Color.WHITE); log.setTextSize(14);
        ScrollView scroll = new ScrollView(this); scroll.addView(log); outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(outer);
    }

    private Button button(String text) { Button b = new Button(this); b.setText(text); b.setAllCaps(false); return b; }

    private void startReader() {
        String handle = username.getText().toString().trim().replace("@", "");
        if (handle.isEmpty()) { status.setText("Escribe tu usuario público de TikTok."); return; }
        Intent intent = new Intent(this, LiveReaderService.class).setAction(LiveReaderService.ACTION_START);
        intent.putExtra(LiveReaderService.EXTRA_USERNAME, handle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
        status.setText("Iniciando servicio para @" + handle + "…");
    }

    private void appendLog(String line) {
        if (line == null || line.isEmpty()) return;
        String old = log.getText().toString();
        String[] lines = (old + (old.isEmpty() ? "" : "\n") + line).split("\n");
        StringBuilder trimmed = new StringBuilder();
        int start = Math.max(0, lines.length - 80);
        for (int i = start; i < lines.length; i++) { if (trimmed.length() > 0) trimmed.append('\n'); trimmed.append(lines[i]); }
        log.setText(trimmed.toString());
    }

    @Override public void onInit(int result) {
        if (result == TextToSpeech.SUCCESS) {
            int language = testTts.setLanguage(Locale.forLanguageTag("es-ES"));
            testTtsReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED;
            if (!testTtsReady && status != null) status.setText("Instala o selecciona una voz española en Ajustes de voz.");
        }
    }

    @Override protected void onDestroy() {
        if (testTts != null) { testTts.stop(); testTts.shutdown(); }
        super.onDestroy();
    }
}
