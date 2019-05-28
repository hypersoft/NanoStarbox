package box.star.text.basic;

import box.star.contract.NotNull;
import box.star.text.FormatException;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

import static box.star.text.basic.Parser.Status.*;

/**
 * <p>Provides an interface to parse text with a {@link Scanner}</p>
 * <br>
 * <p>A parser implementation is a subclass of this class. In addition to
 * handling the scanner, a parser implementation can store properties in it's
 * own fields and create discrete (pure) object types.</p>
 * <br>
 * <p>This class and it's subclasses can be used to execute any parser
 * implementation through it's {@link #parse(Class, Scanner) static parse}
 * method. Each subclass also has an {@link #parse(Class) instance parse} method, which uses the
 * parser's {@link #scanner} to create and start a new parser as an inline scanner pipeline task.</p>
 * <br>
 * <p>
 *   In a typical parser implementation, there is the notion of an elliptical
 *   list, which is a repeating sequence of inputs, following a particular type
 *   of pattern. To support that, this parser interface provides the
 *   {@link List} class, which can be used to store a list of parser instances
 *   as a list of parsed results.
 * </p>
 * <br>
 * <p>Additionally this class provides a call based integrated {@link SyntaxError}
 * class inheriting from {@link RuntimeException}, which enables uniform and exact
 * syntax error reporting for all parser implementations. As a compliment
 * <i>of</i> and testament <i>to</i> the <i>isometric design</i> of this
 * <i>parser system</i>, the class features automatic syntax error recovery for
 * the scanner, which allows syntax errors to be caught, and safely ignored.</p>
 *
 * @see Scanner
 */
public class Parser {

  protected final static String
      PARSER_DID_NOT_SYNC = ": parser did not synchronize its end result with the scanner state",
      PARSER_DID_NOT_FINISH = ": parser must call finish before it exits",
      PARSER_ALREADY_FINISHED = ": parsing already finished",
      PARSER_QA_BUG = " (parser quality assurance bug)",
      PARSER_CODE_QUALITY_BUG = " (code optimization bug)"
  ;

  // Public Static Class Provisions
  public static class
    List<T extends Parser> extends ArrayList<T> {}

  public static enum
    Status {OK, FAILED}

  // Protected Properties
  protected Status status;
  protected Scanner scanner;

  // Private Properties
  private long start, end;
  private boolean finished;
  private Bookmark origin;

  // Public Properties
  final public boolean successful(){return status.equals(OK);}
  final public boolean isFinished() { return finished; }
  final public long getStart() { return start; }
  final public long getEnd() { return end; }
  final public @NotNull Bookmark getOrigin() { return origin; }
  final public long length(){
    long start = Math.max(0, this.start);
    return (end==0)?scanner.getIndex()-start:end-start;
  }

  // Protected Static Class Provisions
  /**
   * This Interface allows a Parser to specify that upon successful
   * completion the scanner history should be synchronized (flushed) with the
   * current position.
   */
  protected static interface NewFuturePromise {}

  // Public Constructors
  public Parser(@NotNull Scanner scanner){
    if (scanner.endOfSource()){ status = FAILED; return; }
    this.scanner = scanner;
    this.origin = scanner.nextBookmark();
    start = origin.index - 1;
    status = OK;
  }

  // Protected Methods
  final protected @NotNull Bookmark cancel() {
    Bookmark bookmark = scanner.createBookmark();
    scanner.walkBack(this.end = this.start);
    this.status = FAILED;
    return bookmark;
  }

  /**
   * @return true if end is not zero
   */
  final protected boolean hasEnding(){
    return end != 0;
  }
  /**
   * @return true if the current scanner position is NOT synchronized with this record's ending
   */
  final protected boolean isNotSynchronized(){
    return ((scanner.endOfSource()?-1:0))+scanner.getIndex() != this.end;
  }

  /**
   * A parser must call finish before it exits {@link #start()}
   */
  final protected void finish(){
    if (finished) throw new RuntimeException(Parser.class.getName()+PARSER_CODE_QUALITY_BUG, new IllegalStateException(this.getClass().getName()+PARSER_ALREADY_FINISHED));
    this.end = scanner.getIndex();
    this.finished = true;
  }

  protected void start(){finish();};

  // Public Static Methods

  final public static <T extends Parser> @NotNull T parse(@NotNull Class<T> parserSubclass, @NotNull Scanner scanner) throws IllegalStateException {
    T parser;
    try {
      Constructor<T> classConstructor = parserSubclass.getConstructor(Scanner.class);
      classConstructor.setAccessible(true);
      parser = classConstructor.newInstance(scanner);
    } catch (Exception e){throw new RuntimeException(Parser.class.getName()+PARSER_CODE_QUALITY_BUG, e);}
    if (parser.successful()) {
      try { parser.start(); }
      catch (FormatException fromScannerOrParser) {throw new SyntaxError(parser, fromScannerOrParser.getMessage());}
      if (! parser.isFinished())
        throw new RuntimeException(Parser.class.getName()+PARSER_QA_BUG, new IllegalStateException(parserSubclass.getName()+PARSER_DID_NOT_FINISH));
      else if (parser.isNotSynchronized())
        throw new RuntimeException(Parser.class.getName()+PARSER_QA_BUG, new IllegalStateException(parserSubclass.getName()+PARSER_DID_NOT_SYNC));
      if (parser instanceof NewFuturePromise) scanner.flushHistory();
    }
    return parser;
  }

  // Protected Method (located here for mirroring with static method above)

  protected <T extends Parser> @NotNull T parse(@NotNull Class<T> parserSubclass){
    T parser;
    try {
      Constructor<T> classConstructor = parserSubclass.getConstructor(Scanner.class);
      classConstructor.setAccessible(true);
      parser = classConstructor.newInstance(scanner);
    } catch (Exception e){throw new RuntimeException(this.getClass().getName()+PARSER_CODE_QUALITY_BUG, e);}
    if (parser.successful()) {
      try { parser.start(); }
      catch (FormatException fromScannerOrParser) {throw new SyntaxError(parser, fromScannerOrParser.getMessage());}
      if (! parser.isFinished())
        throw new RuntimeException(this.getClass().getName()+PARSER_QA_BUG, new IllegalStateException(parserSubclass.getName()+PARSER_DID_NOT_FINISH));
      else if (parser.isNotSynchronized())
        throw new RuntimeException(this.getClass().getName()+PARSER_QA_BUG, new IllegalStateException(parserSubclass.getName()+PARSER_DID_NOT_SYNC));
      if (parser instanceof NewFuturePromise) scanner.flushHistory();
    }
    return parser;
  }

  public static class SyntaxError extends RuntimeException {
    protected Parser parser;
    private String tag(){
      return parser.getClass().getName()+".SyntaxError: ";
    }
    @Override
    public String toString() {
      return tag() + super.getMessage();
    }
    public SyntaxError(Parser parser, String message) {
      super("\n\n"+message+":\n\n   "+parser.cancel()+"\n");
      this.parser = parser;
    }
    public SyntaxError(Parser parser, String message, Throwable cause) {
      super("\n\n"+message+":\n\n   "+parser.cancel()+"\n", cause);
      this.parser = parser;
    }
  }

}