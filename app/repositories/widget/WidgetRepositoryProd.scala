package repositories.widget

import java.util.UUID

import com.mongodb
import com.mongodb.{DBObject, casbah}
import com.mongodb.casbah.Imports.{MongoCredential, ServerAddress}
import com.mongodb.casbah._
import com.sksamuel.elastic4s.http.ElasticDsl.{boolQuery, field, matchQuery, must, search, termQuery, _}
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.queries.matches.MatchQueryDefinition
import ftd_api.yaml.{Error, Success, Widget, WidgetsFilters}
import play.api.Logger
import utils._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class WidgetRepositoryProd extends WidgetRepository {

  private val mongoHost: String = ConfigReader.getDbHost
  private val mongoPort = ConfigReader.getDbPort

  private val username = ConfigReader.userName
  private val dbName = ConfigReader.database
  private val password = ConfigReader.password

  private val server = new ServerAddress(mongoHost, mongoPort)
  private val credentials = MongoCredential.createCredential(username, dbName, password.toCharArray)

  private val elasticsearchUrl = ConfigReader.getElasticsearchUrl
  private val elasticsearchPort = ConfigReader.getElasticsearchPort
  private val elasticsearchIndex = ConfigReader.getElasticsearchIndex

  private val collName = ConfigReader.getCollWidgetsName
  private val typeName = ConfigReader.getElasticsearchTypeWidgetsName


  private def textStringQuery(text: Option[String]): List[MatchQueryDefinition] = {
    val nameField = "name"
    val sourcesField = "sources"

    text match {
      case Some(value) =>  List(matchQuery(nameField, value), matchQuery(sourcesField, value))
      case None        =>  List()
    }
  }

  private def ownerQueryString(owner: Option[Boolean], user: String): List[MatchQueryDefinition] = {
    val ownerField = "author"
    owner match {
      case Some(true) =>  List(matchQuery(ownerField, user))
      case _          =>  List()
    }
  }

  private def exist(coll: MongoCollection, conditions: (String, String)*): Boolean = {
    import mongodb.casbah.query.Imports._

    val query = $and(conditions
      .map{
        case (field, value) =>
          mongodb.casbah.Imports.MongoDBObject(field -> value)
      }
    )
    coll.findOne(query) match {
      case Some(_) => true
      case _       => false
    }
  }

  def widgetsGetAll(user: String, groups: List[String], filters: WidgetsFilters): Future[Either[Error, List[Widget]]] = {
    import ftd_api.yaml.BodyReads._
    Logger.logger.debug(s"elasticsearchUrl: $elasticsearchUrl elasticsearchPort: $elasticsearchPort index: $elasticsearchIndex type: $typeName")

    def queryElasticsearch: SearchDefinition = {
      search(elasticsearchIndex).types(typeName).query(
        boolQuery()
          must(
            should(textStringQuery(filters.text)),
            must(
              ownerQueryString(filters.owner, user)
            ),
            should(
              must(termQuery("shared", false), matchQuery("acl.groupName", groups.mkString(" ")).operator("OR")),
              must(termQuery("shared", false), termQuery("author", user)),
              termQuery("shared", true)
            )
          )
      ).limit(filters.limit.getOrElse(1000))
    }

    val client = HttpClient(ElasticsearchClientUri(elasticsearchUrl, elasticsearchPort))

    Logger.debug(s"$user execute query: ${client.show(queryElasticsearch)}")

    val response: Future[List[Widget]] = client.execute(queryElasticsearch).map{ res =>
      res.hits.hits.toList.map{ widgetsList =>
        WidgetReads.reads(Json.parse(widgetsList.sourceAsString)).get
      }
    }

    response.map{ res =>
      if(res.nonEmpty) Right(res)
      else Left(Error(Some(404), Some("Widgets not found"), None))
    }

  }

  def widgetSave(user: String, widget: Widget): Future[Either[Error, Success]] = {
    import ftd_api.yaml.ResponseWrites.WidgetWrites

    val mongoClient: MongoDB = MongoClient(server, List(credentials))(dbName)
    val coll = mongoClient(collName)

    def save: Either[Error, Success] = {
      val widgetToSave: Widget = widget.copy(author = Some(user), id = Some(UUID.randomUUID().toString))
      val widgetDBObject: DBObject = com.mongodb.util.JSON.parse(
        Json.toJson(
          widgetToSave
        ).toString()).asInstanceOf[DBObject]
      Logger.debug(s"$user try to save $widgetToSave")
      val result: TypeImports.WriteResult = coll.insert(widgetDBObject)
      if(result.wasAcknowledged()) { Logger.debug(s"$user saved $widgetToSave"); Right(Success(Some(s"widget ${widget.name} saved"), None)) }
      else { Logger.debug(s"$user not saved $widgetToSave"); Left(Error(Some(500), Some(s"widget ${widget.name} not saved"), None)) }
    }

    def update(queryId: String): Either[Error, Success] = {
      Logger.debug(s"$user try to update $widget")
      val result: casbah.TypeImports.WriteResult = coll.update(
        QueryObject.composeQuery(MultiQuery(Seq(QueryComponent("author", user), QueryComponent("id", queryId)))),
        com.mongodb.util.JSON.parse(
          Json.toJson(widget).toString()).asInstanceOf[DBObject]
      )
      if(result.getN == 0) { Logger.debug(s"$user: error in update of ${widget.name}"); Left(Error(Some(500), Some("error in update notifications"), None)) }
      else { Logger.debug(s"$user: widget ${widget.name} is updated"); Right(Success(Some(s"widget ${widget.name} is updated"), None)) }
    }

    val response: Either[Error, Success] = widget.id match {
      case Some(id) => update(id)
      case None     => save
    }

    Future.successful(response)

  }

  def widgetById(user: String, groups: List[String], id: String): Future[Either[Error, Widget]] = {
    import mongodb.casbah.query.Imports._
    import ftd_api.yaml.BodyReads.WidgetReads

    val mongoClient: MongoDB = MongoClient(server, List(credentials))(dbName)
    val coll: MongoCollection = mongoClient(collName)

    val shearedField = "shared"
    val aclOrgNameField = "acl.groupName"
    val authorField = "author"
    val idField = "id"

    val query = $and(
      mongodb.casbah.Imports.MongoDBObject(idField -> id),
      $or(
        mongodb.casbah.Imports.MongoDBObject(shearedField -> true),
        $and(mongodb.casbah.Imports.MongoDBObject(shearedField -> false), mongodb.casbah.Imports.MongoDBObject(authorField -> user)),
        aclOrgNameField $in groups
      )
    )
    Logger.debug(s"$user try to get widget $id")
    val result: Option[commons.TypeImports.DBObject] = coll.findOne(query)

    val json: JsValue = Json.parse(com.mongodb.util.JSON.serialize(result))

    val widget: Option[Widget] = json.validate[Widget] match {
      case s: JsSuccess[Widget] => Logger.debug(s"found widget ${s.get.name}"); Some(s.get)
      case e: JsError           => Logger.debug(s"error in widgetById: $e");    None
    }

    if     (widget.isDefined)           Future.successful(Right(widget.get))
    else if(exist(coll, (idField, id))) Future.successful(Left(Error(Some(401), Some("Unauthorized"), None)))
    else                                Future.successful(Left(Error(Some(404), Some("Widget not found"), None)))

  }

  def widgetDelete(user: String, id: String): Future[Either[Error, Success]] = {
    import mongodb.casbah.query.Imports._

    val mongoClient: MongoDB  = MongoClient(server, List(credentials))(dbName)
    val coll: MongoCollection = mongoClient(collName)

    val idField = "id"
    val authorField = "author"


    val query = $and(
      mongodb.casbah.Imports.MongoDBObject(idField -> id),
      mongodb.casbah.Imports.MongoDBObject(authorField -> user)
    )

    val removed: _root_.com.mongodb.casbah.TypeImports.WriteResult = coll.remove(query)

    if(removed.getN > 0)                                     Future.successful(Right(Success(Some("widget deleted"), None)))
    else if(exist(coll, (idField, id), (authorField, user))) Future.successful(Left(Error(Some(401), Some("Unauthorized"), None)))
    else                                                     Future.successful(Left(Error(Some(500), Some("widget not delete"), None)))
  }
}
