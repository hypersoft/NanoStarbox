package box.star.net.tools;

import box.star.net.http.IHTTPSession;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Load the zip archive at the given virtual directory.
 */
public class ZipSiteProvider extends ContentProvider implements Closeable {

  public Map<String, ZipEntry> vfs;
  protected ZipFile zipFile;
  protected File file;
  protected long zipTime;

  public ZipSiteProvider(String baseUri, File zipFile) {
    super(baseUri);
    this.file = zipFile;
  }

  private void loadZipEntries() {
    try {
      this.zipFile = new ZipFile(file);
      this.zipTime = file.lastModified();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    String virtualDirectory = getBaseUri();
    if (virtualDirectory == "/") virtualDirectory = "";
    Enumeration<? extends ZipEntry> entries = this.zipFile.entries();
    vfs = new Hashtable<>();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      vfs.put(virtualDirectory + "/" + entry.getName(), entry);
      String eName = entry.getName();
      if (eName.matches("^([^\0]*(index)\\.[^\0]+)$")) {
        vfs.put(virtualDirectory + "/" + eName.substring(0, Math.max(0, eName.lastIndexOf("/"))), entry);
      }
    }
  }

  @Override
  public ServerContent getContent(IHTTPSession session) {
    String target = session.getUri();
    if (target.equals(getBaseUri()) && !target.endsWith("/")) return redirect(target + "/");
    if (file.exists()) {
      if (zipTime < file.lastModified()) loadZipEntries();
    } else if (zipFile == null) return notFound(target);
    try {
      if (vfs.containsKey(target)) {
        ZipEntry content = vfs.get(target);
        if (content.isDirectory() && !target.endsWith("/")) return redirect(target + "/");
        if (content.isDirectory()) return notFound(target);
        InputStream inputStream = this.zipFile.getInputStream(content);
        return new ServerContent(session, getUriMimeType(content.getName()), inputStream, content.getSize(), zipTime);
      }
      return notFound(target);
    }
    catch (Exception e) {
      throw new RuntimeException(target, e);
    }
  }

  @Override
  public void close() throws IOException {
    zipFile.close();
    zipFile = null;
    vfs = null;
    zipTime = 0;
  }

}
