package box.star.text.basic;

import box.star.contract.NotNull;
import box.star.io.Streams;
import box.star.text.Char;

import java.io.*;

import static box.star.text.Char.*;

public class Scanner implements Closeable{

  /**
   * Reader for the input.
   */
  protected Reader reader;
  protected boolean closeable;

  public void setLineEscape(boolean escapeLines){
    state.escapeLines = escapeLines;
  }

  public void setLineEscape(boolean escapeLines, boolean useUnderscore) {
    state.escapeLines = escapeLines;
    state.escapeUnderscoreLine = useUnderscore;
  }

  protected ScannerState state;

  /**
   * Call this after each successful main loop to clear the back-buffer.
   *
   * {@link #haveNext()} loads the next position into history.
   *
   */
  public void flushHistory(){
    if (state.haveNext())
      throw new Exception("cannot flush history",
          new IllegalStateException("buffer state is not synchronized"));
    state.clearHistory();
  }

  @Override
  public void close() {
    try /*  ignoring exceptions with final closure */ {
      if (closeable) reader.close();
    } catch (IOException ignored){}
    finally /*  complete */ {
      ;
    }
  }

  /**
   * Closes the file if closeable when the object is garbage collected.
   *
   * @throws Throwable
   */
  @Override
  protected void finalize() throws Throwable {
    close();
    super.finalize();
  }

  public Scanner(@NotNull String path, @NotNull Reader reader) {
    this.reader = reader.markSupported() ? reader : new BufferedReader(reader);
    this.state = new ScannerState(path);
  }

  public Scanner(@NotNull String path, @NotNull InputStream inputStream) {
    this(path, new InputStreamReader(inputStream));
    if (inputStream.equals(System.in)) return;
    closeable = true;
  }

  public Scanner(@NotNull String path, @NotNull String s) {
    this(path, new StringReader(s));
    this.closeable = true;
  }

  public Scanner(@NotNull File file) {
    this(file.getPath(), Streams.getUriStream(file.toURI()));
  }

  public boolean hasStateLock() {
    return state.snapshot;
  }

  @NotNull
  public ScannerStateLock getStateLock() {
    return new ScannerStateLock(this);
  }

  /**
   * Determine if the source string still contains characters that next()
   * can consume.
   *
   * @return true if not yet at the end of the source.
   * @throws Exception thrown if there is an error stepping forward
   *                   or backward while checking for more data.
   */

  public boolean haveNext() throws Exception {
    if (state.haveNext()) return true;
    else if (state.eof) return false;
    else {
      try {
        int c = this.reader.read();
        if (c <= 0){
          state.eof = true;
          return false;
        }
        state.recordCharacter(Char.valueOf(c));
        state.stepBackward();
        return true;
      }
      catch (IOException exception) { throw new Exception(exception); }
    }
  }

  /**
   * Checks if the end of the input has been reached.
   *
   * @return true if at the end of the file and we didn't step back
   */

  public boolean endOfSource() {
    return state.eof && !state.haveNext();
  }

  /**
   * Step backward one position.
   *
   * @throws Exception if unable to step backward
   */

  public void back() throws Exception { state.stepBackward(); }

  /**
   * Get the next character.
   *
   * @return
   * @throws Exception if read fails
   */

  public char next() throws Exception {
    if (state.haveNext()) return state.next();
    else {
      try {
        int c = this.reader.read();
        if (c <= 0){
          state.eof = true;
          return 0;
        }
        state.recordCharacter(Char.valueOf(c));
        return Char.valueOf(c);
      }
      catch (IOException exception) { throw new Exception(exception); }
    }
  }


  public char nextCharacter(char character, boolean caseSensitive) {
    char c = next();
    if (c == 0)
      throw syntaxError("Expected " + translate(character) + " and found end of text stream");
    if (!caseSensitive) {
      c = Char.toLowerCase(c);
      character = Char.toLowerCase(character);
    }
    if (character != c)
      throw this.syntaxError("Expected " + translate(c) + " and found " + translate(character));
    return c;
  }

  /**
   * Match the next string input with a source string.
   *
   * @param source
   * @param caseSensitive
   * @return
   * @throws SyntaxError if match fails
   */

  @NotNull
  public String nextString(@NotNull String source, boolean caseSensitive) throws SyntaxError {
    StringBuilder out = new StringBuilder();
    char[] sequence = source.toCharArray();
    for (char c : sequence) out.append(nextCharacter(c, caseSensitive));
    return out.toString();
  }

  /**
   * Scan and assemble characters while scan in map.
   *
   * @param map
   * @return
   * @throws Exception if read fails.
   */

  @NotNull
  public String nextMap(@NotNull char... map) throws Exception {
    char c;
    StringBuilder sb = new StringBuilder();
    do {
      c = this.next();
      if (Char.mapContains(c, map)) sb.append(c);
      else {
        this.back();
        break;
      }
    } while (c != 0);
    return sb.toString();
  }

  /**
   * Scan and assemble characters while scan in map and scan-length < max.
   *
   * @param max
   * @param map
   * @return
   * @throws Exception if read fails.
   */

  @NotNull
  public String nextMapLength(int max, @NotNull char... map) throws Exception {
    char c;
    StringBuilder sb = new StringBuilder();
    do {
      if (sb.length() == max) break;
      c = this.next();
      if (Char.mapContains(c, map)) sb.append(c);
      else {
        this.back();
        break;
      }
    } while (c != 0);
    return sb.toString();
  }

  /**
   * Scan and assemble characters while scan is not in map.
   *
   * @param map
   * @return
   * @throws Exception if read fails.
   */

  @NotNull
  public String nextField(@NotNull char... map) throws Exception {
    char c;
    StringBuilder sb = new StringBuilder();
    do {
      c = this.next();
      if (!Char.mapContains(c, map)) sb.append(c);
      else {
        this.back();
        break;
      }
    } while (c != 0);
    return sb.toString();
  }

  /**
   * Scan and assemble characters while scan is not in map and scan-length < max.
   *
   * @param max
   * @param map
   * @return
   * @throws Exception if read fails.
   */

  @NotNull
  public String nextFieldLength(int max, @NotNull char... map) throws Exception {
    char c;
    StringBuilder sb = new StringBuilder();
    do {
      if (sb.length() == max) break;
      c = this.next();
      if (!Char.mapContains(c, map)) sb.append(c);
      else {
        this.back();
        break;
      }
    } while (c != 0);
    return sb.toString();
  }

  /**
   * Get the next n characters.
   *
   * @param n The number of characters to take.
   * @return A string of n characters.
   * @throws Exception Substring bounds error if there are not
   *                   n characters remaining in the source string.
   */
  @NotNull

  public String nextLength(int n) throws Exception {
    if (n == 0) {
      return "";
    }
    char[] chars = new char[n];
    int pos = 0;
    while (pos < n) {
      chars[pos] = this.next();
      if (this.endOfSource()) {
        throw this.syntaxError("Substring bounds error");
      }
      pos += 1;
    }
    return new String(chars);
  }

  @NotNull
  final public String run(ScannerMethod method, Object... parameters) {
    method.reset();
    method.start(this, parameters);
    char c;
    do {
      c = next();
      method.collect(this, c);
      if (method.terminator(this, c)) break;
    } while (method.scanning(this));
    return method.compile(this);
  }

  public boolean backSlashMode(){
    return state.slashing;
  }


  public boolean escapeMode() {
    return state.escaped;
  }

  /**
   * Make a Exception to signal a syntax error.
   *
   * @param message The error message.
   * @return A Exception object, suitable for throwing
   */

  @NotNull
  public SyntaxError syntaxError(String message) {
    return new SyntaxError(message + this.claim());
  }

  /**
   * Make a Exception to signal a syntax error.
   *
   * @param message  The error message.
   * @param causedBy The throwable that caused the error.
   * @return A Exception object, suitable for throwing
   */

  @NotNull
  public SyntaxError syntaxError(@NotNull String message, @NotNull Throwable causedBy) {
    return new SyntaxError(message + this.claim(), causedBy);
  }


  public String claim() {
    return " at location = "+"{line: " + getLine() + ", column: " + getColumn() + ", index: "+getIndex()+", source: '"+getPath()+"'}";
  }


  public String toString() {
    return claim();
  }


  public String getPath() {
    return state.path;
  }


  public long getIndex() {
    return state.index;
  }


  public long getLine() {
    return state.line;
  }


  public long getColumn() {
    return state.column;
  }

  /**
   * The TextScanner.Exception is thrown by the TextScanner interface classes when things are amiss.
   *
   * @author Hypersoft-Systems: USA
   * @version 2015-12-09
   */
  public static class Exception extends RuntimeException {
    /**
     * Serialization ID
     */
    private static final long serialVersionUID = 0;

    /**
     * Constructs a TextScanner.Exception with an explanatory message.
     *
     * @param message Detail about the reason for the exception.
     */
    public Exception(final String message) {
      super(message);
    }

    /**
     * Constructs a TextScanner.Exception with an explanatory message and cause.
     *
     * @param message Detail about the reason for the exception.
     * @param cause   The cause.
     */
    public Exception(final String message, final Throwable cause) {
      super(message, cause);
    }

    /**
     * Constructs a new TextScanner.Exception with the specified cause.
     *
     * @param cause The cause.
     */
    public Exception(final Throwable cause) {
      super(cause.getMessage(), cause);
    }

  }

  /**
   * The TextScanner.Exception is thrown by the TextScanner interface classes when things are amiss.
   *
   * @author Hypersoft-Systems: USA
   */
  public static class SyntaxError extends RuntimeException {
    /**
     * Serialization ID
     */
    private static final long serialVersionUID = 0;

    /**
     * Constructs a TextScanner.Exception with an explanatory message.
     *
     * @param message Detail about the reason for the exception.
     */
    public SyntaxError(final String message) {
      super(message);
    }

    /**
     * Constructs a TextScanner.Exception with an explanatory message and cause.
     *
     * @param message Detail about the reason for the exception.
     * @param cause   The cause.
     */
    public SyntaxError(final String message, final Throwable cause) {
      super(message, cause);
    }

  }
}
