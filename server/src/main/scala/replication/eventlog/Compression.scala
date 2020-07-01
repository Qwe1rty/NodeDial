package replication.eventlog

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import scala.util.Try


trait Compression {

  /**
   * Compress bytes via gzip
   *
   * @param bytes bytes to compress to gzip
   * @return compressed bytes
   */
  def compress(bytes: Array[Byte]): Try[Array[Byte]] = Try {
    val bytesOutputStream = new ByteArrayOutputStream()
    val gzip = new GZIPOutputStream(bytesOutputStream)
    gzip.write(bytes)
    gzip.close()
    bytesOutputStream.toByteArray
  }

  /**
   * Decompresses bytes via gzip
   *
   * @param bytes gzipped bytes to decompress
   * @return decompressed bytes
   */
  def decompress(bytes: Array[Byte]): Try[Array[Byte]] = Try {
    val bytesInputStream: InputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))
    val raw = bytesInputStream.readAllBytes()
    bytesInputStream.close()
    raw
  }

}
