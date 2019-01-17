package utils

import com.mongodb

trait Query

final case class SimpleQuery(queryCondition: QueryComponent) extends Query

final case class MultiQuery(seqQueryConditions: Seq[QueryComponent]) extends Query


final case class QueryComponent(nameField: String, valueField: Any)

object QueryObject {
  def composeQuery(query: Query) =  {

    def simpleQuery(queryComponent: QueryComponent) = {
      mongodb.casbah.Imports.MongoDBObject(queryComponent.nameField -> queryComponent.valueField)
    }

    def multiAndQuery(seqQueryComponent: Seq[QueryComponent]) = {
      import mongodb.casbah.query.Imports._

      $and(seqQueryComponent
        .map(queryComponent =>
          mongodb.casbah.Imports.MongoDBObject(queryComponent.nameField -> queryComponent.valueField))
      )
    }

    query match {
      case s: SimpleQuery => simpleQuery(s.queryCondition)
      case m: MultiQuery => multiAndQuery(m.seqQueryConditions)
    }

  }
}