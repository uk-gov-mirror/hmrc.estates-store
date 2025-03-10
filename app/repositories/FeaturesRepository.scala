/*
 * Copyright 2021 HM Revenue & Customs
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

package repositories

import javax.inject.{Inject, Singleton}
import models.FeatureFlag
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection

import scala.concurrent.{ExecutionContext, Future}

@Singleton()
class FeaturesRepository @Inject()(mongo: ReactiveMongoApi)
                                  (implicit ec: ExecutionContext) {

  private val collectionName: String = "features"
  val featureFlagDocumentId = "feature-flags"

  private def collection: Future[JSONCollection] =
    mongo.database.map(_.collection[JSONCollection](collectionName))

  private val lastUpdatedIndex = Index(
    key = Seq("lastUpdated" -> IndexType.Ascending),
    name = Some("features-last-updated-index")
  )

  val started: Future[Unit] =
    collection.flatMap {
      coll =>
        for {
          _ <- coll.indexesManager.ensure(lastUpdatedIndex)
        } yield ()
    }

  def getFeatureFlags: Future[Seq[FeatureFlag]] =
    collection.flatMap(_.find(Json.obj("_id" -> featureFlagDocumentId), None)
      .one[JsObject])
      .map(_.map(js => (js \ "flags").as[Seq[FeatureFlag]]))
      .map(_.getOrElse(Seq.empty[FeatureFlag]))

  def setFeatureFlags(flags: Seq[FeatureFlag]): Future[Boolean] = {

    val selector = Json.obj(
      "_id" -> featureFlagDocumentId
    )

    val modifier = Json.obj(
      "_id" -> featureFlagDocumentId,
      "flags" -> Json.toJson(flags)
    )

    collection.flatMap {
      _.update(ordered = false)
        .one(selector, modifier, upsert = true).map {
        lastError: WriteResult =>
          lastError.ok
      }
    }
  }
}