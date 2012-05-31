package cc.spray.json

package object lenses {
  type JsPred = JsValue => Boolean
  type Id[T] = T
  type Validated[T] = Either[Exception, T]
  type SafeJsValue = Validated[JsValue]

  type Operation = SafeJsValue => SafeJsValue

  type ScalarProjection = Projection[Id]
  type OptProjection = Projection[Option]
  type SeqProjection = Projection[Seq]

  def ??? = sys.error("NYI")
  def unexpected(message: String) = Left(new RuntimeException(message))
  def outOfBounds(message: String) = Left(new IndexOutOfBoundsException(message))

  implicit def rightBiasEither[A, B](e: Either[A, B]): Either.RightProjection[A, B] = e.right

  case class GetOrThrow[B](e: Either[Throwable, B]) {
    def getOrThrow: B = e match {
      case Right(b) => b
      case Left(e) => throw e
    }
  }

  implicit def orThrow[B](e: Either[Throwable, B]): GetOrThrow[B] = GetOrThrow(e)

  trait MonadicReader[T] {
    def read(js: JsValue): Validated[T]
  }

  object MonadicReader {
    implicit def safeMonadicReader[T: JsonReader]: MonadicReader[T] = new MonadicReader[T] {
      def read(js: JsValue): Validated[T] =
        safe(js.convertTo[T])
    }
  }

  def safe[T](body: => T): Validated[T] =
    try {
      Right(body)
    } catch {
      case e: Exception => Left(e)
    }

  case class ValidateOption[T](option: Option[T]) {
    def getOrError(message: => String): Validated[T] = option match {
      case Some(t) => Right(t)
      case None => unexpected(message)
    }
  }

  implicit def validateOption[T](o: Option[T]): ValidateOption[T] = ValidateOption(o)

  case class RichJsValue(value: JsValue) {
    def update(updater: Update): JsValue = updater(value)

    def update[T: JsonWriter, M[_]](lens: UpdateLens, pValue: T): JsValue =
      lens ! Operations.set(pValue) apply value

    // This can't be simplified because we don't want the type constructor
    // of projection to appear in the type paramater list.
    def extract[T: MonadicReader](p: Projection[Id]): T =
      p.get[T](value)

    def extract[T: MonadicReader](p: Projection[Option]): Option[T] =
      p.get[T](value)

    def extract[T: MonadicReader](p: Projection[Seq]): Seq[T] =
      p.get[T](value)

    def as[T: MonadicReader]: Validated[T] =
      implicitly[MonadicReader[T]].read(value)
  }

  implicit def updatable(value: JsValue): RichJsValue = RichJsValue(value)
}