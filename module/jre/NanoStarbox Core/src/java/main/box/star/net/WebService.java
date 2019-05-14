package box.star.net;

import box.star.content.MimeTypeDriver;
import box.star.content.MimeTypeMap;
import box.star.content.MimeTypeProvider;
import box.star.net.http.HTTPServer;
import box.star.net.http.IHTTPSession;
import box.star.net.http.response.Response;
import box.star.net.tools.*;

import java.io.BufferedInputStream;
import java.util.Hashtable;
import java.util.Map;

public class WebService extends HTTPServer implements MimeTypeProvider {

  public final Map<String, ContentProvider> contentProviders = new Hashtable<>();
  public final Map<String, MimeTypeDriver> mimeTypeDrivers = new Hashtable<>();
  public final MimeTypeMap mimeTypeMap = new MimeTypeMap();

  public WebService() { super(); }

  public WebService mountContentProvider(ContentProvider contentProvider){
    contentProviders.put(contentProvider.getBaseUri(), contentProvider);
    return this;
  }

  public WebService(String host, int port){
    super();
    configuration.set(CONFIG_HOST, host);
    configuration.set(CONFIG_PORT, port);
  }

  public MimeTypeMap getMimeTypeMap() {
    return mimeTypeMap;
  }

  public ServerContent getContent(IHTTPSession session){
    String uri = session.getUri();
    // first: uri-equality
    for (String path:contentProviders.keySet()){
      ContentProvider provider = contentProviders.get(path);
      if (path.equals(uri)) return provider.getContent(session);
    }
    // second: parent-uri-equality
    while (! uri.equals("/") ) {
      uri = uri.substring(0, Math.max(0, uri.lastIndexOf('/')));
      if (uri.equals("")) uri = "/";
      for (String path:contentProviders.keySet()){
        ContentProvider provider = contentProviders.get(path);
        if (path.equals(uri))return provider.getContent(session);
      }
    }
    // third: fail-silently
    return null;
  }

  protected ServerResult getResult(ServerContent content) {
    if (content == null) return null;
    MimeTypeDriver driver = mimeTypeDrivers.get(content.mimeType);
    if (driver != null) return driver.createMimeTypeResult(content);
    return new ServerResult(content);
  }

  @Override
  protected Response serviceRequest(IHTTPSession session) {
    try {
      ServerResult serverResult = getResult(getContent(session));
      if (serverResult == null) return notFoundResponse();
      return serverResult.response();
    } catch (Exception e){
      return this.serverExceptionResponse(e);
    }
  }

  public String readMimeTypeMagic(BufferedInputStream stream) {
    return mimeTypeMap.readMimeTypeMagic(stream);
  }

  public void addMimeTypeDriver(String mimeType, MimeTypeDriver driver) {
    mimeTypeDrivers.put(mimeType, driver);
  }

}
