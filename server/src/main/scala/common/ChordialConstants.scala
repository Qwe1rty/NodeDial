package common

import better.files.File


object ChordialConstants {

  val BASE_DIRECTORY: File = File.currentWorkingDirectory/".chordial"


  // Used to determine how large a buffer of any container of nodes is
  def DEFAULT_BUFFER_CAPACITY(clusterSize: Int): Int =
    (2.5 * Math.log(clusterSize) + 0.5).toInt
}
