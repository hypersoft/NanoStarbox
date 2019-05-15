package box.star.net;

import box.star.Tools;
import box.star.content.MimeTypeMap;
import box.star.content.MimeTypeScanner;
import box.star.contract.Nullable;
import box.star.io.Streams;
import box.star.net.http.response.Status;
import box.star.net.tools.MimeTypeDriver;
import box.star.net.tools.ServerContent;
import box.star.net.tools.ServerResult;
import box.star.text.MacroShell;
import box.star.text.basic.Scanner;

import org.mozilla.javascript.*;
import org.mozilla.javascript.tools.shell.Global;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import static box.star.net.http.HTTPServer.MIME_HTML;

public class RhinoPageDriver implements MimeTypeDriver<WebService>, MimeTypeDriver.WithMediaMapControlPort, MimeTypeScanner {
  public final static String NANO_STARBOX_JAVASCRIPT_SERVER_PAGE = "text/html, application/x-nano-starbox-javascript-server-page";
  private Global global;
  public RhinoPageDriver(Global global){
    this.global = global;
  }
  @Override
  public void openMimeTypeMap(MimeTypeMap controlPort) {
    controlPort.putIfAbsent("jsp", NANO_STARBOX_JAVASCRIPT_SERVER_PAGE);
  }
  public RhinoPageDriver(){this((List)null);}
  public RhinoPageDriver(@Nullable List<String> moduleDirectories){
    global = new Global();
    Context cx = Context.enter();
    global.init(cx);
    if (moduleDirectories == null){
      String modulePath = Tools.makeNotNull(System.getenv("JSP_MODULE_URIS"), System.getProperty("box.star.net.jsp.module.uris"));
      if (modulePath != null)
        global.installRequire(cx, Arrays.asList(modulePath.split(";")), false);
      else
        global.installRequire(cx, null, false);
    } else {
      global.installRequire(cx, moduleDirectories, false);
    }
    Context.exit();
  }
  private Scriptable getScriptShell(Context cx, @Nullable Scriptable parent) {
    return ScriptRuntime.newObject(cx, Tools.makeNotNull(parent, global), "Object", null);
  }
  @Override
  public ServerResult createMimeTypeResult(WebService server, ServerContent content) {
    Context cx = Context.enter();
    try {
      String uri = content.session.getUri();
      Object location = server.getFile(uri);
      if (location != null) {
        if (((File)location).isDirectory());
        else location = ((File)location).getParentFile();
      } else {
        location = URI.create(server.getAddress() +"/"+ uri.substring(0, Math.max(0, uri.lastIndexOf("/"))).substring(1)).toURL();
      }
      Scriptable jsThis = getScriptShell(cx, global);
      ScriptRuntime.setObjectProp(jsThis, "global", global, cx);
      ScriptRuntime.setObjectProp(jsThis, "directory", Context.javaToJS(location, jsThis), cx);
      ScriptRuntime.setObjectProp(jsThis, "server", Context.javaToJS(server, jsThis), cx);
      ScriptRuntime.setObjectProp(jsThis, "session", Context.javaToJS(content.session, jsThis), cx);
      InputStream sourceStream = content.getStream();
      Scanner scanner = new Scanner(content.session.getUri(), Streams.readWholeString(sourceStream));
      sourceStream.close();
      MacroShell documentBuilder = new MacroShell(System.getenv());
      ScriptRuntime.setObjectProp(jsThis, "shell", Context.javaToJS(documentBuilder, jsThis), cx);
      documentBuilder.addCommand("js", new MacroShell.Command(){
        @Override
        protected String run(String command, Stack<String> parameters) {
          StringBuilder output = new StringBuilder();
          for (String p: parameters) output.append((String)
              Context.jsToJava(
                  cx.evaluateString(jsThis, p,
                      scanner.getPath(), (int) scanner.getLine(),
                      null), String.class)
          );
          return output.toString();
        }
      });
      if (content.mimeType.equals(NANO_STARBOX_JAVASCRIPT_SERVER_PAGE)) content.mimeType = MIME_HTML;
      return new ServerResult(content.session, Status.OK, content.mimeType, documentBuilder.start(scanner));
    } catch (Exception e){throw new RuntimeException(e);}
    finally {
      Context.exit();
    }
  }

  private final static String HTML_MAGIC = "<!MIME "+ NANO_STARBOX_JAVASCRIPT_SERVER_PAGE+">";

  private boolean detectHtmlDocument(BufferedInputStream source) throws IOException {
    String scan;
    int SEEK = HTML_MAGIC.length();
    source.mark(SEEK);
    scan = new Scanner(NANO_STARBOX_JAVASCRIPT_SERVER_PAGE, source).nextFieldLength(SEEK,'>');
    source.reset();
    if ((scan + '>').equals(HTML_MAGIC)){
      //noinspection ResultOfMethodCallIgnored
      source.skip(SEEK);
      return true;
    }
    return false;
  }

  @Override
  public String scanMimeType(BufferedInputStream source) {
    try {
      if (detectHtmlDocument(source))
        return NANO_STARBOX_JAVASCRIPT_SERVER_PAGE; else return null;
    } catch (Exception e) { throw new RuntimeException(e); }
  }

}