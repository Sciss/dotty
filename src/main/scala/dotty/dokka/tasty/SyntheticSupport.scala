package dotty.dokka.tasty

import scala.tasty.Reflection

trait SyntheticsSupport:
  self: TastyParser =>

  import reflect._

  extension (s: Symbol):
    def isSyntheticFunc: Boolean = s.flags.is(Flags.Synthetic) || s.flags.is(Flags.FieldAccessor)

    def isExtensionMethod: Boolean = hackIsExtension(self.reflect)(s)

    def isOpaque: Boolean = hackIsOpaque(self.reflect)(s)

    def extendedSymbol: Option[ValDef] =
      Option.when(hackIsExtension(self.reflect)(s))(
        if(hackIsLeftAssoc(s)) s.tree.asInstanceOf[DefDef].paramss(0)(0)
        else s.tree.asInstanceOf[DefDef].paramss(1)(0)
      )

  def isSyntheticField(c: Symbol, classDef: ClassDef) =
    c.flags.is(Flags.CaseAcessor) || c.flags.is(Flags.Object)

  def isValidPos(pos: Position) =
    val a = (pos.exists, pos.start, pos.end)
    pos.exists && pos.start != pos.end

  def constructorWithoutParamLists(c: ClassDef): Boolean =
    !isValidPos(c.constructor.pos)  || {
      val end = c.constructor.pos.end
      val typesEnd =  c.constructor.typeParams.lastOption.fold(end - 1)(_.pos.end)
      val classDefTree = c.constructor.show
      c.constructor.typeParams.nonEmpty && end <= typesEnd + 1
    }

  // TODO: #49 Remove it after TASTY-Reflect release with published flag Extension
  def hackIsExtension(r: Reflection)(rsym: r.Symbol): Boolean = {
    import dotty.tools.dotc
    given dotc.core.Contexts.Context = r.rootContext.asInstanceOf
    val sym = rsym.asInstanceOf[dotc.core.Symbols.Symbol]
    sym.is(dotc.core.Flags.Extension)
  }

  def hackIsOpaque(r: Reflection)(rsym: r.Symbol): Boolean = {
    import dotty.tools.dotc
    given dotc.core.Contexts.Context = r.rootContext.asInstanceOf
    val sym = rsym.asInstanceOf[dotc.core.Symbols.Symbol]
    sym.is(dotc.core.Flags.Opaque)
  }

  def hackIsLeftAssoc(d: Symbol): Boolean = !d.name.endsWith(":")

  def hackGetSupertypes(r: Reflection)(rdef: r.ClassDef) = {
    import dotty.tools.dotc
    given dotc.core.Contexts.Context = r.rootContext.asInstanceOf
    val classdef = rdef.asInstanceOf[dotc.ast.tpd.TypeDef]
    val ref = classdef.symbol.info.asInstanceOf[dotc.core.Types.ClassInfo].appliedRef
    val baseTypes: List[dotc.core.Types.Type] = ref.baseClasses.map(b => ref.baseType(b))
    baseTypes.asInstanceOf[List[r.Type]]
  }

  def getSupertypes(c: ClassDef) = hackGetSupertypes(self.reflect)(c).tail

  object MatchTypeCase:
    def unapply(tpe: Type): Option[(TypeOrBounds, TypeOrBounds)] =
      tpe match
        case AppliedType(t, Seq(from, to)) if t == MatchCaseType =>
            Some((from, to))
        case TypeLambda(paramNames, paramTypes, AppliedType(t, Seq(from, to))) if t == MatchCaseType =>
            Some((from, to))
        case _ =>
          None
