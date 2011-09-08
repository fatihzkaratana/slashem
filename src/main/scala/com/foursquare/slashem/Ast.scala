package com.foursquare.slashem


object Ast {

  // ripped from lucene source code QueryParser.java
  def escape(q: String) = {
    val sb = new StringBuilder()
    q.trim.foreach{c =>
      // These characters are part of the query syntax and must be escaped
      if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':'
        || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~'
        || c == '*' || c == '?' || c == '|' || c == '&') {
        sb.append('\\')
      }
      sb.append(c)
    }
    sb.toString
  }

  def quote(q: String) = "\"" + q + "\""


  abstract class AbstractClause {
    def extend: String
  }

  //You can use a JoinClause to join two clauses (normally with "and" or "or")
  case class JoinClause(s1 : AbstractClause, s2: AbstractClause, j: String) extends AbstractClause {
    def extend(): String = {
      "("+s1.extend+") "+j+" ("+s2.extend+")"
    }
  }

  // A 'Clause' is something of the form 'field:(query)'
  // @param plus Set true for regular required or false to negate.
  case class Clause[T](fieldName: String, query: Query[T], plus: Boolean = true) extends AbstractClause {
    def extend(): String = {
      val q = query match {
        case Group(x) => query
        case Splat() => query
        case _ => Group(query)
      }
      //If a field does not have a name then do not attempt to specify it
      val qstr = fieldName match {
        case "" => q.extend
        case x => x + ":" + q.extend
      }
      plus match {
        case true => qstr
        case false => "-"+qstr
      }
    }
  }

  //A field with a query weight
  case class WeightedField(fieldName: String, boost: Double = 1) {
    def extend(): String = {
      boost match {
        case 1 => fieldName
        case x => fieldName+"^"+x
      }
    }
  }

  //A phrase weighted field. Results in a document scoring bost
  //pf => traditional phrase query
  //pf2 => in edismax type queries two word shingle matches
  //pf3 => in edismax type queries three word shingle matches
  case class PhraseWeightedField(fieldName: String, boost: Double = 1, pf: Boolean, pf2: Boolean, pf3: Boolean) {
    def extend(): String = {
      boost match {
        case 1 => fieldName
        case x => fieldName+"^"+x
      }
    }
  }

  abstract class Query[T]() {
    def extend: String
    def and(c: Query[T]): Query[T] = And(this, c)
    def or(c: Query[T]): Query[T] = Or(this, c)
    def boost(b : Float): Query[T] = Boost(this, b)
  }

  case class Empty[T]() extends Query[T] {
    def extend = "\"\""
  }

  case class Phrase[T](query: T, escaped: Boolean = true) extends Query[T] {
    def extend = {'"' + escape(query.toString) + '"'}
  }

  case class Range[T](q1: Query[T],q2: Query[T]) extends Query[T] {
    def extend = {'['+q1.extend+" TO "+ q2.extend +']'}
  }


  case class UnescapedPhrase[T](query: T) extends Query[T] {
    def extend = query.toString
  }

  case class BagOfWords[T](query: T) extends Query[T] {
    def extend = escape(query.toString)
  }

  case class Group[T](items: Query[T]) extends Query[T] {
    def extend = {"(%s)".format(items.extend)}
  }


  // Override the constructor for Group so that it gobbles nested Groups, ie
  // Group(Group(foo))) => Group(foo)
  // Fails to compile with 'method apply is defined twice'
/*  object Group {
    def apply[T](items: Query[T]) = {
      new Group(Phrase("asdf"))}
      items match {
        case x: Group[_] => items
        case x => new Group(x)
      }
    }
  } */


  case class And[T](q1: Query[T], q2: Query[T]) extends Query[T] {
    def extend = "%s AND %s".format(q1.extend, q2.extend)
  }

  case class Or[T](q1: Query[T], q2: Query[T]) extends Query[T] {
    def extend = "%s OR %s".format(q1.extend, q2.extend)
  }

  case class Splat[T]() extends Query[T] {
    def extend = "*"
  }

  case class Boost[T](q: Query[T], boost: Float) extends Query[T] {
    def extend = q.extend + "^" + boost.toString
  }
}