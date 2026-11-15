package vn.pharmacy.dao;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import vn.pharmacy.dto.Schema;

/** Single atomic snapshot, UTF-8 text, Base64 fields; cross-process exclusive lock. */
public final class TextDatabase implements AutoCloseable {
  private final Path file;
  private final FileChannel channel;
  private final FileLock lock;

  public TextDatabase(Path folder) throws IOException {
    Files.createDirectories(folder);
    file = folder.resolve("store.txt");
    channel =
        FileChannel.open(
            folder.resolve("store.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock l;
    try {
      l = channel.tryLock();
    } catch (IOException | RuntimeException e) {
      try {
        channel.close();
      } catch (IOException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw new IOException("Không khóa được dữ liệu; có thể đang được mở bởi phiên khác", e);
    }
    if (l == null) {
      channel.close();
      throw new IOException("Dữ liệu đang được mở bởi phiên khác");
    }
    lock = l;
  }

  public boolean exists() {
    return Files.exists(file);
  }

  public synchronized Map<String, List<List<String>>> read() throws IOException {
    ensureOpen();
    var db = Schema.empty();
    if (!exists()) return db;
    var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    if (lines.isEmpty() || !lines.get(0).equals("PHARMACY_TXT_V1"))
      throw new IOException("Sai định dạng dữ liệu");
    try {
      for (String line : lines.subList(1, lines.size())) {
        String[] a = line.split("\\t", -1);
        var table = Schema.table(a[0]);
        if (a.length != table.columns().length + 1)
          throw new IOException("Sai số cột TXT tại bảng " + a[0]);
        var row = new ArrayList<String>();
        for (int i = 1; i < a.length; i++)
          row.add(
              StandardCharsets.UTF_8
                  .newDecoder()
                  .onMalformedInput(CodingErrorAction.REPORT)
                  .onUnmappableCharacter(CodingErrorAction.REPORT)
                  .decode(ByteBuffer.wrap(Base64.getDecoder().decode(a[i])))
                  .toString());
        db.get(a[0]).add(row);
      }
    } catch (RuntimeException e) {
      throw new IOException("Dữ liệu TXT bị hỏng", e);
    }
    return db;
  }

  public synchronized void write(Map<String, List<List<String>>> db) throws IOException {
    ensureOpen();
    if (db == null || !db.keySet().equals(Schema.empty().keySet()))
      throw new IOException("Thiếu/thừa bảng TXT");
    for (var table : Schema.TABLES) {
      if (db.get(table.key()) == null) throw new IOException("Bảng TXT bị rỗng cấu trúc");
      for (var row : db.get(table.key()))
        if (row == null
            || row.size() != table.columns().length
            || row.stream().anyMatch(Objects::isNull))
          throw new IOException("Sai cấu trúc dòng TXT: " + table.key());
    }
    var s = new StringBuilder("PHARMACY_TXT_V1\n");
    for (var t : Schema.TABLES)
      for (var r : db.get(t.key())) {
        s.append(t.key());
        for (String v : r)
          s.append('\t')
              .append(Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)));
        s.append('\n');
      }
    Path temp = Files.createTempFile(file.getParent(), "snapshot-", ".tmp");
    try {
      Files.writeString(temp, s, StandardCharsets.UTF_8);
      try (var c = FileChannel.open(temp, StandardOpenOption.WRITE)) {
        c.force(true);
      }
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private void ensureOpen() throws IOException {
    if (!channel.isOpen() || !lock.isValid()) throw new IOException("Kho dữ liệu đã đóng");
  }

  public synchronized void close() throws IOException {
    if (!channel.isOpen()) return;
    try {
      if (lock.isValid()) lock.release();
    } finally {
      channel.close();
    }
  }
}
