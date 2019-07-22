package zio.metrics
import java.math.BigInteger
import java.util.Date

/**
 *ALLOWED_CLASSNAMES_LIST = {
 *"java.lang.Void",
 *"java.lang.Boolean",
 *"java.lang.Character",
 *"java.lang.Byte",
 *"java.lang.Short",
 *"java.lang.Integer",
 *"java.lang.Long",
 *"java.lang.Float",
 *"java.lang.Double",
 *"java.lang.String",
 *"java.math.BigDecimal",
 *"java.math.BigInteger",
 *"java.util.Date",
 *"javax.management.ObjectName",
 *CompositeData.class.getName(),
 *TabularData.class.getName() }
 **/
trait Measurable                       extends Any
case class VoidZ(z: Unit)              extends AnyVal with Measurable
case class BooleanZ(z: Boolean)        extends AnyVal with Measurable
case class CharacterZ(z: Character)    extends AnyVal with Measurable
case class ByteZ(z: Byte)              extends AnyVal with Measurable
case class ShortZ(z: Short)            extends AnyVal with Measurable
case class IntegerZ(z: Integer)        extends AnyVal with Measurable
case class LongZ(z: Long)              extends AnyVal with Measurable
case class FloatZ(z: Float)            extends AnyVal with Measurable
case class DoubleZ(z: Double)          extends AnyVal with Measurable
case class StringZ(z: String)          extends AnyVal with Measurable
case class BigDecimalZ(z: BigDecimal)  extends AnyVal with Measurable
case class BigIntegerzZ(z: BigInteger) extends AnyVal with Measurable
case class DateZ(z: Date)              extends AnyVal with Measurable
