package common

import better.files.File


object ChordialConstants {

  val EXTERNAL_REQUEST_PORT: Int = 8080
  val MEMBERSHIP_PORT: Int = 22200
  val PARTITION_PORT: Int = 22201
  val REPLICATION_PORT: Int = 22202


  val BASE_DIRECTORY: File = File.root/"var"/"chordial"

  val REQUIRED_TRIGGERS: Int = 2
}
