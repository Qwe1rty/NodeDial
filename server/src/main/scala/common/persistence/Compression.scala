package common.persistence

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import scala.util.Try


trait Compression {

  /**
   * Compress bytes via gzip
   *
   * @param value bytes to compress to gzip
   * @return compressed bytes
   */
  def compressBytes(value: Array[Byte]): Try[Array[Byte]] = Try {
    val bytesOutputStream = new ByteArrayOutputStream()
    val gzipOutputStream = new GZIPOutputStream(bytesOutputStream)
    gzipOutputStream.write(value)
    gzipOutputStream.close()
    bytesOutputStream.toByteArray
  }

  /**
   * Decompresses bytes via gzip
   *
   * @param gzip gzipped bytes to decompress
   * @return decompressed bytes
   */
  def decompressBytes(gzip: Array[Byte]): Try[Array[Byte]] = Try {
    val gzipInputStream: InputStream = new GZIPInputStream(new ByteArrayInputStream(gzip))
    val value = LazyList.continually(gzipInputStream.read).takeWhile(_ != -1).map(_.toByte).toArray
    gzipInputStream.close()
    value
  }
}
