package com.ma.keyboard

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Local, exact decimal calculator (BigDecimal => 0.1 + 0.2 = 0.3). */
object Calc {
    class CalcError(val reason: String) : Exception(reason)

    private val MC = MathContext(34, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)

    fun normalize(expr: String): String {
        val sb = StringBuilder()
        for (c in expr) {
            when (c) {
                '×', 'x', 'X', '*' -> sb.append('*')
                '÷', '/' -> sb.append('/')
                '−', '–', '-' -> sb.append('-')
                '٫', ',', '.' -> sb.append(if (c == ',') "" else ".")
                '٪', '%' -> sb.append('%')
                in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                ' ', '\u00A0', '٬' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun evaluate(expr: String): BigDecimal {
        val s = normalize(expr)
        if (s.isEmpty()) throw CalcError("empty")
        val p = Parser(s)
        val v = p.expr()
        if (p.pos < s.length) throw CalcError("syntax")
        return v.v
    }

    fun format(v: BigDecimal): String {
        var r = v.round(MathContext(16, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        if (r.signum() == 0) return "0"
        val exp = r.precision() - r.scale() - 1
        return if (exp > 15 || exp < -10) r.toString().replace("E+", "e").replace("E", "e") else r.toPlainString()
    }

    /** Returns (result text or null, error message or null). */
    fun tryEval(expr: String): Pair<String?, String?> = try {
        Pair(format(evaluate(expr)), null)
    } catch (e: CalcError) {
        Pair(null, if (e.reason == "div0") "تقسیم بر صفر ممکن نیست" else null)
    } catch (e: ArithmeticException) {
        Pair(null, "نتیجه قابل محاسبه نیست")
    } catch (e: NumberFormatException) {
        Pair(null, null)
    }

    private class V(val v: BigDecimal, val pct: Boolean)

    private class Parser(val s: String) {
        var pos = 0
        fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

        fun expr(): V {
            val first = term()
            var acc = first.v
            var any = false
            while (true) {
                val c = peek()
                if (c != '+' && c != '-') break
                pos++; any = true
                val r = term()
                val rv = if (r.pct) acc.multiply(r.v, MC) else r.v
                acc = if (c == '+') acc.add(rv, MC) else acc.subtract(rv, MC)
            }
            return if (any) V(acc, false) else first
        }

        fun term(): V {
            val first = unary()
            var acc = first.v
            var any = false
            while (true) {
                val c = peek()
                val implicit = c == '('
                if (c != '*' && c != '/' && !implicit) break
                if (!implicit) pos++
                any = true
                val r = unary()
                if (c == '/') {
                    if (r.v.signum() == 0) throw CalcError("div0")
                    acc = acc.divide(r.v, MC)
                } else acc = acc.multiply(r.v, MC)
            }
            return if (any) V(acc, false) else first
        }

        fun unary(): V {
            if (peek() == '-') { pos++; val u = unary(); return V(u.v.negate(), u.pct) }
            if (peek() == '+') { pos++; return unary() }
            return postfix()
        }

        fun postfix(): V {
            var v = primary()
            var pct = false
            while (peek() == '%') { pos++; v = v.divide(HUNDRED, MC); pct = true }
            return V(v, pct)
        }

        fun primary(): BigDecimal {
            if (peek() == '(') {
                pos++
                val e = expr()
                if (peek() == ')') pos++ else if (pos < s.length) throw CalcError("syntax")
                return e.v
            }
            val start = pos
            var dot = false
            while (pos < s.length && (s[pos].isDigit() || (s[pos] == '.' && !dot))) {
                if (s[pos] == '.') dot = true
                pos++
            }
            val num = s.substring(start, pos)
            if (num.isEmpty() || num == ".") throw CalcError("syntax")
            return BigDecimal(if (num.endsWith(".")) num.dropLast(1) else num)
        }
    }
}
