package com.treasuredata.flow.lang.compiler.analyzer

import com.treasuredata.flow.lang.StatusCode
import com.treasuredata.flow.lang.compiler.{CompilationUnit, Context, Phase}
import com.treasuredata.flow.lang.model.DataType
import com.treasuredata.flow.lang.model.DataType.{ExtensionType, FunctionType, NamedType, SchemaType, UnresolvedType}
import com.treasuredata.flow.lang.model.expr.{ColumnType, Literal}
import com.treasuredata.flow.lang.model.plan.*
import wvlet.log.LogSupport

object PreTypeScan  extends TypeScanner("collect-types")
object PostTypeScan extends TypeScanner("post-type-scan")

/**
  * Scan all referenced types in the code, including imported and defined ones
  */
class TypeScanner(name: String) extends Phase(name) with LogSupport:
  override def run(unit: CompilationUnit, context: Context): CompilationUnit =
    scanTypeDefs(unit.unresolvedPlan, context)
    unit

  protected def scanTypeDefs(plan: LogicalPlan, context: Context): Unit =
    plan.traverse {
      case alias: TypeAlias =>
        context.scope.addAlias(alias.alias, alias.sourceTypeName)
      case td: TypeDef =>
        context.scope.addType(scanTypeDef(td, context))
      case tbl: TableDef =>
        context.scope.addTableDef(tbl)
      case q: Query =>
        context.scope.addType(q.relationType)
    }

  private def scanTypeDef(typeDef: TypeDef, context: Context): DataType =
    // TODO resolve defs
    val defs: Seq[FunctionType] = Seq.empty // typeDef.defs.collect { case tpe: TypeDefDef =>

    val valDefs = typeDef.elems.collect { case v: TypeValDef =>
      val resolvedType = scanDataType(ColumnType(v.tpe.fullName, v.nodeLocation), context)
      NamedType(v.name.fullName, resolvedType)
    }
    val selfType = valDefs.filter(_.name == "self")

    if valDefs.nonEmpty then SchemaType(typeDef.name.fullName, valDefs)
    else
      selfType.size match
        case 0 =>
          throw StatusCode.SYNTAX_ERROR.newException(
            "Missing self parameter in type definition",
            context.compileUnit.toSourceLocation(typeDef.nodeLocation)
          )
        case n if n > 1 =>
          throw StatusCode.SYNTAX_ERROR.newException(
            "Multiple self parameters are found in type definition",
            context.compileUnit.toSourceLocation(typeDef.nodeLocation)
          )
        case 1 =>
          ExtensionType(typeDef.name.fullName, selfType.head, defs)

  private def scanDataType(columnType: ColumnType, context: Context): DataType =
    context.scope
      .findType(columnType.tpe)
      .getOrElse(UnresolvedType(columnType.tpe))
