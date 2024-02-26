package io.opentelemetry.instrumentation.awssdk.v2_2;

import software.amazon.awssdk.http.Abortable;
import software.amazon.awssdk.http.AbortableInputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class InputStreamCapture {

  public final InputStream inputStream;
  public final byte[] capturedData;

  public InputStreamCapture(InputStream inputStream, byte[] capturedData) {
    this.inputStream = inputStream;
    this.capturedData = capturedData;
  }

  public static InputStreamCapture capture(InputStream inputStream, int limit) {
    BufferedInputStream bis = new BufferedInputStream(inputStream);
    bis.mark(limit);
    IOException exception = null;
    byte[] captured;
    try {
      captured = toByteArrayWithLimit(bis, limit);
      bis.reset();
    } catch (IOException e) {
      captured = new byte[0];
      exception = e;
    }

    InputStream is1 = bis;
    if (exception != null){
      IOException movedException = exception;
      is1 = new InputStream() {
        @Override
        public int read() throws IOException {
          throw movedException;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
          throw movedException;
        }
      };
    }

    InputStream is2;
    if (inputStream instanceof Abortable) {
        is2 = AbortableInputStream.create(is1, (Abortable) inputStream);
    } else {
        is2 = AbortableInputStream.create(is1);
    }

    return new InputStreamCapture(is2, captured);
  }

  public static byte[] toByteArrayWithLimit(InputStream is, int maxBytes) throws IOException {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      int bytesLeftUntilMax = maxBytes;
      byte[] buffer = new byte[4096];
      int n;
      while (bytesLeftUntilMax > 0 && (n = is.read(buffer, 0, Math.min(buffer.length, bytesLeftUntilMax))) != -1) {
        output.write(buffer, 0, n);
        bytesLeftUntilMax -= n;
      }

      return output.toByteArray();
    }
  }
}
