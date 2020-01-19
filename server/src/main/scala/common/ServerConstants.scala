package common

import better.files.File


object ServerConstants {

  val BASE_DIRECTORY: File = File.root/"var"/"chordial"

  val REQUIRED_TRIGGERS: Int = 2
}
