package com.renzo.tiktokchatlector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

/** Keeps chat reading alive while the UI is backgrounded. TikTok client API remains unofficial. */
public class LiveReaderService extends Service implements TextToSpeech.OnInitListener {
    public static final String ACTION_START = "com.renzo.tiktokchatlector.START";
    public static final String ACTION_STOP = "com.renzo.tiktokchatlector.STOP";
    public static final String EXTRA_USERNAME = "username";
    public static final String ACTION_STATUS = "com.renzo.tiktokchatlector.STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String ACTION_COMMENT = "com.renzo.tiktokchatlector.COMMENT";
    public static final String EXTRA_COMMENT = "comment";
    private static final String TAG = "LiveReaderService";
    private static final String CHANNEL_ID = "live_reader_channel";
    private static final int NOTIFICATION_ID = 4107;

    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile boolean stopped = false;
    private volatile boolean speechActive = false;
    private final Queue<String> speechQueue = new ArrayDeque<>();
    private Object liveClient;
    private Object liveBuilder;
    private Thread connectThread;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        tts = new TextToSpeech(this, this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopReader();
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String username = intent.getStringExtra(EXTRA_USERNAME);
            if (username == null || username.trim().isEmpty()) {
                broadcastStatus("Escribe un usuario público de TikTok.");
                stopSelf();
                return START_NOT_STICKY;
            }
            stopped = false;
            Notification notification = makeNotification("Conectando a @" + username);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            connect(username.trim().replace("@", ""));
        }
        return START_STICKY;
    }

    private void connect(String username) {
        if (connectThread != null && connectThread.isAlive()) {
            broadcastStatus("Ya hay un intento de conexión en curso.");
            return;
        }
        connectThread = new Thread(() -> {
            try {
                Class<?> api = loadApiClass();
                Method newClient = api.getMethod("newClient", String.class);
                Object builder = newClient.invoke(null, username);
                builder = addCallback(builder, "onComment", (client, event) -> {
                    String user = invokeChain(event, new String[]{"getUser", "getProfileName"});
                    if (user == null) user = invokeChain(event, new String[]{"getUser", "getName"});
                    String message = invokeChain(event, new String[]{"getText"});
                    if (message == null || message.trim().isEmpty()) return;
                    String line = (user == null || user.trim().isEmpty() ? "Comentario" : user) + ": " + message;
                    Intent broadcast = new Intent(ACTION_COMMENT).setPackage(getPackageName());
                    broadcast.putExtra(EXTRA_COMMENT, line);
                    sendBroadcast(broadcast);
                    enqueue(line);
                });
                builder = addCallback(builder, "onConnected", (client, event) -> {
                    broadcastStatus("Conectado a @" + username);
                    updateNotification("Conectado a @" + username + " · leyendo comentarios");
                });
                builder = addCallback(builder, "onDisconnected", (client, event) -> {
                    broadcastStatus("TikTok desconectó la conexión. Puedes volver a intentar.");
                    updateNotification("Conexión interrumpida");
                });
                builder = addCallback(builder, "onError", (client, event) -> {
                    String detail = invokeChain(event, new String[]{"getException", "getMessage"});
                    broadcastStatus("Error de conexión" + (detail == null ? "." : ": " + detail));
                });
                liveBuilder = builder;
                Method build = findNoArg(builder.getClass(), "buildAndConnect");
                if (build == null) throw new NoSuchMethodException("buildAndConnect() no encontrado");
                liveClient = build.invoke(builder);
                if (liveClient == null) liveClient = builder;
                broadcastStatus("Solicitud enviada; esperando respuesta de TikTok LIVE…");
            } catch (Throwable e) {
                Throwable root = e.getCause() != null ? e.getCause() : e;
                Log.e(TAG, "TikTok connection failed", root);
                broadcastStatus("No se pudo conectar: " + root.getClass().getSimpleName() + " - " + root.getMessage());
                updateNotification("No se pudo conectar");
            }
        }, "TikTokLive-Connect");
        connectThread.start();
    }

    private Class<?> loadApiClass() throws ClassNotFoundException {
        String[] candidates = {
            "io.github.jwdeveloper.tiktok.TikTokLive",
            "io.github.jwdeveloper.tiktoklive.TikTokLive",
            "com.github.jwdeveloper.tiktoklive.TikTokLive",
            "com.github.jwdeveloper.tiktok.TikTokLive"
        };
        ClassNotFoundException last = null;
        for (String name : candidates) {
            try { return Class.forName(name); }
            catch (ClassNotFoundException e) { last = e; }
        }
        throw last != null ? last : new ClassNotFoundException("TikTokLive API class not found");
    }

    private interface Callback { void call(Object client, Object event); }

    private Object addCallback(Object builder, String name, Callback callback) throws Exception {
        for (Method method : builder.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != 1) continue;
            Class<?> callbackType = method.getParameterTypes()[0];
            if (!callbackType.isInterface()) continue;
            Object proxy = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, (p, m, args) -> {
                if (args != null && args.length >= 2) callback.call(args[0], args[1]);
                return null;
            });
            Object returned = method.invoke(builder, proxy);
            return returned == null ? builder : returned;
        }
        if ("onComment".equals(name)) throw new NoSuchMethodException("Callback onComment no encontrado");
        return builder;
    }

    private Method findNoArg(Class<?> type, String name) {
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterTypes().length == 0) return method;
        return null;
    }

    private String invokeChain(Object start, String[] methods) {
        try {
            Object current = start;
            for (String name : methods) {
                if (current == null) return null;
                current = current.getClass().getMethod(name).invoke(current);
            }
            return current == null ? null : String.valueOf(current);
        } catch (Throwable ignored) { return null; }
    }

    private void enqueue(String line) {
        synchronized (speechQueue) {
            // Bounded queue protects phone memory. There is no daily message cap; if speech falls
            // far behind, oldest pending items are dropped to avoid an endless backlog.
            if (speechQueue.size() >= 250) speechQueue.poll();
            speechQueue.offer(line);
        }
        speakNext();
    }

    private void speakNext() {
        if (!ttsReady || tts == null || stopped || speechActive) return;
        final String next;
        synchronized (speechQueue) { next = speechQueue.poll(); }
        if (next == null) return;
        speechActive = true;
        int result = tts.speak(next, TextToSpeech.QUEUE_ADD, null, "comment-" + System.nanoTime());
        if (result == TextToSpeech.ERROR) {
            speechActive = false;
            broadcastStatus("Error de voz: revisa el motor de texto a voz de Android.");
        }
    }

    @Override public void onInit(int result) {
        if (result == TextToSpeech.SUCCESS) {
            int language = tts.setLanguage(Locale.forLanguageTag("es-ES"));
            ttsReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { speechActive = false; speakNext(); }
                @Override public void onError(String id) { speechActive = false; speakNext(); }
            });
            if (!ttsReady) broadcastStatus("No hay voz española disponible. Configura el motor de voz del teléfono.");
        } else broadcastStatus("No se pudo iniciar el motor de voz.");
    }

    private void stopReader() {
        stopped = true;
        if (tts != null) tts.stop();
        synchronized (speechQueue) { speechQueue.clear(); }
        speechActive = false;
        Object target = liveClient != null ? liveClient : liveBuilder;
        boolean disconnected = false;
        if (target != null) {
            for (String name : new String[]{"disconnect", "close", "stop"}) {
                Method method = findNoArg(target.getClass(), name);
                if (method != null) {
                    try { method.invoke(target); disconnected = true; break; }
                    catch (Throwable e) { Log.w(TAG, "Unable to call " + name, e); }
                }
            }
        }
        liveClient = null;
        liveBuilder = null;
        broadcastStatus(disconnected ? "Desconectado." : "Lectura detenida. La biblioteca no confirmó el cierre.");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Lectura de comentarios LIVE", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantiene activa la lectura de comentarios de TikTok LIVE");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification makeNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setContentTitle("TikTok Chat Lector")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, makeNotification(text));
    }

    private void broadcastStatus(String text) {
        Intent intent = new Intent(ACTION_STATUS).setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, text);
        sendBroadcast(intent);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopped = true;
        Object target = liveClient != null ? liveClient : liveBuilder;
        if (target != null) {
            for (String name : new String[]{"disconnect", "close", "stop"}) {
                Method method = findNoArg(target.getClass(), name);
                if (method != null) { try { method.invoke(target); break; } catch (Throwable ignored) { } }
            }
        }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
