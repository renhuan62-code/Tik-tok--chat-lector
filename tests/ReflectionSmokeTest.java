import android.content.Context;
import android.content.Intent;
import com.renzo.tiktokchatlector.LiveReaderService;

public class ReflectionSmokeTest {
  public static void main(String[] args) throws Exception {
    LiveReaderService service = new LiveReaderService();
    service.onCreate();
    Intent start = new Intent().setAction(LiveReaderService.ACTION_START)
        .putExtra(LiveReaderService.EXTRA_USERNAME, "usuario_prueba");
    service.onStartCommand(start, 0, 1);
    Thread.sleep(250);
    boolean connected = false, comment = false;
    for (Intent i : Context.broadcasts) {
      if (LiveReaderService.ACTION_STATUS.equals(i.getAction()) && i.getStringExtra(LiveReaderService.EXTRA_STATUS).contains("Conectado a @usuario_prueba")) connected = true;
      if (LiveReaderService.ACTION_COMMENT.equals(i.getAction()) && i.getStringExtra(LiveReaderService.EXTRA_COMMENT).contains("usuario_prueba: comentario simulado")) comment = true;
    }
    if (!connected) throw new AssertionError("No se emitió estado conectado en prueba simulada");
    if (!comment) throw new AssertionError("No se emitió comentario simulado");
    System.out.println("PASS: callback de conexión y callback de comentario usando biblioteca falsa de prueba.");
    service.onDestroy();
  }
}
