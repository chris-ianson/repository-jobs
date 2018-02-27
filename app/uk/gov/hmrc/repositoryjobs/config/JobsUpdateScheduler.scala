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

import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig
@Singleton
class JobsUpdateScheduler @Inject()(repositoryJobsConfig: RepositoryJobsConfig, scheduler: Scheduler) {

  import scala.concurrent.duration._

  if (repositoryJobsConfig.schedulerEnabled) {
    scheduler.startUpdatingJobsModel(10 minutes)
  }

}
