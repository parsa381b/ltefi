package com.litefiles.app.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Above this size highlighting is skipped so typing stays smooth (the text is still fully editable). */
const val HIGHLIGHT_MAX_CHARS = 200_000

class Lang(
    val keywords: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockStart: String? = null,
    val blockEnd: String? = null,
    /** quote characters whose strings end at the line end */
    val quotes: String = "\"'",
    /** quote characters whose strings may span lines (JS template literals, Markdown code) */
    val multilineQuotes: String = "",
    val tripleQuotes: Boolean = false,
    val markup: Boolean = false,
    val headings: Boolean = false,
    val ignoreCase: Boolean = false,
)

private fun words(s: String): Set<String> = s.trim().split(Regex("\\s+")).toSet()

private val KOTLIN_KW = words("package import class interface object fun val var if else when for while do return break continue try catch finally throw is as in out by init constructor companion data sealed enum abstract open override private protected public internal suspend inline lateinit const typealias true false null this super")
private val JAVA_KW = words("package import class interface enum extends implements new if else switch case default for while do return break continue try catch finally throw throws static final abstract public private protected void int long double float boolean char byte short instanceof this super null true false synchronized volatile transient def")
private val JS_KW = words("import export from default class extends function const let var if else switch case for while do return break continue try catch finally throw new typeof instanceof in of async await yield this super null undefined true false interface type enum implements public private protected readonly static void")
private val PY_KW = words("import from as def class if elif else for while return break continue try except finally raise with lambda pass yield global nonlocal assert del is in not and or None True False async await self")
private val C_KW = words("include define ifdef ifndef endif if else switch case default for while do return break continue goto struct union enum typedef static const extern volatile sizeof void int long short char float double unsigned signed class public private protected namespace using template typename new delete this true false nullptr virtual override auto inline")
private val CS_KW = words("using namespace class struct interface enum public private protected internal static readonly const void int long double float bool string var new if else switch case default for foreach while do return break continue try catch finally throw this base null true false async await override virtual abstract sealed")
private val GO_KW = words("package import func var const type struct interface map chan if else switch case default for range return break continue go defer select fallthrough goto nil true false")
private val RUST_KW = words("use mod pub fn let mut const static struct enum impl trait for in while loop if else match return break continue as ref move self Self super crate where type unsafe async await dyn true false")
private val SWIFT_KW = words("import class struct enum protocol extension func var let if else guard switch case default for while repeat return break continue in is as try catch throw throws init deinit self super nil true false public private internal static override")
private val RUBY_KW = words("def end class module if elsif else unless while until for in do return yield begin rescue ensure require include nil true false self")
private val SHELL_KW = words("if then else elif fi for while until do done case esac function in select time return exit export local readonly echo cd")
private val SQL_KW = words("select from where insert into values update set delete create table alter drop index view join inner left right outer on group by order having limit offset union all distinct as and or not null is in like between exists case when then else end primary key foreign references default unique")
private val JSON_KW = words("true false null")
private val YAML_KW = words("true false null yes no on off")

private val CLIKE = Lang(lineComments = listOf("//"), blockStart = "/*", blockEnd = "*/")

object Languages {
    /** null = plain text (no highlighting). */
    fun forFile(name: String): Lang? {
        val lower = name.lowercase()
        return when (lower.substringAfterLast('.', lower)) {
            "kt", "kts" -> Lang(KOTLIN_KW, listOf("//"), "/*", "*/", tripleQuotes = true)
            "java", "gradle" -> Lang(JAVA_KW, listOf("//"), "/*", "*/")
            "js", "mjs", "cjs", "jsx", "tsx", "ts" -> Lang(JS_KW, listOf("//"), "/*", "*/", multilineQuotes = "`")
            "py", "pyw" -> Lang(PY_KW, listOf("#"), tripleQuotes = true)
            "c", "h", "cpp", "cc", "cxx", "hpp" -> Lang(C_KW, listOf("//"), "/*", "*/")
            "cs" -> Lang(CS_KW, listOf("//"), "/*", "*/")
            "go" -> Lang(GO_KW, listOf("//"), "/*", "*/", multilineQuotes = "`")
            "rs" -> Lang(RUST_KW, listOf("//"), "/*", "*/")
            "swift", "dart", "php" -> Lang(SWIFT_KW, listOf("//", "#"), "/*", "*/")
            "rb" -> Lang(RUBY_KW, listOf("#"))
            "sh", "bash", "zsh", "makefile", "dockerfile" -> Lang(SHELL_KW, listOf("#"))
            "bat", "cmd" -> Lang(SHELL_KW, listOf("rem ", "::"), ignoreCase = true)
            "sql" -> Lang(SQL_KW, listOf("--"), "/*", "*/", ignoreCase = true)
            "json" -> Lang(JSON_KW, quotes = "\"")
            "xml", "html", "htm", "xhtml", "plist", "xsl" -> Lang(markup = true)
            "css", "scss", "less" -> CLIKE
            "yml", "yaml", "toml" -> Lang(YAML_KW, listOf("#"))
            "ini", "conf", "cfg", "env" -> Lang(lineComments = listOf("#", ";"))
            "properties" -> Lang(lineComments = listOf("#", "!"), quotes = "")
            "md", "markdown" -> Lang(headings = true, quotes = "", multilineQuotes = "`")
            else -> null
        }
    }
}

class Highlighter(private val lang: Lang?, dark: Boolean) {
    private val keyword = SpanStyle(color = if (dark) Color(0xFFCE93D8) else Color(0xFF7B1FA2))
    private val string = SpanStyle(color = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32))
    private val comment = SpanStyle(color = if (dark) Color(0xFF9E9E9E) else Color(0xFF757575), fontStyle = FontStyle.Italic)
    private val number = SpanStyle(color = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0))
    private val tag = SpanStyle(color = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0))
    private val heading = SpanStyle(color = if (dark) Color(0xFFCE93D8) else Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)

    fun highlight(text: String): AnnotatedString {
        val l = lang
        if (l == null || text.length > HIGHLIGHT_MAX_CHARS) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            scan(text, l)
        }
    }

    /** Re-highlights only when the text itself changed (cursor moves don't re-run the scan). */
    fun asVisualTransformation(): VisualTransformation {
        var lastText: String? = null
        var lastResult: AnnotatedString? = null
        return VisualTransformation { input ->
            val text = input.text
            val result = if (text == lastText && lastResult != null) {
                lastResult!!
            } else {
                highlight(text).also { lastText = text; lastResult = it }
            }
            TransformedText(result, OffsetMapping.Identity)
        }
    }

    private fun eol(t: String, from: Int): Int {
        val e = t.indexOf('\n', from)
        return if (e < 0) t.length else e
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    /** Single left-to-right pass: no regex, no backtracking, linear in the text length. */
    private fun AnnotatedString.Builder.scan(t: String, l: Lang) {
        val n = t.length
        var i = 0
        outer@ while (i < n) {
            val c = t[i]

            if (l.headings && c == '#' && (i == 0 || t[i - 1] == '\n')) {
                val e = eol(t, i)
                addStyle(heading, i, e)
                i = e
                continue
            }
            if (l.markup && c == '<') {
                i = markup(t, i)
                continue
            }
            for (p in l.lineComments) {
                if (t.startsWith(p, i)) {
                    val e = eol(t, i)
                    addStyle(comment, i, e)
                    i = e
                    continue@outer
                }
            }
            val bs = l.blockStart
            val be = l.blockEnd
            if (bs != null && be != null && t.startsWith(bs, i)) {
                val e = t.indexOf(be, i + bs.length)
                val end = if (e < 0) n else e + be.length
                addStyle(comment, i, end)
                i = end
                continue
            }
            val multi = l.multilineQuotes.indexOf(c) >= 0
            if (multi || l.quotes.indexOf(c) >= 0) {
                val end: Int
                val triple = "$c$c$c"
                if (l.tripleQuotes && t.startsWith(triple, i)) {
                    val close = t.indexOf(triple, i + 3)
                    end = if (close < 0) n else close + 3
                } else {
                    var j = i + 1
                    while (j < n) {
                        val d = t[j]
                        if (d == '\\') { j += 2; continue }
                        if (d == c) { j++; break }
                        if (d == '\n' && !multi) break
                        j++
                    }
                    end = j.coerceAtMost(n)
                }
                addStyle(string, i, end)
                i = end
                continue
            }
            if (c.isDigit() && (i == 0 || !isIdentPart(t[i - 1]))) {
                var j = i + 1
                while (j < n && (t[j].isLetterOrDigit() || t[j] == '.' || t[j] == '_')) j++
                addStyle(number, i, j)
                i = j
                continue
            }
            if (isIdentStart(c)) {
                var j = i + 1
                while (j < n && isIdentPart(t[j])) j++
                if (j - i <= 12 && l.keywords.isNotEmpty()) {
                    val word = t.substring(i, j)
                    if ((if (l.ignoreCase) word.lowercase() else word) in l.keywords) addStyle(keyword, i, j)
                }
                i = j
                continue
            }
            i++
        }
    }

    /** Handles `<!-- -->` comments and `<tag attr="value">`; returns the index after the construct. */
    private fun AnnotatedString.Builder.markup(t: String, start: Int): Int {
        val n = t.length
        if (t.startsWith("<!--", start)) {
            val e = t.indexOf("-->", start + 4)
            val end = if (e < 0) n else e + 3
            addStyle(comment, start, end)
            return end
        }
        val close = t.indexOf('>', start)
        if (close < 0 || close - start > 2000) return start + 1 // a stray "<", not a tag
        var segStart = start
        var quote = '\u0000'
        for (j in start + 1..close) {
            val d = t[j]
            if (quote != '\u0000') {
                if (d == quote) {
                    addStyle(string, segStart, j + 1)
                    segStart = j + 1
                    quote = '\u0000'
                }
            } else if (d == '"' || d == '\'') {
                addStyle(tag, segStart, j)
                segStart = j
                quote = d
            }
        }
        if (segStart <= close) addStyle(if (quote != '\u0000') string else tag, segStart, close + 1)
        return close + 1
    }
}
