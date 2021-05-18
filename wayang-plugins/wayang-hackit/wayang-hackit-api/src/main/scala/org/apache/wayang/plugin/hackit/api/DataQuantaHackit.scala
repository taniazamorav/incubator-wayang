/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 */

package org.apache.wayang.plugin.hackit.api

import org.apache.wayang.api.{PlanBuilder, basicDataUnitType, dataSetType, groupedDataSetType, groupedDataUnitType}
import org.apache.wayang.api.dataquanta.{DataQuanta, DataQuantaCreator, KeyedDataQuanta}
import org.apache.wayang.basic.data.{Tuple2 => WayangTuple2}
import org.apache.wayang.basic.function.ProjectionDescriptor
import org.apache.wayang.basic.operators.{CartesianOperator, CoGroupOperator, CountOperator, DistinctOperator, FilterOperator, FlatMapOperator, GlobalMaterializedGroupOperator, GlobalReduceOperator, IntersectOperator, JoinOperator, MapOperator, MapPartitionsOperator, MaterializedGroupByOperator, ReduceByOperator, SampleOperator, SortOperator, UnionAllOperator, ZipWithIdOperator}
import org.apache.wayang.core.function.FunctionDescriptor.{SerializableBinaryOperator, SerializableFunction, SerializablePredicate}
import org.apache.wayang.core.function.{FlatMapDescriptor, MapPartitionsDescriptor, PredicateDescriptor, ReduceDescriptor, TransformationDescriptor}
import org.apache.wayang.core.optimizer.ProbabilisticDoubleInterval
import org.apache.wayang.core.optimizer.costs.LoadProfileEstimator
import org.apache.wayang.core.plan.wayangplan.{ElementaryOperator, Operator, OutputSlot, WayangPlan}
import org.apache.wayang.plugin.hackit.core.tagger.HackitTagger
import org.apache.wayang.plugin.hackit.core.tagger.wrapper.FunctionWrapperHackit
import org.apache.wayang.plugin.hackit.core.tags.HackitTag
import org.apache.wayang.plugin.hackit.core.tuple.HackitTuple



import java.{lang, util}
import java.lang.{Iterable => JavaIterable}
import java.util.function.IntUnaryOperator
import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * Represents an intermediate result/data flow edge in a [[WayangPlan]].
 *
 * @param operator    a unary [[Operator]] that produces this instance
 * @param ev$1        the data type of the elements in this instance
 * @param planBuilder keeps track of the [[WayangPlan]] being build
 */
class DataQuantaHackit[Out : ClassTag]
                            (override val operator: ElementaryOperator, outputIndex: Int = 0)
                            (implicit override val  planBuilder: PlanBuilder)
                    extends DataQuanta[Out](operator, outputIndex)
{

  var tagger : HackitTagger = null;
  //var sniffer : HackitSniffer = null;

  /**
   * add a [[HackitTag]] on the [[HackitTagger]] to enable the process
   *
   * @param tag [[HackitTag]] to be added
   * @return the self instance
   */
  //TODO add the version of collection
  def addTag(tag: HackitTag): DataQuantaHackit[Out] = {
//    tagger.addPreTag(tag)
    println("here")
    this
  }

  def underHackit[Key: ClassTag](): DataQuantaHackit[HackitTuple[Key, Out]] = {
//    HackItRDD<K, T> ktHackItRDD = new HackItRDD<K, T>(
//      rdd.map((Function<T, HackItTuple<K, T>>) HackItTuple::new)
//    );

null
  }

  /**
   * Feed this instance into a [[MapOperator]].
   *
   * @param udf     a Java 8 lambda expression as UDF for the [[MapOperator]]
   * @param udfLoad optional [[LoadProfileEstimator]] for the `udf`
   * @return a new instance representing the [[MapOperator]]'s output
   */
   override def mapJava[NewOut: ClassTag](udf: SerializableFunction[Out, NewOut],
                                         udfLoad: LoadProfileEstimator = null): DataQuantaHackit[NewOut] = {

    val wrapper_internal = new FunctionWrapperHackit[Object, Out, NewOut](udf)
    this.tagger = wrapper_internal

    val wrapper = new SerializableFunction[HackitTuple[Object, Out], HackitTuple[Object, NewOut]](){
      override def apply(t: HackitTuple[Object, Out]): HackitTuple[Object, NewOut] = {
        println(t)
        wrapper_internal.apply(t)
      }
    }

    val mapOperator = new MapOperator(new TransformationDescriptor(
      wrapper, basicDataUnitType[HackitTuple[Object, Out]], basicDataUnitType[HackitTuple[Object, NewOut]], udfLoad
    ))
    this.connectTo(mapOperator, 0)
    DataQuantaHackit.wrap[NewOut](mapOperator)
  }

  /**
   * Feed this instance into a [[MapPartitionsOperator]].
   *
   * @param udf         a Java 8 lambda expression as UDF for the [[MapPartitionsOperator]]
   * @param selectivity selectivity of the UDF
   * @param udfLoad     optional [[LoadProfileEstimator]] for the `udf`
   * @return a new instance representing the [[MapOperator]]'s output
   */
  override def mapPartitionsJava[NewOut: ClassTag](udf: SerializableFunction[JavaIterable[Out], JavaIterable[NewOut]],
                                                   selectivity: ProbabilisticDoubleInterval = null,
                                                   udfLoad: LoadProfileEstimator = null): DataQuantaHackit[NewOut] = {
    val mapOperator = new MapPartitionsOperator(
      new MapPartitionsDescriptor(udf, basicDataUnitType[Out], basicDataUnitType[NewOut], selectivity, udfLoad)
    )
    this.connectTo(mapOperator, 0)
    DataQuantaHackit.wrap[NewOut](mapOperator)
  }

  /**
   * Feed this instance into a [[MapOperator]] with a [[ProjectionDescriptor]].
   *
   * @param fieldNames names of the fields to be projected
   * @return a new instance representing the [[MapOperator]]'s output
   */
  override def project[NewOut: ClassTag](fieldNames: Seq[String]): DataQuantaHackit[NewOut] = {
    val projectionOperator = new MapOperator(
      new ProjectionDescriptor(basicDataUnitType[Out], basicDataUnitType[NewOut], fieldNames: _*)
    )
    this.connectTo(projectionOperator, 0)
    DataQuantaHackit.wrap[NewOut](projectionOperator)
  }

  /**
   * Feed this instance into a [[FilterOperator]].
   *
   * @param udf         UDF for the [[FilterOperator]]
   * @param sqlUdf      UDF as SQL `WHERE` clause
   * @param selectivity selectivity of the UDF
   * @param udfLoad     optional [[LoadProfileEstimator]] for the `udf`
   * @return a new instance representing the [[FilterOperator]]'s output
   */
  override def filterJava(udf: SerializablePredicate[Out],
                          sqlUdf: String = null,
                          selectivity: ProbabilisticDoubleInterval = null,
                          udfLoad: LoadProfileEstimator = null): DataQuantaHackit[Out] = {
//    val filterOperator = new FilterOperator(new PredicateDescriptor(
//      udf, this.output.getType.getDataUnitType.toBasicDataUnitType, selectivity, udfLoad
//    ).withSqlImplementation(sqlUdf))
//    this.connectTo(filterOperator, 0)
//    DataQuantaHackit.wrap[Out](filterOperator)
    null
  }

  /**
   * Feed this instance into a [[FlatMapOperator]].
   *
   * @param udf         a Java 8 lambda expression as UDF for the [[FlatMapOperator]]
   * @param selectivity selectivity of the UDF
   * @param udfLoad     optional [[LoadProfileEstimator]] for the `udf`
   * @return a new instance representing the [[FlatMapOperator]]'s output
   */
  override def flatMapJava[NewOut: ClassTag](udf: SerializableFunction[Out, JavaIterable[NewOut]],
                                             selectivity: ProbabilisticDoubleInterval = null,
                                             udfLoad: LoadProfileEstimator = null): DataQuantaHackit[NewOut] = {
//    val flatMapOperator = new FlatMapOperator(new FlatMapDescriptor(
//      udf, basicDataUnitType[Out], basicDataUnitType[NewOut], selectivity, udfLoad
//    ))
//    this.connectTo(flatMapOperator, 0)
//    DataQuantaHackit.wrap[NewOut](flatMapOperator)
    null
  }


  /**
   * Feed this instance into a [[SampleOperator]].
   *
   * @param sampleSizeFunction absolute size of the sample as a function of the current iteration number
   * @param datasetSize        optional size of the dataset to be sampled
   * @param sampleMethod       the [[SampleOperator.Methods]] to use for sampling
   * @return a new instance representing the [[FlatMapOperator]]'s output
   */
  override def sampleDynamicJava(sampleSizeFunction: IntUnaryOperator,
                                 datasetSize: Long = SampleOperator.UNKNOWN_DATASET_SIZE,
                                 seed: Option[Long] = None,
                                 sampleMethod: SampleOperator.Methods = SampleOperator.Methods.ANY): DataQuantaHackit[Out] = {
    if (seed.isEmpty) {
      val sampleOperator = new SampleOperator(
        sampleSizeFunction,
        dataSetType[Out],
        sampleMethod
      )
      sampleOperator.setDatasetSize(datasetSize)
      this.connectTo(sampleOperator, 0)
      DataQuantaHackit.wrap[Out](sampleOperator)
    }
    else {
      val sampleOperator = new SampleOperator(
        sampleSizeFunction,
        dataSetType[Out],
        sampleMethod,
        seed.get
      )
      sampleOperator.setDatasetSize(datasetSize)
      this.connectTo(sampleOperator, 0)
      DataQuantaHackit.wrap[Out](sampleOperator)
    }
  }

  /**
   * Assigns this instance a key extractor, which enables some key-based operations.
   *
   * @see KeyedDataQuanta
   * @param keyExtractor extracts the key from the [[DataQuantaDefault]]
   * @return the [[KeyedDataQuanta]]
   */
  //TODO validate this implementation
  override def keyByJava[Key: ClassTag](keyExtractor: SerializableFunction[Out, Key]) : KeyedDataQuanta[Out, Key] = {
    new KeyedDataQuanta[Out, Key](this, keyExtractor)
  }

  /**
   * Feed this instance into a [[ReduceByOperator]].
   *
   * @param keyUdf  UDF to extract the grouping key from the data quanta
   * @param udf     aggregation UDF for the [[ReduceByOperator]]
   * @param udfLoad optional [[LoadProfileEstimator]] for the `udf`
   * @return a new instance representing the [[ReduceByOperator]]'s output
   */
  override def reduceByKeyJava[Key: ClassTag](keyUdf: SerializableFunction[Out, Key],
                                              udf: SerializableBinaryOperator[Out],
                                              udfLoad: LoadProfileEstimator = null)
  : DataQuantaHackit[Out] = {
//    val reduceByOperator = new ReduceByOperator(
//      new TransformationDescriptor(keyUdf, basicDataUnitType[Out], basicDataUnitType[Key]),
//      new ReduceDescriptor(udf, groupedDataUnitType[Out], basicDataUnitType[Out], udfLoad)
//    )
//    this.connectTo(reduceByOperator, 0)
//    DataQuantaHackit.wrap[Out](reduceByOperator)
    null
  }

  /**
   * Feed this instance into a [[MaterializedGroupByOperator]].
   *
   * @param keyUdf     UDF to extract the grouping key from the data quanta
   * @param keyUdfLoad optional [[LoadProfileEstimator]] for the `keyUdf`
   * @return a new instance representing the [[MaterializedGroupByOperator]]'s output
   */
  override def groupByKeyJava[Key: ClassTag](keyUdf: SerializableFunction[Out, Key],
                                             keyUdfLoad: LoadProfileEstimator = null): DataQuantaHackit[java.lang.Iterable[Out]] = {
//    val groupByOperator = new MaterializedGroupByOperator(
//      new TransformationDescriptor(keyUdf, basicDataUnitType[Out], basicDataUnitType[Key], keyUdfLoad),
//      dataSetType[Out],
//      groupedDataSetType[Out]
//    )
//    this.connectTo(groupByOperator, 0)
//    DataQuantaHackit.wrap[java.lang.Iterable[Out]](groupByOperator)
    null
  }

  /**
   * Feed this instance into a [[GlobalReduceOperator]].
   *
   * @param udf     aggregation UDF for the [[GlobalReduceOperator]]
   * @param udfLoad optional [[LoadProfileEstimator]] for the `udf`
   * @return a new instance representing the [[GlobalReduceOperator]]'s output
   */
  override def reduceJava(udf: SerializableBinaryOperator[Out],
                          udfLoad: LoadProfileEstimator = null): DataQuantaHackit[Out] = {
//    val globalReduceOperator = new GlobalReduceOperator(
//      new ReduceDescriptor(udf, groupedDataUnitType[Out], basicDataUnitType[Out], udfLoad)
//    )
//    this.connectTo(globalReduceOperator, 0)
//    DataQuantaHackit.wrap[Out](globalReduceOperator)
    null
  }

  /**
   * Feeds this and a further instance into a [[JoinOperator]].
   *
   * @param thisKeyUdf UDF to extract keys from data quanta in this instance
   * @param that       the other instance
   * @param thatKeyUdf UDF to extract keys from data quanta from `that` instance
   * @return a new instance representing the [[JoinOperator]]'s output
   */
  override def joinJava[ThatOut: ClassTag, Key: ClassTag](thisKeyUdf: SerializableFunction[Out, Key], that: DataQuanta[ThatOut], thatKeyUdf: SerializableFunction[ThatOut, Key]): DataQuantaHackit[WayangTuple2[Out, ThatOut]] = {
//    require(this.planBuilder eq that.planBuilder, s"$this and $that must use the same plan builders.")
//    val joinOperator = new JoinOperator(
//      new TransformationDescriptor(thisKeyUdf, basicDataUnitType[Out], basicDataUnitType[Key]),
//      new TransformationDescriptor(thatKeyUdf, basicDataUnitType[ThatOut], basicDataUnitType[Key])
//    )
//    this.connectTo(joinOperator, 0)
//    that.connectTo(joinOperator, 1)
//    DataQuantaHackit.wrap[WayangTuple2[Out, ThatOut]](joinOperator)
    null
  }

  /**
   * Feeds this and a further instance into a [[CoGroupOperator]].
   *
   * @param thisKeyUdf UDF to extract keys from data quanta in this instance
   * @param that       the other instance
   * @param thatKeyUdf UDF to extract keys from data quanta from `that` instance
   * @return a new instance representing the [[CoGroupOperator]]'s output
   */
  override def coGroupJava[ThatOut: ClassTag, Key: ClassTag](thisKeyUdf: SerializableFunction[Out, Key], that: DataQuanta[ThatOut], thatKeyUdf: SerializableFunction[ThatOut, Key]): DataQuantaHackit[WayangTuple2[JavaIterable[Out], JavaIterable[ThatOut]]] = {
//    require(this.planBuilder eq that.planBuilder, s"$this and $that must use the same plan builders.")
//    val coGroupOperator = new CoGroupOperator(
//      new TransformationDescriptor(thisKeyUdf, basicDataUnitType[Out], basicDataUnitType[Key]),
//      new TransformationDescriptor(thatKeyUdf, basicDataUnitType[ThatOut], basicDataUnitType[Key])
//    )
//    this.connectTo(coGroupOperator, 0)
//    that.connectTo(coGroupOperator, 1)
//    DataQuantaHackit.wrap[WayangTuple2[java.lang.Iterable[Out], java.lang.Iterable[ThatOut]]](coGroupOperator)
    null
  }


  /**
   * Feeds this and a further instance into a [[SortOperator]].
   *
   * @param keyUdf UDF to extract key from data quanta in this instance
   * @return a new instance representing the [[SortOperator]]'s output
   */
  override def sortJava[Key: ClassTag] (keyUdf: SerializableFunction[Out, Key]) : DataQuantaHackit[Out] = {
//    val sortOperator = new SortOperator(new TransformationDescriptor(
//      keyUdf, basicDataUnitType[Out], basicDataUnitType[Key]))
//    this.connectTo(sortOperator, 0)
//    DataQuantaHackit.wrap[Out](sortOperator)
     null
  }


  /**
   * Feed this instance into a [[GlobalMaterializedGroupOperator]].
   *
   * @return a new instance representing the [[GlobalMaterializedGroupOperator]]'s output
   */
  override def group(): DataQuantaHackit[JavaIterable[Out]] = {
//    val groupOperator = new GlobalMaterializedGroupOperator(dataSetType[Out], groupedDataSetType[Out])
//    this.connectTo(groupOperator, 0)
//    DataQuantaHackit.wrap[JavaIterable[Out]](groupOperator)
    null
  }

  /**
   * Feed this instance and a further instance into a [[UnionAllOperator]].
   *
   * @param that the other instance to union with
   * @return a new instance representing the [[UnionAllOperator]]'s output
   */
  override def union(that: DataQuanta[Out]): DataQuantaHackit[Out] = {
//    require(this.planBuilder eq that.planBuilder, s"$this and $that must use the same plan builders.")
//    val unionAllOperator = new UnionAllOperator(dataSetType[Out])
//    this.connectTo(unionAllOperator, 0)
//    that.connectTo(unionAllOperator, 1)
//    DataQuantaHackit.wrap[Out](unionAllOperator)
    null
  }

  /**
   * Feed this instance and a further instance into a [[IntersectOperator]].
   *
   * @param that the other instance to intersect with
   * @return a new instance representing the [[IntersectOperator]]'s output
   */
  override def intersect(that: DataQuanta[Out]): DataQuantaHackit[Out] =  {
//    require(this.planBuilder eq that.planBuilder, s"$this and $that must use the same plan builders.")
//    val intersectOperator = new IntersectOperator(dataSetType[Out])
//    this.connectTo(intersectOperator, 0)
//    that.connectTo(intersectOperator, 1)
//    DataQuantaHackit.wrap[Out](intersectOperator)
    null
  }

  /**
   * Feeds this and a further instance into a [[CartesianOperator]].
   *
   * @param that the other instance
   * @return a new instance representing the [[CartesianOperator]]'s output
   */
  override def cartesian[ThatOut: ClassTag](that: DataQuanta[ThatOut]): DataQuantaHackit[WayangTuple2[Out, ThatOut]] = {
//    require(this.planBuilder eq that.planBuilder, s"$this and $that must use the same plan builders.")
//    val cartesianOperator = new CartesianOperator(dataSetType[Out], dataSetType[ThatOut])
//    this.connectTo(cartesianOperator, 0)
//    that.connectTo(cartesianOperator, 1)
//    DataQuantaHackit.wrap[WayangTuple2[Out, ThatOut]](cartesianOperator)
    null
  }

  /**
   * Feeds this instance into a [[ZipWithIdOperator]].
   *
   * @return a new instance representing the [[ZipWithIdOperator]]'s output
   */
  override def zipWithId: DataQuantaHackit[WayangTuple2[lang.Long, Out]] = {
//    val zipWithIdOperator = new ZipWithIdOperator(dataSetType[Out])
//    this.connectTo(zipWithIdOperator, 0)
//    DataQuantaHackit.wrap[WayangTuple2[lang.Long, Out]](zipWithIdOperator)
    null
  }

  /**
   * Feeds this instance into a [[DistinctOperator]].
   *
   * @return a new instance representing the [[DistinctOperator]]'s output
   */
  override def distinct: DataQuantaHackit[Out] = {
//    val distinctOperator = new DistinctOperator(dataSetType[Out])
//    this.connectTo(distinctOperator, 0)
//    DataQuantaHackit.wrap[Out](distinctOperator)
    null
  }

  /**
   * Feeds this instance into a [[CountOperator]].
   *
   * @return a new instance representing the [[CountOperator]]'s output
   */
  override def count: DataQuantaHackit[lang.Long] = {
//    val countOperator = new CountOperator(dataSetType[Out])
//    this.connectTo(countOperator, 0)
//    DataQuantaHackit.wrap[lang.Long](countOperator)
    null
  }
}

object DataQuantaHackit extends DataQuantaCreator{

  def wrap[T:ClassTag](operator: ElementaryOperator, outputIndex: Int = 0)(implicit planBuilder: PlanBuilder): DataQuantaHackit[T] = {
    new DataQuantaHackit[T](operator, outputIndex)
  }

  def create[T](output: OutputSlot[T])(implicit planBuilder: PlanBuilder): DataQuantaHackit[_] =
    new DataQuantaHackit(output.getOwner.asInstanceOf[ElementaryOperator], output.getIndex)(ClassTag(output.getType.getDataUnitType.getTypeClass), planBuilder)

  implicit def wrapDataQuanta[T:ClassTag](dataQuanta: DataQuanta[T]): DataQuantaHackit[T] = {
    new DataQuantaHackit[T](dataQuanta.operator, dataQuanta.outputIndex)(ClassTag(dataQuanta.output.getType.getDataUnitType.getTypeClass), dataQuanta.planBuilder);
  }

}
