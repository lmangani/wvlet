package com.treasuredata.flow.lang.analyzer

import com.treasuredata.flow.lang.CompileUnit
import com.treasuredata.flow.lang.model.plan.{FlowPlan, LogicalPlan, Relation}
import com.treasuredata.flow.lang.parser.FlowParser
import wvlet.log.LogSupport

/**
  */
object Analyzer extends LogSupport:

  def analyzeSourceFolder(path: String): Seq[FlowPlan] =
    val plans   = FlowParser.parseSourceFolder(path)
    val context = AnalyzerContext(Scope.Global)

    def typeScan: Unit = for plan <- plans do
      context.withCompileUnit(plan.compileUnit) { context =>
        TypeScanner.scanSchemaAndTypes(plan, context)
      }

    // Pre-process to collect all schema and typs
    typeScan

    // Post-process to resolve unresolved typesa
    typeScan

    debug(context.getTypes.map(t => s"[${t._1}]: ${t._2}").mkString("\n"))

    // resolve plans
    val resolvedPlans = for plan <- plans yield analyzeSingle(plan, context)
    resolvedPlans

  def analyzeSingle(plan: FlowPlan, globalContext: AnalyzerContext): FlowPlan =
    // Resolve schema and types first.
    globalContext.withCompileUnit(plan.compileUnit) { context =>
      // Then, resolve the plan
      val resolvedPlan: Seq[LogicalPlan] = plan.logicalPlans.map { p =>
        TypeResolver.resolve(p, context)
      }
      FlowPlan(resolvedPlan, plan.compileUnit)
    }
