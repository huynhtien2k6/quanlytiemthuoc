package vn.pharmacy.dao;

import java.io.*;
import java.nio.channels.*;
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
    } catch (OverlappingFileLockException e) {
      channel.close();
      throw new IOException("Dữ liệu đang được mở bởi phiên khác", e);
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

  public Map<String, List<List<String>>> read() throws IOException {
    var db = Schema.empty();
    if (!exists()) return db;
    var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    if (lines.isEmpty() || !lines.get(0).equals("PHARMACY_TXT_V1"))
      throw new IOException("Sai định dạng dữ liệu");
    try {
      for (String line : lines.subList(1, lines.size())) {
        String[] a = line.split("\\t", -1);
        Schema.table(a[0]);
        var row = new ArrayList<String>();
        for (int i = 1; i < a.length; i++)
          row.add(new String(Base64.getDecoder().decode(a[i]), StandardCharsets.UTF_8));
        db.get(a[0]).add(row);
      }
    } catch (RuntimeException e) {
      throw new IOException("Dữ liệu TXT bị hỏng", e);
    }
    return db;
  }

  public void write(Map<String, List<List<String>>> db) throws IOException {
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

  public void close() throws IOException {
    lock.release();
    channel.close();
  }
}
