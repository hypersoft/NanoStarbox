package box.star.state;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public interface ObjectSerializer {
  Serializable loadStreamObject(ObjectInputStream ois);

  void saveStreamObject(Serializable o, ObjectOutputStream oos);
}
