package com.treasuredata.flow.lang.model

import wvlet.log.LogSupport

import scala.util.Try

abstract class DataType(val typeName: String, val typeParams: Seq[DataType]):
  override def toString: String = typeDescription

  def typeDescription: String =
    if typeParams.isEmpty then typeName
    else s"${typeName}(${typeParams.mkString(", ")})"

  def baseTypeName: String = typeName

  def isBound: Boolean                                  = typeParams.forall(_.isBound)
  def bind(typeArgMap: Map[String, DataType]): DataType = this

object DataType extends LogSupport:

//  def unapply(str: String): Option[DataType] =
//    Try(DataTypeParser.parse(str)).toOption

  case class NamedType(name: String, dataType: DataType) extends DataType(s"${name}:${dataType}", Seq.empty)

  /**
    * DataType parameter for representing concrete types like timestamp(2), and abstract types like timestamp(p).
    */
  sealed abstract class TypeParameter(name: String) extends DataType(typeName = name, typeParams = Seq.empty)

  /**
    * Constant type used for arguments of varchar(n), char(n), decimal(p, q), etc.
    */
  case class IntConstant(value: Int) extends TypeParameter(s"${value}")

  case class TypeVariable(name: String) extends TypeParameter(s"$$${name}"):
    override def isBound: Boolean = false

    override def bind(typeArgMap: Map[String, DataType]): DataType =
      typeArgMap.get(name) match
        case Some(t) => t
        case None    => this

  case class GenericType(override val typeName: String, override val typeParams: Seq[DataType] = Seq.empty)
      extends DataType(typeName, typeParams):
    override def isBound: Boolean = typeParams.forall(_.isBound)

    override def bind(typeArgMap: Map[String, DataType]): DataType =
      GenericType(typeName, typeParams.map(_.bind(typeArgMap)))

  case class IntervalDayTimeType(from: String, to: String) extends DataType(s"interval", Seq.empty):
    override def toString: String = s"interval from ${from} to ${to}"

  sealed trait TimestampField

  object TimestampField:
    case object TIME      extends TimestampField
    case object TIMESTAMP extends TimestampField

  case class TimestampType(field: TimestampField, withTimeZone: Boolean, precision: Option[DataType] = None)
      extends DataType(field.toString.toLowerCase, precision.toSeq):

    override def toString: String =
      val base = super.toString
      if withTimeZone then s"${base} with time zone"
      else base

  private def primitiveTypes: Seq[DataType] = Seq(
    AnyType,
    NullType,
    BooleanType,
    ByteType,
    ShortType,
    IntegerType,
    LongType,
    FloatType,
    RealType,
    DoubleType,
    StringType,
    JsonType,
    DateType,
    BinaryType
  )

  private val primitiveTypeTable: Map[String, DataType] =
    primitiveTypes.map(x => x.typeName -> x).toMap ++
      Map(
        "int"      -> IntegerType,
        "bigint"   -> LongType,
        "tinyint"  -> ByteType,
        "smallint" -> ShortType
      )

  /**
    * data type names that will be mapped to GenericType
    */
  private val knownGenericTypeNames: Set[String] = Set(
    "char",
    "varchar",
    "varbinary",
    // trino-specific types
    "bingtile",
    "ipaddress",
    "json",
    "jsonpath",
    "joniregexp",
    "tdigest",
    "qdigest",
    "uuid",
    "hyperloglog",
    "geometry",
    "p4hyperloglog",
    // lambda
    "function"
  )

  def isKnownGenericTypeName(s: String): Boolean =
    knownGenericTypeNames.contains(s)

  def isPrimitiveTypeName(s: String): Boolean =
    primitiveTypeTable.contains(s)

  def getPrimitiveType(s: String): DataType =
    primitiveTypeTable.getOrElse(s, throw new IllegalArgumentException(s"Unknown primitive type name: ${s}"))

  abstract class PrimitiveType(name: String) extends DataType(name, Seq.empty)
  // calendar date (year, month, day)
  case object DateType extends PrimitiveType("date")

  case object UnknownType extends PrimitiveType("?")
  case object AnyType     extends PrimitiveType("any")
  case object NullType    extends PrimitiveType("null")

  case object BooleanType                      extends PrimitiveType("boolean")
  abstract class NumericType(typeName: String) extends PrimitiveType(typeName)
  case object ByteType                         extends NumericType("byte")
  case object ShortType                        extends NumericType("short")
  case object IntegerType                      extends NumericType("integer")
  case object LongType                         extends NumericType("long")

  abstract class FractionType(typeName: String) extends NumericType(typeName)
  case object FloatType                         extends FractionType("float")
  case object RealType                          extends FractionType("real")
  case object DoubleType                        extends FractionType("double")

  case class CharType(length: Option[DataType])    extends DataType("char", length.toSeq)
  case object StringType                           extends PrimitiveType("string")
  case class VarcharType(length: Option[DataType]) extends DataType("varchar", length.toSeq)

  case class DecimalType(precision: TypeParameter, scale: TypeParameter)
      extends DataType("decimal", Seq(precision, scale))

  object DecimalType:
    def of(precision: Int, scale: Int): DecimalType = DecimalType(IntConstant(precision), IntConstant(scale))

    def of(precision: DataType, scale: DataType): DecimalType =
      (precision, scale) match
        case (p: TypeParameter, s: TypeParameter) => DecimalType(p, s)
        case _ =>
          throw IllegalArgumentException(s"Invalid DecimalType parameters (${precision}, ${scale})")

  case object JsonType   extends PrimitiveType("json")
  case object BinaryType extends PrimitiveType("binary")

  case class ArrayType(elemType: DataType)                   extends DataType(s"array", Seq(elemType))
  case class MapType(keyType: DataType, valueType: DataType) extends DataType(s"map", Seq(keyType, valueType))
  case class RecordType(elems: Seq[DataType])                extends DataType("record", elems)

  /**
    * For describing the type of 'select *'
    */
  case class EmbeddedRecordType(elems: Seq[DataType]) extends DataType("*", elems):

    override def typeDescription: String =
      elems.map(_.typeDescription).mkString(", ")

//  def parse(typeName: String): DataType =
//    DataTypeParser.parse(typeName)
//
//  def parseArgs(typeArgs: String): List[DataType] =
//    DataTypeParser.parseTypeList(typeArgs)