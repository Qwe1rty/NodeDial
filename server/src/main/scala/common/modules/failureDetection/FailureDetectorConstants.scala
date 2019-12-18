package common.modules.failureDetection

import scala.concurrent.duration.{Duration, _}


private[membership] object FailureDetectorConstants {

  val SUSPICION_DEADLINE: Duration = 20.second
  val DEATH_DEADLINE: Duration = 45.second

  val DIRECT_CONNECTIONS_LIMIT: Int = 5
  val FOLLOWUP_TEAM_SIZE: Int = 3
}
