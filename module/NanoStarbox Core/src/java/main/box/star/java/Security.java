package box.star.java;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;

final class Security extends java.lang.SecurityManager {

  private static SecurityManager defaultManager = System.getSecurityManager();
  private static Security manager;

  private Security() {}

  public static boolean install() {
    if (manager != null) return true;
    manager = new Security();
    System.setSecurityManager(manager);
    return true;
  }

  public static SecurityManager getManager() {
    if (manager == null) return defaultManager;
    return manager;
  }

  @Override
  public void checkPermission(Permission perm) {
  }

  @Override
  public void checkPermission(Permission perm, Object context) {
  }

  @Override
  public void checkCreateClassLoader() {
  }

  @Override
  public void checkAccess(Thread t) {
  }

  @Override
  public void checkAccess(ThreadGroup g) {
  }

  @Override
  public void checkExit(int status) {
  }

  @Override
  public void checkExec(String cmd) {
  }

  @Override
  public void checkLink(String lib) {
  }

  @Override
  public void checkRead(FileDescriptor fd) {
  }

  @Override
  public void checkRead(String file) {
  }

  @Override
  public void checkRead(String file, Object context) {
  }

  @Override
  public void checkWrite(FileDescriptor fd) {
  }

  @Override
  public void checkWrite(String file) {
  }

  @Override
  public void checkDelete(String file) {
  }

  @Override
  public void checkConnect(String host, int port) {
  }

  @Override
  public void checkConnect(String host, int port, Object context) {
  }

  @Override
  public void checkListen(int port) {
  }

  @Override
  public void checkAccept(String host, int port) {
  }

  @Override
  public void checkMulticast(InetAddress maddr) {
  }

  @Override
  public void checkPropertiesAccess() {
  }

  @Override
  public void checkPropertyAccess(String key) {
  }

  @Override
  public void checkPrintJobAccess() {
  }

  @Override
  public void checkPackageAccess(String pkg) {
  }

  @Override
  public void checkPackageDefinition(String pkg) {
  }

  @Override
  public void checkSetFactory() {
  }

  @Override
  public void checkSecurityAccess(String target) {
  }

}
