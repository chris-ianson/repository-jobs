/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.repositoryjobs

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import org.joda.time.Duration
import play.Logger
import play.libs.Akka
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}
import uk.gov.hmrc.play.http.HttpGet

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

sealed trait JobResult
case class Error(message: String, ex : Throwable) extends JobResult {
  Logger.error(message, ex)
}
case class Warn(message: String) extends JobResult {
  Logger.warn(message)
}
case class Info(message: String) extends JobResult {
  Logger.info(message)
}

trait DefaultSchedulerDependencies extends MongoDbConnection with JenkinsConnector {
  import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig._


  def repositoryJobsService:RepositoryJobsService

  val akkaSystem = Akka.system()

  override def jenkinsBaseUrl: String = jobsApiBase
  override def http: HttpGet = WSHttp

}

private[repositoryjobs] abstract class Scheduler extends LockKeeper with DefaultMetricsRegistry {
  self: MongoDbConnection  =>

  def akkaSystem: ActorSystem
  def repositoryJobsService: RepositoryJobsService

  override def lockId: String = "repository-jobs-scheduled-job"

  override def repo: LockRepository = LockMongoRepository(db)

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(15)

  def startUpdatingJobsModel(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update every $interval")

    akkaSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      updateRepositoryJobsModel
    }
  }


  private def updateRepositoryJobsModel: Future[JobResult] = {
    tryLock {
      Logger.info(s"Starting mongo update")

      repositoryJobsService.update.map { result =>
        val total = result.toList.length
        val failureCount = result.count(r => !r)
        val successCount = total - failureCount

        defaultMetricsRegistry.counter("scheduler.success").inc(successCount)
        defaultMetricsRegistry.counter("scheduler.failure").inc(failureCount)

        Info(s"Added $successCount and encountered $failureCount failures")
      }.recover { case ex =>
        Error(s"Something went wrong during the mongo update:", ex)
      }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }
  }

}

object Scheduler extends Scheduler with DefaultSchedulerDependencies {

  override val repositoryJobsService = new RepositoryJobsService(new BuildsMongoRepository(db), this)

}
