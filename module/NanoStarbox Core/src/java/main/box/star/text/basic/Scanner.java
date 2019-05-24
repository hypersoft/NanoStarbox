package box.star.text.basic;

import box.star.Tools;
import box.star.contract.NotNull;
import box.star.contract.Nullable;
import box.star.io.Streams;
import box.star.text.Char;
import box.star.text.Exception;
import box.star.text.SyntaxError;

import java.io.*;
import java.util.Hashtable;
import java.util.Map;

import static box.star.text.Char.*;

/**
 * <h2>Basic Text Scanner</h2>
 * <p>Provides the basic facilities to scan text formats with precision text format
 * error-reporting and sub-processing capabilities. The scanner also supports
 * custom character-to-string translation so that each scanner may provide
 * standardized: `hard to define character error disambiguation` to its users.
 * In addition, the scanner supports overridable backslash escape expansion with
 * customizable fallback support, for the default implementation.</p><br>
 *
 * <p>The scanner automatically tracks backslash escape activation, and in some
 * built-in scanner methods, automatically handles backslash expansion and output
 * string insertion. However the scanner does not usually provide such facilities,
 * as the meaning of "character escape sequence" is often text format dependent.
 * The design methodology of these features is passive, which allows for them to
 * be safely ignored by any custom implementation.</p><br>
 *
 * <p>The default implementation understands escaped
 * and not escaped, according to the status of the previous character. This could cause
 * logical errors in a text-stream that has not been correctly back-stepped (to the beginning
 * of an escape sequence), but most implementations which call upon expansion routines,
 * will never need back-step support within that context.</p>
 *
 * <br>
 * <p>Quick Overview</p>
 * <ul>
 * <li>Foreign Batch Operation Method interface through {@link #run(ScannerMethod, Object...)}, and {@link #branch(ScannerMethod, Object...)}</li>
 * <li>Case Controlled Syntax Character Match Mandate through {@link #nextCharacter(char, boolean)}</li>
 * <li>Case Controlled Syntax Keyword Match Mandate through {@link #nextString(String, boolean)}</li>
 * <li>Character Map Searching through {@link #nextMap(char...)} and {@link #nextMapLength(int, char...)}</li>
 * <li>Character Map Field Boundary Searching through {@link #nextField(char...)}, {@link #nextFieldLength(int, char...)} and {@link #nextBoundField(char...)}</li>
 * <li>Buffer Position Backstep through {@link #back()}, and {@link #walkBack(long)}</li>
 * <li>Integral Back Step Buffer Control Method through {@link #flushHistory()}</li>
 * </ul>
 * <br>
 * <tt>Basic Text Scanner (c) 2019 Hypersoft-Systems: USA (Triston-Jerard: Taylor)</tt>
 * <p></p>
 */
public class Scanner implements Closeable {

  private Map<Character, String> TRANSLATION = new Hashtable<>();

  /**
   * <p>Call this method to configure the translation for a particular character</p>
   * @param c the character to map
   * @param translation the string to use when displaying this character to a user
   * @return the translation given
   */
  public String mapCharacterTranslation(char c, String translation) {
    TRANSLATION.put(c, translation);
    return translation;
  }

  /**
   * <p>Call this method to get a user display capable version of any character,
   * according to this scanner's internal configuration, or the static global configuration
   * of the box.star.Char class</p>
   * @param c the character to translate
   * @return the translation of the character
   */
  public String translateCharacter(char c) {
    if (c == 0) return "null";
    if (TRANSLATION.containsKey(c)) return TRANSLATION.get(c);
    else return Tools.switchNull(Char.translate(c), String.valueOf(c));
  }

  private static final CharacterExpander defaultCharacterExpander = new CharacterExpander() {
    @Override
    public String expand(Scanner scanner, char c) {
      return Char.toString(c);
    }
  };

  /**
   * <p>A custom character expander.</p>
   * <br>
   * <p>If this character expander returns null or is null, the default
   * implementation will be used.</p>
   * <p></p>
   */
  public CharacterExpander characterExpander = null;

  /**
   * The character expander that will resolve backslash escapes, if the current
   * configuration does not handle the escape.
   */
  public CharacterExpander fallBackCharacterExpander = defaultCharacterExpander;

  /**
   * Reader for the input.
   */
  protected Reader reader;
  protected boolean closeable;
  protected ScannerState state;

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

  /**
   * Sometimes you need to parse some sub-text, so here is a method to set
   * the location data, after you create the scanner.
   *
   * @param line
   * @param column
   * @param index
   * @return the new Scanner
   * @throws IllegalStateException if this is not a new {@link Scanner}
   */
  public Scanner At(long line, long column, long index) throws IllegalStateException {
    // since this is a profile-method, raise hell if state is
    // in post-initialization status.
    if (state.index != -1) {
      throw new IllegalStateException("Scanner has already been initialized");
    }
    state.line = line;
    state.column = column;
    state.index = index;
    return this;
  }

  /**
   * This method enables lines to be escaped by the state, and probably should
   * not be used for any reason.
   *
   * @param escapeLines
   */
  @Deprecated
  public void setLineEscape(boolean escapeLines) {
    state.escapeLines = escapeLines;
  }

  /**
   * This method enables lines to be escaped by the state, and probably should
   * not be used for any reason.
   *
   * @param escapeLines
   * @param useUnderscore
   */
  @Deprecated
  public void setLineEscape(boolean escapeLines, boolean useUnderscore) {
    state.escapeLines = escapeLines;
    state.escapeUnderscoreLine = useUnderscore;
  }

  /**
   * Determines the size of the current history buffer.
   *
   * @return
   */
  public int getHistoryLength() {
    return state.getHistoryLength();
  }

  /**
   * Trims the size of the history buffer to the amount given.
   * <p>
   * if the amount is zero or less, the history is flushed.
   * if the amount is not reached, nothing is done.
   *
   * @param size
   * @throws IllegalStateException if the current position is within the history.
   */
  public void trimHistoryLength(int size) throws IllegalStateException {
    state.trimHistoryLength(size);
  }

  /**
   * Call this after each successful main loop to clear the back-buffer.
   * <p>
   * {@link #haveNext()} loads the next position into history.
   */
  public void flushHistory() {
    state.clearHistory();
  }

  /**
   * <p>call this to close the reader.</p>
   * <br>
   * <p>If using a string source, it's okay to let this happen during finalize.</p>
   */
  @Override
  public void close() {
    try /*  ignoring exceptions with final closure */ {
      if (closeable) reader.close();
    }
    catch (IOException ignored) {}
    finally /*  complete */ {
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
    flushHistory();
    super.finalize();
  }

  /**
   * @return true if this scanner already has a state lock.
   */
  @Deprecated  public boolean hasStateRecordLock() {
    return state.locked;
  }

  /**
   * <p>Like {@link #flagThisCharacterSyntaxError}, but does the error checking beforehand.</p>
   * <br>
   *   <p>The current stream position is maintained if an error does not occur.</p>
   * @param message the content type expected by the driver (caller)
   * @param map the list of characters to match the current character with
   * @throws SyntaxError if the current character is not found within the given map
   */
  public void flagNextCharacterSyntaxError(String message, char... map) throws SyntaxError {
    char c = next();
    if (! Char.mapContains(c, map))
      throw this.syntaxError("Expected " + message + " and located `" + translateCharacter(c) + "'");
    back();
  }

  /**
   * <p>Raises a syntax error with the specified message on the current character position.</p>
   * @param message the content type expected by the driver (caller)
   * @throws SyntaxError representing this character at this position with this expected content message
   */
  public void flagThisCharacterSyntaxError(String message) throws SyntaxError {
    this.syntaxError("Expected " + message + " and located `" + translateCharacter(state.current()) + "'");
  }

  /**
   * Obtains a state lock, which can reset the reader and state if needed.
   *
   * @return a new state lock if the state is not already locked.
   */
  @NotNull
  @Deprecated public ScannerStateRecord getStateLock() {
    return new ScannerStateRecord(this);
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
        if (c <= 0) {
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
        if (c <= 0) {
          state.eof = true;
          return 0;
        }
        state.recordCharacter(Char.valueOf(c));
        return Char.valueOf(c);
      }
      catch (IOException exception) { throw new Exception(exception); }
    }
  }

  /**
   * @param character
   * @param caseSensitive
   * @return
   */
  public char nextCharacter(char character, boolean caseSensitive) {
    char c = next();
    if (c == 0)
      throw syntaxError("Expected " + translateCharacter(character) + " and located end of text stream");
    if (!caseSensitive) {
      c = Char.toLowerCase(c);
      character = Char.toLowerCase(character);
    }
    if (character != c)
      throw this.syntaxError("Expected " + translateCharacter(character) + " and located `" + translateCharacter(c) + "'");
    return c;
  }

  /**
   * @return the white-space-scanned
   */
  public String scanAllWhiteSpace(){
    return nextMap(Char.MAP_ASCII_ALL_WHITE_SPACE);
  }

  /**
   * @return all white-space characters which do not escape lines
   */
  public String scanLineWhiteSpace(){
    return nextMap(MAP_ASCII_LINE_WHITE_SPACE);
  }

  /**
   * <p>Returns the scanner to the specified position. This position must be within
   * the buffer history. No validation is performed.</p>
   * @param to
   */
  public void walkBack(long to){ while (to != getIndex()) back(); }

  /**
   * <p>Tries to silently fetch the requested sequence match, from the beginning. if it fails
   * it returns a zero-length-string. this allows iteration through known compounds that fit in
   * certain contexts, and pass-through-[maybe-not]-present-value. failure to resolve 1
   * of a set should throw a syntax error, citing the semantic documentation
   * language for the composition set.</p>
   * @param sequence
   * @param caseSensitive
   * @return
   */
  public String nextOptionalSequence(String sequence, boolean caseSensitive){
    long start = getIndex();
    String test;
    String match = test = nextOptionalLength(sequence.length());
    if (!caseSensitive) {
      test = match.toLowerCase();
      sequence = sequence.toLowerCase();
    }
    if (! sequence.equals(test)) {
      walkBack(start); return "";
    }
    return match;
  }

  /**
   * @param character
   * @param caseSensitive
   * @return
   */
  public char nextCharacter(String label, char character, boolean caseSensitive) {
    char c = next();
    if (c == 0)
      throw syntaxError("Expected " + label + " and located end of text stream");
    if (!caseSensitive) {
      c = Char.toLowerCase(c);
      character = Char.toLowerCase(character);
    }
    if (character != c)
      throw this.syntaxError("Expected " + label + " and located `" + translateCharacter(c) + "'");
    return c;
  }

  /**
   * Match the next string input with a source string.
   *
   * @param seek
   * @param caseSensitive
   * @return
   * @throws SyntaxError if match fails
   */
  @NotNull
  public String nextString(@NotNull String seek, boolean caseSensitive) throws SyntaxError {
    StringBuilder out = new StringBuilder();
    char[] sequence = seek.toCharArray();
    for (char c : sequence) out.append(nextCharacter(seek, c, caseSensitive));
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
   * <p>A rendition of {@link #nextField(char...)} that searches for a character sequence
   * with case sensitivity and optional backslash detection.</p>
   * <br>
   * <p>The found part is discarded from the buffer, and the stream is not rewound;
   * unlike the character variants of *Field which put the character back into the buffer.
   * These behaviors follow the logic that:</p>
   * <ol>
   *   <li>keywords are consumed by the caller</li>
   *   <li>symbols are terminals rather than input</li>
   * </ol>
   *
   * @param sequence a string to find at the end of the input buffer.
   * @param caseSensitive true if the search should be case sensitive (exact)
   * @param detectEscape handle backslash escapes on the head of this sequence (any found escape within sequence, will break the match regardless of this setting)
   * @return all the text up to but not including sequence
   * @throws SyntaxError if not found
   */
  @NotNull
  public String nextSequence(String sequence, boolean caseSensitive, boolean detectEscape) throws SyntaxError {
    int sourceLength = sequence.length(), bl = 0, matchIndex = 0;
    if (sourceLength == 0) return "";
    char[] search = ((caseSensitive)?sequence:sequence.toLowerCase()).toCharArray();
    StringBuilder sb = new StringBuilder();
    do {
      char c = this.next(); // step
      ++bl; // count buffer length
      if (c == 0) // die
        throw syntaxError("Expected `" + sequence + "' and found end of text stream");
      sb.append(c); // add to collection
      char find = (caseSensitive?c:Char.toLowerCase(c)); // transliterate if needed
      if (find == search[matchIndex]) { // got a match at stream, and search-index
        if (detectEscape && matchIndex == 0 && escapeMode()){ // got a escapable sequence-head-match
          /* doing this is: escaping */
        } else matchIndex++; // go to next char
      } else matchIndex = 0; // no match, go back to start
    } while (matchIndex != sourceLength);
    // if we got here, then a match occurred because no error was thrown during seek.
    return sb.substring(0, bl - sourceLength); // chop off the ending, returning what we scanned.
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
      if (Char.mapContains(c, map)) {
        this.back();
        break;
      }
      sb.append(c);
    } while (c != 0);
    return sb.toString();
  }

  /**
   * Performs all right-hand-side-backslash operations.
   * <p>
   * (for this: right-hand-side = "everything after")
   * </p>
   *
   * @param character
   * @return
   */
  @NotNull public String expand(char character) {
    if (characterExpander != null) {
      String expansion = characterExpander.expand(this, character);
      if (expansion != null) return expansion;
    }
    switch (character) {
      case 'd':
        return DELETE + Tools.EMPTY_STRING;
      case 'e':
        return ESCAPE + Tools.EMPTY_STRING;
      case 't':
        return "\t";
      case 'b':
        return "\b";
      case 'v':
        return VERTICAL_TAB + Tools.EMPTY_STRING;
      case 'r':
        return "\r";
      case 'n':
        return "\n";
      case 'f':
        return "\f";
      /*unicode*/
      case 'u': {
        try { return String.valueOf((char) Integer.parseInt(this.nextMapLength(4, MAP_ASCII_HEX), 16)); }
        catch (NumberFormatException e) { throw this.syntaxError("Illegal escape", e); }
      }
      /*hex or octal*/
      case '0': {
        char c = this.next();
        if (c == 'x') {
          try { return String.valueOf((char) Integer.parseInt(this.nextMapLength(4, MAP_ASCII_HEX), 16)); }
          catch (NumberFormatException e) { throw this.syntaxError("Illegal escape", e); }
        } else {
          this.back();
        }
        String chars = '0' + this.nextMapLength(3, MAP_ASCII_OCTAL);
        int value = Integer.parseInt(chars, 8);
        if (value > 255) {
          throw this.syntaxError("octal escape subscript out of range; expected 00-0377; have: " + value);
        }
        char out = (char) value;
        return out + Tools.EMPTY_STRING;
      }
      /*integer or pass-through */
      default: {
        if (mapContains(character, MAP_ASCII_NUMBERS)) {
          String chars = character + this.nextMapLength(2, MAP_ASCII_NUMBERS);
          int value = Integer.parseInt(chars);
          if (value > 255) {
            throw this.syntaxError("integer escape subscript out of range; expected 0-255; have: " + value);
          } else {
            char out = (char) value;
            return out + Tools.EMPTY_STRING;
          }
        } else return fallBackCharacterExpander.expand(this, character);
      }
    }
  }

  /**
   * <p>Scan and assemble characters while scan is not in map, expanding escape
   * sequences, and ignoring escaped characters in map.</p>
   * <br>
   * <p>If eof is encountered, it is considered as the field boundary.</p>
   *
   * @param map
   * @return
   * @throws SyntaxError if trying to escape end of stream.
   */
  @NotNull
  public String nextBoundField(@NotNull char... map) throws SyntaxError {

    StringBuilder sb = new StringBuilder();

    while (haveNext()) {

      char c = next();

      if (c == BACKSLASH && !escapeMode()) continue;

      if (c == 0) {
        if (escapeMode())
          throw syntaxError("expected character escape sequence, found end of stream");
        return sb.toString();
      }

      if (escapeMode()) {
        String swap = expand(c);
        sb.append(swap);
        continue;
      }

      if (Char.mapContains(c, map)) {
        this.back();
        break;
      }

      sb.append(c);

    }
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
   * Tries to get up to n characters from the stream.
   * @param n the size of the string request
   * @return an empty string (n<=0), all the characters requested, or a truncated buffer (eof = true), whichever comes first
   */
  public String nextOptionalLength(int n){
    if (n <= 0) {
      return Tools.EMPTY_STRING;
    }
    char[] chars = new char[n];
    int pos = 0;
    while (pos < n) {
      chars[pos] = this.next();
      if (this.endOfSource()) {
        break;
      }
      pos += 1;
    }
    return new String(chars);
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
      return Tools.EMPTY_STRING;
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

  /**
   * <h2>Run</h2>
   * <p>Starts a {@link ScannerMethod}.</p>
   * <br>
   * <p>Creates a copy of the method, and calls its
   * {@link ScannerMethod#start(Scanner, Object[])} method with the given
   * parameters.</p>
   *
   * @param method
   * @param parameters
   * @return
   */
  @NotNull
  final public String run(ScannerMethod method, Object... parameters) {
    method = method.clone();
    method.start(this, parameters);
    do {
      char c = next();
      method.collect(this, c);
      if (method.terminate(this, c)) break;
    } while (method.scan(this));
    return method.compile(this);
  }

  /**
   * <p>Transfer</p>
   * <br>
   * <p>Transfers the current scanner position and character to a new scanner.</p>
   *
   * @param method the method to use
   * @param parameters the parameters for the method
   * @return
   */
  @NotNull
  final public String branch(ScannerMethod method, Object... parameters) {
    method = method.clone();
    method.start(this, parameters);
    method.collect(this, state.current());
    if (! method.terminate(this, state.current()) && method.scan(this))
    do {
      char c = next();
      method.collect(this, c);
      if (method.terminate(this, c)) break;
    } while (method.scan(this));
    return method.compile(this);
  }

  /**
   * <p>Call this to determine if the current character should be escaped.</p>
   * <br>
   * <p>if the sequence is \ then backslash mode = true;</p>
   * <p>if the sequence is \\ then backslash mode = false (and escape mode = true).</p>
   *
   * @return true if the current state is in backslash mode.
   */
  public boolean backSlashMode() {
    return state.slashing;
  }

  /**
   * @return true if the current state is in escape mode for the current character.
   */
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
    return new SyntaxError(message +":\n\n   "+this.claim());
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
    return new SyntaxError(message + ":\n\n   " +this.claim(), causedBy);
  }

  /**
   * <p>Gets a future claim for the next character.</p>
   * <br>
   *   <p>Some scanner tasks may need a way to record where an external procedure
   *   will be starting within the scanner stream. This method provides that
   *   functionality, with a simple next/claim/back step routine, returning
   *   a capture of the claim.</p>
   *   <br>
   * @return the future claim
   */
  public String nextCharacterClaim(){
    String claim;
    next();
    claim = claim();
    back();
    return claim;
  }

  public String claim() {
    return " at location = " + "{line: " + getLine() + ", column: " + getColumn() + ", index: " + getIndex() + ", source: '" + getPath() + "'}";
  }

  public String toString() {
    return claim();
  }

  /**
   * @return the current scanner character
   */
  public char current(){
    return state.current();
  }

  /**
   * @return the scanner's connotation of where this stream-data originates
   */
  public String getPath() {
    return state.path;
  }

  /**
   * @return the position within the text-stream
   */
  public long getIndex() {
    return state.index;
  }

  /**
   * @return the line number within the text-stream
   */
  public long getLine() {
    return state.line;
  }

  /**
   * @return the column number at the current line within the text-stream
   */
  public long getColumn() {
    return state.column;
  }

  /**
   * <p>Requires that the next character explicitly match the specified character.</p>
   * @param character character to match
   * @return the character matched
   * @throws SyntaxError if the character in the stream does not match the character specified.
   */
  public char nextCharacter(char character) throws SyntaxError {
    char c = next();
    if (c == 0 && character != 0)
      throw syntaxError("Expected " + translateCharacter(character) + " and found end of text stream");
    if (character != c)
      throw this.syntaxError("Expected " + translateCharacter(character) + " and found " + translateCharacter(c));
    return c;
  }

}
