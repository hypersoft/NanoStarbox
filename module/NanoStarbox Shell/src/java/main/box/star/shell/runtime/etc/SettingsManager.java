package box.star.shell.runtime.etc;

import box.star.Runtime;
import box.star.shell.runtime.Environment;
import box.star.shell.Main;
import box.star.state.Configuration;
import box.star.state.EnumSettings;

import java.io.Serializable;

import static box.star.shell.Main.Settings.SYSTEM_PROFILE;
import static box.star.shell.Main.Settings.USER_PROFILE;

public class SettingsManager extends EnumSettings.Manager<Main.Settings, Serializable> {
  public SettingsManager(Environment environment) {
    super(SettingsManager.class.getSimpleName());
    set(SYSTEM_PROFILE, Runtime.switchNull(environment.getString(Main.SHELL_SYSTEM_PROFILE_VARIABLE), System.getProperty(Main.SHELL_SYSTEM_PROFILE_PROPERTY)));
    set(USER_PROFILE, Runtime.switchNull(environment.getString(Main.SHELL_USER_PROFILE_VARIABLE), System.getProperty(Main.SHELL_USER_PROFILE_PROPERTY)));
  }
  public SettingsManager(String name, Configuration<Main.Settings, Serializable> parent) {
    super(name, parent);
  }
}
