package administration

import better.files.File
import com.roundeights.hasher.Implicits._
import Administration.rejoin
import schema.ImplicitGrpcConversions._


private[administration] object NodeIDLoader {

  private[administration] def apply(file: File): String = {

    if (rejoin) file.loadBytes
    else {
      val newID: String = System.nanoTime().toString.sha256

      file.createDirectoryIfNotExists()
      file.writeByteArray(newID)

      newID
    }
  }
}
