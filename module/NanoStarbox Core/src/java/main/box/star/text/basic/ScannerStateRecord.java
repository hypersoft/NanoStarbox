package box.star.text.basic;

import box.star.contract.NotNull;

import java.io.IOException;

public class ScannerStateRecord {

  protected Scanner main;
  protected ScannerState backupState;

  protected ScannerStateRecord(@NotNull Scanner main) {
    if (main.hasStateRecordLock())
      throw new Scanner.Exception("cannot acquire scanner lock",
          new IllegalStateException("state lock acquired"));
    if (!main.haveNext()) {
      throw new Scanner.Exception("cannot acquire scanner lock",
          new IllegalStateException("end of source data"));
    }
    this.main = main;
    this.backupState = main.state.clone();
    try {
      main.reader.mark(1000000);
    }
    catch (IOException e) {
      throw new Scanner.Exception("failed to configure source reader", e);
    }
    main.state.locked = true;
  }

  public void restore() throws Scanner.Exception {
    if (main == null) return;
    try {
      try {
        main.reader.reset();
        main.state = backupState;
      }
      catch (IOException e) {
        throw new Scanner.Exception("failed to restore backup state", e);
      }
    }
    finally { free(); }
  }

  public void free() {
    if (main == null) return;
    try {
      try { main.reader.mark(1);}
      catch (IOException ignore) {}
    }
    finally {
      this.main.state.locked = false;
      this.main = null;
      this.backupState = null;
    }
  }
}
