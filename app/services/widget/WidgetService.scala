package services.widget

import ftd_api.yaml.{Error, Success, Widget, WidgetsFilters}
import play.api.{Configuration, Environment}
import repositories.widget.{WidgetRepository, WidgetRepositoryComponent}

import scala.concurrent.Future


trait WidgetServiceComponent {
  this: WidgetRepositoryComponent => val widgetService: WidgetService

  class WidgetService {

    def widgetsGetAll(user: String, groups: List[String], filters: WidgetsFilters): Future[Either[Error, List[Widget]]] = {
      widgetRepository.widgetsGetAll(user, groups, filters)
    }

    def widgetSave(user: String, widget: Widget): Future[Either[Error, Success]] = {
      widgetRepository.widgetSave(user, widget)
    }

    def widgetById(user: String, groups: List[String], id: String): Future[Either[Error, Widget]] = {
      widgetRepository.widgetById(user, groups, id)
    }

    def widgetDelete(user: String, id: String): Future[Either[Error, Success]] = {
      widgetRepository.widgetDelete(user, id)
    }

  }

}

object WidgetRegistry extends WidgetRepositoryComponent
  with WidgetServiceComponent
{
  val conf = Configuration.load(Environment.simple())
  val app: String = conf.getString("app.type").getOrElse("prod")
  val widgetRepository =  WidgetRepository(app)
  val widgetService = new WidgetService

}