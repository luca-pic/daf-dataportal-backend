package repositories.widget

import ftd_api.yaml.{Error, Success, Widget, WidgetsFilters}

import scala.concurrent.Future

class WidgetRepositoryDev extends WidgetRepository {

  def widgetsGetAll(user: String, groups: List[String], filters: WidgetsFilters): Future[Either[Error, List[Widget]]] = {
    Future.successful(Right(List()))
  }
  def widgetSave(user: String, widget: Widget): Future[Either[Error, Success]] = {
    Future.successful(Right(Success(None, None)))
  }
  def widgetById(user: String, groups: List[String], id: String): Future[Either[Error, Widget]] = {
    Future.successful(Left(Error(None, None, None)))
  }
  def widgetDelete(user: String, id: String): Future[Either[Error, Success]] = {
    Future.successful(Right(Success(None, None)))
  }

}
