/*
 * Copyright (C) 2022 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.dd2d.mapping

import scala.xml.Node

object SpatialCoverage extends Spatial with BlockTemporalAndSpatial {
  private val controlledValues = List(
    "Netherlands",
    "United Kingdom",
    "Belgium",
    "Germany"
  )

  def toControlledSpatialValue(node: Node): Option[String] = {
    if (controlledValues.contains(node.text)) Some(node.text)
    else Option.empty
  }

  def toUncontrolledSpatialValue(node: Node): Option[String] = {
    if (controlledValues.contains(node.text)) Option.empty
    else Some(node.text)
  }
}
