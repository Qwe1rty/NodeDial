package schema

import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory


object LoggingConfiguration {

  /**
   * Sets logs from certain packages to the specified level. Useful for cutting down on
   * more granular logs from certain sources without losing logs from the rest of the entire
   * program
   *
   * @param level log level
   * @param packages list of packages to set
   */
  def setPackageLevel(level: Level, packages: String*): Unit = packages.foreach {
    LoggerFactory
      .getLogger(_)
      .asInstanceOf[Logger]
      .setLevel(level)
  }

}
