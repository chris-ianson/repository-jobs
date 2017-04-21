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

import play.api.Logger
import play.api.libs.json.{JsError, JsResult, JsSuccess, Json}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class BuildResponse(description: Option[String], duration: Option[Int], id: Option[String], number: Option[Int], result: Option[String],
                         timestamp: Option[Long], url: Option[String], builtOn: Option[String])

case class UserRemoteConfig(url: Option[String])
case class Scm(userRemoteConfigs: Option[Seq[UserRemoteConfig]])
case class Job(name: Option[String], url: Option[String], allBuilds: Option[Seq[BuildResponse]], scm: Option[Scm])
case class JenkinsJobsResponse(jobs: Seq[Job])

trait JenkinsConnector {

  def http: HttpGet

  def jenkinsBaseUrl: String

  implicit val buildReads = Json.format[BuildResponse]
  implicit val userRemoteConfigReads = Json.format[UserRemoteConfig]
  implicit val scmReads = Json.format[Scm]
  implicit val jobReads = Json.format[Job]
  implicit val jenkinsReads = Json.format[JenkinsJobsResponse]

  val buildsUrl = "/api/json?tree=" + java.net.URLEncoder.encode("jobs[name,url,allBuilds[id,description,duration,number,result,timestamp,url,builtOn],scm[userRemoteConfigs[url]]]", "UTF-8")

  def getBuilds: Future[JenkinsJobsResponse] = {
    implicit val hc = new HeaderCarrier()

    val url = jenkinsBaseUrl + buildsUrl

    //!@TODO test the ctrl character removal
    val result = http.GET[HttpResponse](url).recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
        throw ex
    }.map(httpResponse => Try(Json.parse(httpResponse.body.replaceAll("\\p{Cntrl}", "")).validate[JenkinsJobsResponse]) match {
      case Success(jsResult) => jsResult
      case Failure(t) => JsError(t.getMessage)
    })

    result.map {
      case q: JsSuccess[JenkinsJobsResponse] => //println(Json.prettyPrint(Json.toJson(q.get)))
        q.get
      case JsError(e) =>
        throw new RuntimeException(s"${e.toString()}")
    }
  }



}