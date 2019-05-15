package box.star.net.tools;

import box.star.content.MimeTypeMap;
import box.star.net.http.IHTTPSession;

import java.io.File;

public class FileContentProvider extends ContentProvider implements NativeContentProvider {

  protected File root;

  public FileContentProvider(MimeTypeMap mimeTypeMap, String baseUri, File root) {
    super(mimeTypeMap, baseUri);
    this.root = root;
  }

  @Override
  public ServerContent getContent(IHTTPSession session) {
    String uri = session.getUri();
    return new ServerContent(session, getUriMimeType(uri), getFile(uri));
  }

  public File getFile(String uri) {
    return new File(root, uri.substring(1));
  }

}
