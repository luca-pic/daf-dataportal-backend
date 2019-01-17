package repositories.widget

import ftd_api.yaml.{Error, Success, Widget, WidgetsFilters}

import scala.concurrent.Future

trait WidgetRepository {

  def widgetsGetAll(user: String, groups: List[String], filters: WidgetsFilters): Future[Either[Error, List[Widget]]]
  def widgetSave(user: String, widget: Widget): Future[Either[Error, Success]]
  def widgetById(user: String, groups: List[String], id: String): Future[Either[Error, Widget]]
  def widgetDelete(user: String, id: String): Future[Either[Error, Success]]

}

object WidgetRepository {
  def apply(config: String): WidgetRepository = config match {
    case "dev" => new WidgetRepositoryDev
    case "prod" => new WidgetRepositoryProd
  }
}

trait WidgetRepositoryComponent {
  val widgetRepository: WidgetRepository
}
