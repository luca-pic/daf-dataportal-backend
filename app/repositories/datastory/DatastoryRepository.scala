package repositories.datastory

import ftd_api.yaml.{Datastory, Error, Success}
import play.api.libs.ws.WSClient

import scala.concurrent.Future

trait DatastoryRepository {

  def saveDatastory(user: String, datastory: Datastory, token: String, ws: WSClient): Future[Either[Error, Success]]
  def deleteDatastory(id: String, user: String): Future[Either[Error, Success]]
  def getAllDatastory(user: String, groups: List[String], limit: Option[Int], status: Option[Int]): Future[Either[Error, Seq[Datastory]]]
  def getAllPublicDatastory(limit: Option[Int]): Future[Either[Error, Seq[Datastory]]]
  def getDatastoryById(id: String, user: String, group: List[String]): Future[Either[Error, Datastory]]
  def getPublicDatastoryById(id: String): Future[Either[Error, Datastory]]

}

object DatastoryRepository {
  def apply(config: String): DatastoryRepository = config match {
    case "dev" => new DatastoryRepositoryDev
    case "prod" => new DatastoryRepositoryProd
  }
}

trait DatastoryRepositoryComponent {
  val datastoryRepository: DatastoryRepository
}