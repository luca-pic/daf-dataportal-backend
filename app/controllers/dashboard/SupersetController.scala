package controllers.dashboard

import ftd_api.yaml.Error
import javax.inject._
import it.gov.daf.common.sso.common.{LoginInfo, SecuredInvocationManager}
import it.gov.daf.common.utils.RequestContext
import play.api.cache.CacheApi
import play.api.{Configuration, Environment}
import play.api.mvc._
import play.api.libs.ws._
import utils.ConfigReader

import scala.concurrent.Future
import play.api.libs.json._
import play.api.inject.ConfigurationProvider
import play.api.Logger

import scala.util.Try


@Singleton
class SupersetController @Inject() ( ws: WSClient, cache: CacheApi  ,config: ConfigurationProvider, sim: SecuredInvocationManager) extends Controller {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  val conf = Configuration.load(Environment.simple())
  val URL : String = ConfigReader.getSupersetUrl
  val user = ConfigReader.getSupersetUser
  val pass = ConfigReader.getSupersetPass
  val openDataUrl = ConfigReader.getSupersetOpenDataUrl
  val openDataUser = ConfigReader.getSupersetOpenDataUser
  val openDataPwd = ConfigReader.getSupersetOpenDataPwd

  val local = ConfigReader.getLocalUrl

  private val logger = Logger(this.getClass.getName)

  def getIframes() = Action.async { implicit request =>
    RequestContext.execInContext[Future[Result]] ("getIframes") { () =>
      val iframeJson = ws.url("http://localhost:8088/slicemodelview/api/read")
        .withHeaders("Cookie" -> """Cookie:csrftoken=QmvKnmdoxhe6mmRdHj9OscISZlWi2QT9; __utma=111872281.1060986550.1484824167.1484918611.1484927618.12; Idea-e243532e=3f306772-24a2-4be2-9425-4d30ba6169c8; _ga=GA1.1.1060986550.1484824167; auth_tkt="f0cfdd1af4b673fe1c91e3a25d2ce994596f3bd0ckanadmin!userid_type=unicode"; session=.eJyFkEFqwzAQRe8ya4NkxZYlQekJClllE4qxpXEtokRGo6SE0LtXTkO7aelqhuG9_2Fu0E8JaQaT0xkr6L0DA4KPunWTlhuNiI6rZp3dMDnh2q5DqMBSmvocD3gqvK7FuLFcjK7BVtpaCm0HVbam7aS0UnDXOCF08UK0Q8DiFLGCZXjDfvaUY7qC2cOc82IYu0NzpGwUV4otmI6eyMcTsVBg9nz3tt_nl-gw7Dy-P9Ul9PeQM2F66H8yP0WXEvYfTcFbPK7VK_2AXytYm77-WMPHJ_Y7dH8.DFuvzw.hj0aw6H_DAh_R9YtVND3K6yhy1o""")
        .get
      iframeJson map { response =>
        val results = (response.json \ "result").get
        Ok(results)
      }
    }
  }


  def session() = Action.async { implicit request =>
    RequestContext.execInContext[Future[Result]] ("session") { () =>
      val data = Json.obj(
        "username" -> user,
        "password" -> pass
      )
      val responseWs: Future[WSResponse] = ws.url(URL + "/login/").post(data)
      responseWs.map { response =>
        println(response.cookie("session"))
        println(response.header("Set-Cookie"))
        val session = response.cookie("session").get.toString
        cache.set("superset." + user, session)
        Ok(session)
      }
    }
  }

  def publicSlice2(user :String) =  Action.async { implicit request =>
    RequestContext.execInContext[Future[Result]] ("publicSlice2") { () =>
      val sessionCookie = cache.get[String]("superset." + user).get
      val responseWs = ws.url(URL + "slicemodelview/api/read")
        .withHeaders("cookie" -> sessionCookie).get()
      responseWs.map { response =>
        println(response.json)

        Ok((response.json \ "result").get)
      }
    }
  }

  def publicSlice(user :String) = Action.async { implicit request =>
    RequestContext.execInContext[Future[Result]] ("publicSlice") { () =>
      case class AppInfo(appName: String, url: String, userPwd: String)
      val info = if (user == openDataUser) AppInfo("superset_open", openDataUrl, null) else AppInfo("superset", URL, null)

      Logger.logger.debug(s"call publicSlice for $user")

      def callPublicSlice(cookie: String, wsClient: WSClient) = {
        Logger.logger.debug(s"call ${info.url} for user $user")
        wsClient.url(info.url + "/slicemodelview/api/read").withHeaders("Cookie" -> cookie).get()
      }

      sim.manageServiceCall(new LoginInfo(user, info.userPwd, info.appName), callPublicSlice).map { json =>
        Ok(Try {
          (json.json \ "result").get
        }.getOrElse(JsNull))
      }
    }

  }

  def databaseIdByName(user: String, dbName: String) = Action.async { implicit request =>
    RequestContext.execInContext[Future[Result]] ("databaseIdByName") { () =>

      Logger.logger.debug(s"$user call superset for $dbName")

      def callDb(cookie: String, wsClient: WSClient) =
        wsClient.url(URL + s"/databaseview/api/readvalues?_flt_3_database_name=$dbName")
          .withHeaders("Content-Type" -> "application/json",
            "Accept" -> "application/json",
            "Cookie" -> cookie
          ).get

      sim.manageServiceCall(new LoginInfo(null, null, "superset"), callDb).map { resp => Ok(resp.json) }

    }
  }

  def tableByNameAndId(user: String, tableName: String, id: Int) = Action.async { implicit request =>
    RequestContext.execInContext[Future[Result]] ("tableByNameAndId") { () =>
      def callDb(cookie: String, wsClient: WSClient) =
        wsClient.url(URL + s"/tablemodelview/api/readvalues?_flt_0_database=$id&_flt_3_table_name=$tableName")
          .withHeaders("Content-Type" -> "application/json",
            "Accept" -> "application/json",
            "Cookie" -> cookie
          ).get
      Logger.logger.debug(s"$user call superset with table name $tableName and id $id")

      sim.manageServiceCall(new LoginInfo(user, null, "superset"), callDb).map { resp => Ok(resp.json) }

    }

  }


  def tableByName(user: String, datasetName :String) = Action.async { implicit request =>

    RequestContext.execInContext[Future[Result]] ("tableByName") { () =>

      def callDb(cookie: String, wsClient: WSClient) =
        wsClient.url(URL + s"/tablemodelview/api/readvalues?_flt_3_table_name=$datasetName")
          .withHeaders("Content-Type" -> "application/json",
            "Accept" -> "application/json",
            "Cookie" -> cookie
          ).get

      def callDbOpen(cookie: String, wsClient: WSClient) =
        wsClient.url(openDataUrl + s"/tablemodelview/api/readvalues?_flt_3_table_name=$datasetName")
          .withHeaders("Content-Type" -> "application/json",
            "Accept" -> "application/json",
            "Cookie" -> cookie
          ).get

      val response = if (user.equals(openDataUser)) sim.manageServiceCall(new LoginInfo(null, null, "superset_open"), callDbOpen)
      else sim.manageServiceCall(new LoginInfo(user, null, "superset"), callDb)

      response.map { resp => Ok(resp.json) }
    }
  }

  /*
  def publicSlice(user :String) = Action.async { implicit request =>
    val sessionCookie = cache.get[String]("superset." + user).getOrElse("test")
    val responseWs = ws.url(URL + "slicemodelview/api/read")
      .withHeaders("cookie" -> sessionCookie).get()

    responseWs.map { response =>
      if (response.status == 401) {
        val sessionFuture: Future[String] = ws.url(local + "/superset/session").get().map(_.body)

        val result: Future[WSResponse] = for {
          session <- sessionFuture
          cards <- ws.url(URL + "slicemodelview/api/read")
            .withHeaders(("cookie", session))
            .get()
        } yield cards

        //val responseTry: Try[WSResponse] = Await.ready(result, 3 seconds).value.get

        val rr: WSResponse = Await.result(result, 10 seconds)

      //  val jsonResponse: JsValue = responseTry match {
      //    case Success(v) => (v.json \ "result").get
      //    case Failure(_) => throw new RuntimeException("error")
      //  }
      //  Ok(jsonResponse)
        val rrjson = (rr.json \ "result").get
        Ok(rrjson)
      } else {
        Ok((response.json \ "result").get)
      }
    }
  }*/



}