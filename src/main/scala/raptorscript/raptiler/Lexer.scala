package raptorscript.raptiler

import scala.collection.mutable.Queue
import scala.language.implicitConversions

class Lexer(var text: String) {

  var pos: Int = 0
  var currentChar: Option[Char] = Some(text.charAt(pos))
  private var nextToken: Option[IToken] = None
  var currentToken: Option[IToken] = None
  val futureTokens = Queue[IToken]()

  advance()

  private def lexAdvance(n: Int = 1): Unit = {
    pos += n
    if (pos >= text.length())
      currentChar = None
    else
      currentChar = Some(text.charAt(pos))
  }

  private def shouldLex(): Boolean = {
    nextToken.isEmpty && currentChar.isDefined
  }

  private def lex(str: String, token: IToken): Unit = {
    if (shouldLex()) {
      if (text.slice(pos, pos + str.length()) == str) {
        lexAdvance(str.length())
        nextToken = Some(token)
      }
    }
  }

  private def skipComments(): Unit = {
    if (shouldLex() && currentChar.forall(c => c == '#')) {
      while (shouldLex && currentChar.forall(c => c != '\n')) {
        lexAdvance()
      }
    }
  }

  private def lexNumber(): Unit = {
    if (shouldLex() && currentChar.forall(Character.isDigit)) {
      var str = ""
      while (currentChar.nonEmpty && currentChar.forall(c => Character.isDigit(c) || c == '.')) {
        str += currentChar.getOrElse("")
        lexAdvance()
      }
      if (str.contains("."))
        nextToken = Some(Tokens.FLOAT(str.toFloat))
      else
        nextToken = Some(Tokens.INT(str.toInt))
    }
  }

  private def lexString(): Unit = {
    if (shouldLex() && currentChar.forall(c => c == '\"' || c == '\'')) {
      val delim = currentChar.get
      lexAdvance()
      var str = ""
      while (shouldLex()) {
        if (currentChar.forall(c => c == delim)) {
          lexAdvance()
          nextToken = Some(Tokens.STRING(str))
          return
        }
        str += currentChar.getOrElse("")
        lexAdvance()
      }
      throw new RaptorError(s"Unfinished string $delim$str")
    }
  }


  private def lexName(): Unit = {
    if (shouldLex() && currentChar.forall(c => Character.isLetter(c) || c == '_')) {
      var str = ""
      while (currentChar.nonEmpty && currentChar.forall(c => Character.isLetter(c) || Character.isDigit(c) || c == '_')) {
        str += currentChar.getOrElse("")
        lexAdvance()
      }
      if (Lexer.KEYWORDS.contains(str))
        nextToken = Some(Tokens.KWORD(str))
      else
        nextToken = Some(Tokens.NAME(str))
    }
  }

  private def getNextToken(): IToken = {
    if (futureTokens.nonEmpty) {
      return futureTokens.dequeue()
    }
    var result = Tokens.EOF
    var run = true
    while (currentChar.nonEmpty && run) {
      skipComments()
      if (currentChar.forall(Character.isSpace))
        lexAdvance()
      else {
        lexNumber()
        lexString()

        lex("+", Tokens.PLUS)
        lex("-", Tokens.MINUS)
        lex("*", Tokens.ASTERISK)
        lex("/", Tokens.SLASH)
        lex("(", Tokens.LPAR)
        lex(")", Tokens.RPAR)
        lex("{", Tokens.LBRAC)
        lex("}", Tokens.RBRAC)
        lex("[", Tokens.LSQBRAC)
        lex("]", Tokens.RSQBRAC)
        lex("<=", Tokens.LTEQ)
        lex(">=", Tokens.GTEQ)
        lex("==", Tokens.EQEQ)
        lex("!=", Tokens.NEQ)
        lex("<", Tokens.LESS_THAN)
        lex(">", Tokens.GREATER_THAN)
        lex("=", Tokens.EQUALS)
        lex(";", Tokens.SEMICOLON)
        lex(":", Tokens.COLON)
        lex(",", Tokens.COMMA)

        lexName()

        if (nextToken.isEmpty) {
          lexAdvance()
          throw new RaptorError(s"Unrecognized character ${currentChar.get}")
        }
        result = nextToken.get
        nextToken = None
        run = false
      }
    }
    result
  }

  def advance(): IToken = {
    val tok = getNextToken()
    currentToken = Some(tok)
    return tok
  }

  def get(index: Int = 0): IToken = {
    if (index == 0)
      try {
        return currentToken.get
      } catch {
        case _: Throwable => println(text); throw new RaptorError()
      }
    if (futureTokens.length < index) {
      val i2 = index - futureTokens.length
      for (i <- 0 until i2) {
        futureTokens.enqueue(getNextToken())
      }
    }
    futureTokens.get(index - 1).get
  }
}

object Lexer {
  val KEYWORDS = List(
    "var",
    "fun",
    "if",
    "else",
    "true",
    "false",
    "loop",
    "while",
    "for"
  )
}

trait IToken {
  val ttype: String
  val value: Option[Any]
}
object IToken {
  def unapply(iToken: IToken): Option[String] = {
    Some(iToken.ttype)
  }
}

case class Token[T](ttype: String) extends IToken {

  override val value: Option[T] = None

  def equals(other: IToken): Boolean = {
    other.ttype == this.ttype
  }

  def apply(value: T): IToken = {
    Token[T](ttype, value)
  }
}

object Token {
  def apply[T](ttype: String, vvalue: T) = {
    new Token[T](ttype) {
      override val value: Option[T] = Some(vvalue)
    }
  }

  // TODO: Get rid of this shit
  implicit def iToken2StrToken(iToken: IToken): Token[String] = {
    Token[String](iToken.ttype, iToken.value.get.toString())
  }

  // TODO: Get rid of this shit
  implicit def iToken2IntToken(iToken: IToken): Token[Int] = {
    Token[Int](iToken.ttype, Integer.valueOf(iToken.value.get.toString()))
  }

  // TODO: Get rid of this shit. Seriously
  implicit def iToken2FloatToken(iToken: IToken): Token[Float] = {
    Token[Float](iToken.ttype, java.lang.Float.valueOf(iToken.value.get.toString()))
  }
}

object Tokens {
  val PLUS = Token("+", "+")
  val MINUS = Token("-", "-")
  val ASTERISK = Token("*", "*")
  val SLASH = Token("/", "/")
  val EQUALS = Token("=", "=")
  val EQEQ = Token("==", "==")
  val NEQ = Token("!=", "!=")
  val GTEQ = Token(">=", "<=")
  val LTEQ = Token("<=", ">=")
  val COLON = Token(":", ":")
  val SEMICOLON = Token(";", ";")
  val COMMA = Token(",", ",")
  val NOT = Token("NOT", "!")
  val AND = Token("AND", "&&")
  val OR = Token("OR", "||")

  val INT = Token[Int]("INT")
  val FLOAT = Token[Float]("FLOAT")
  val STRING = Token[String]("STRING")
  val EOF = Token("EOF", "")

  val LPAR = Token("(", "(")
  val RPAR = Token(")", ")")
  val LBRAC = Token("{", "{")
  val RBRAC = Token("}", "}")
  val LSQBRAC = Token("[", "[")
  val RSQBRAC = Token("]", "]")
  val LESS_THAN = Token("<", "<")
  val GREATER_THAN = Token(">", ">")

  val KWORD = Token[String]("KWORD")
  val NAME = Token[String]("NAME")
}
