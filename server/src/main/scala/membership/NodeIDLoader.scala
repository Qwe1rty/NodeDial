package membership

import better.files.File
import com.roundeights.hasher.Implicits._
import membership.Administration.rejoin
import schema.ImplicitGrpcConversions._


private[membership] object NodeIDLoader {

  private[membership] def apply(file: File): String = {

    if (rejoin) file.loadBytes
    else {
      val newID: String = System.nanoTime().toString.sha256

      file.createDirectoryIfNotExists()
      file.writeByteArray(newID)

      newID
    }
  }
}
